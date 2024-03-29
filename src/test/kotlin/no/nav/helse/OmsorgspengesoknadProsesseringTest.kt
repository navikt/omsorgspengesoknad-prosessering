package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import com.typesafe.config.ConfigFactory
import io.ktor.server.config.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.testing.*
import io.prometheus.client.CollectorRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.delay
import no.nav.helse.SøknadUtils.Companion.barn
import no.nav.helse.SøknadUtils.Companion.melding
import no.nav.helse.SøknadUtils.Companion.søker
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder
import no.nav.helse.k9.assertUtvidetAntallDagerFormat
import no.nav.helse.prosessering.v1.MeldingV1
import no.nav.helse.prosessering.v1.asynkron.deserialiserTilPreprosessertMelding
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.KafkaContainer
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit


class OmsorgspengesoknadProsesseringTest {

    private companion object {

        private val logger: Logger = LoggerFactory.getLogger(OmsorgspengesoknadProsesseringTest::class.java)

        private val wireMockServer: WireMockServer = WireMockBuilder()
            .withAzureSupport()
            .build()
            .stubK9DokumentHealth()
            .stubOmsorgspengerJoarkHealth()
            .stubJournalfor()
            .stubLagreDokument()
            .stubSlettDokument()

        private val kafkaContainer = KafkaWrapper.bootstrap()
        private val kafkaTestProducer = kafkaContainer.meldingsProducer()

        private val cleanupKonsumer = kafkaContainer.cleanupKonsumer()
        private val preprosessertKonsumer = kafkaContainer.preprosessertKonsumer()
        private val k9DittnavVarselKonsumer = kafkaContainer.k9DittnavVarselKonsumer()

        // Se https://github.com/navikt/dusseldorf-ktor#f%C3%B8dselsnummer
        private val dNummerA = "55125314561"

        private var engine = newEngine(kafkaContainer).apply {
            start(wait = true)
        }

        private fun getConfig(kafkaContainer: KafkaContainer?): ApplicationConfig {
            val fileConfig = ConfigFactory.load()
            val testConfig = ConfigFactory.parseMap(
                TestConfiguration.asMap(
                    wireMockServer = wireMockServer,
                    kafkaContainer = this.kafkaContainer
                )
            )
            val mergedConfig = testConfig.withFallback(fileConfig)
            return HoconApplicationConfig(mergedConfig)
        }

        private fun newEngine(kafkaContainer: KafkaContainer?) = TestApplicationEngine(createTestEnvironment {
            config = getConfig(this@Companion.kafkaContainer)
        })

        private fun stopEngine() = engine.stop(5, 60, TimeUnit.SECONDS)

        internal fun restartEngine() {
            stopEngine()
            CollectorRegistry.defaultRegistry.clear()
            engine = newEngine(kafkaContainer)
            engine.start(wait = true)
        }

        @AfterAll
        fun tearDown() {
            logger.info("Tearing down")
            wireMockServer.stop()
            cleanupKonsumer.close()
            kafkaTestProducer.close()
            stopEngine()
            kafkaContainer.stop()
            logger.info("Tear down complete")
        }
    }

    @Test
    fun `test isready, isalive, health og metrics`() {
        with(engine) {
            val healthEndpoints = listOf("/isready", "/isalive", "/metrics", "/health")

            val responses = healthEndpoints.map { endpoint ->
                handleRequest(HttpMethod.Get, endpoint).response.status()
            }

            for(statusCode in responses) {
                Assertions.assertEquals(HttpStatusCode.OK, statusCode)
            }

        }
    }

    @Test
    fun `Gylding melding blir prosessert av journalføringskonsumer`() {
        val søknadId = UUID.randomUUID().toString()
        val melding = melding.copy(
            søknadId = søknadId
        )

        kafkaTestProducer.leggTilMottak(melding)
        cleanupKonsumer
            .hentCleanupMelding(melding.søknadId)
            .assertUtvidetAntallDagerFormat()

        k9DittnavVarselKonsumer.hentK9Beskjed(melding.søknadId)
            .assertGyldigK9Beskjed(melding)
    }

    @Test
    fun `En feilprosessert melding vil bli prosessert etter at tjenesten restartes`() {
        val søknadId = UUID.randomUUID().toString()
        val melding = melding.copy(
            søknadId = søknadId
        )

        wireMockServer.stubJournalfor(500) // Simulerer feil ved journalføring

        kafkaTestProducer.leggTilMottak(melding)
        ventPaaAtRetryMekanismeIStreamProsessering()
        readyGir200HealthGir503()

        wireMockServer.stubJournalfor(201) // Simulerer journalføring fungerer igjen
        restartEngine()
        cleanupKonsumer
            .hentCleanupMelding(melding.søknadId)
            .assertUtvidetAntallDagerFormat()

        k9DittnavVarselKonsumer.hentK9Beskjed(melding.søknadId)
            .assertGyldigK9Beskjed(melding)
    }

    private fun readyGir200HealthGir503() {
        with(engine) {
            handleRequest(HttpMethod.Get, "/isready") {}.apply {
                Assertions.assertEquals(HttpStatusCode.OK, response.status())
                handleRequest(HttpMethod.Get, "/health") {}.apply {
                    Assertions.assertEquals(HttpStatusCode.ServiceUnavailable, response.status())
                }
            }
        }
    }

    @Test
    fun `Melding som gjeder søker med D-nummer`() {

        val søknadId = UUID.randomUUID().toString()
        val melding = melding.copy(
            søknadId = søknadId,
            søker = søker.copy(
                fødselsnummer = dNummerA
            )
        )

        kafkaTestProducer.leggTilMottak(melding)
        assertInnsending(melding)
    }

    @Test
    fun `Melding lagt til prosessering selv om sletting av vedlegg feiler`() {

        val søknadId = UUID.randomUUID().toString()
        val melding = melding.copy(
            søknadId = søknadId,
            barn = barn.copy(
                navn = "kari"
            )
        )

        kafkaTestProducer.leggTilMottak(melding)
        assertInnsending(melding)
    }

    @Test
    fun `Forvent barnets fødselsnummer dersom den er satt i melding`() {
        val forventetFodselsNummer: String = "19066672169"
        val søknadId = UUID.randomUUID().toString()
        val melding = melding.copy(
            søknadId = søknadId,
            barn = barn.copy(
                navn = "Barn Barnesen",
                norskIdentifikator = forventetFodselsNummer,
                aktørId = "56789"
            )
        )

        kafkaTestProducer.leggTilMottak(melding)
        val preprosessertMelding =
            preprosessertKonsumer.hentPreprosessertMelding(melding.søknadId).deserialiserTilPreprosessertMelding()

        Assertions.assertEquals(forventetFodselsNummer, preprosessertMelding.barn.norskIdentifikator)

        assertInnsending(melding)
    }

    @Test
    fun `Forvent 2 legeerklæringer og 2 samværsavtaler dersom den er satt i melding`() {
        val søknadId = UUID.randomUUID().toString()
        val melding = melding.copy(
            søknadId = søknadId,
            legeerklæringVedleggId = listOf("1234", "5678"),
            samværsavtaleVedleggId = listOf("9876", "5432")
        )

        kafkaTestProducer.leggTilMottak(melding)
        val preprosessertMelding =
            preprosessertKonsumer.hentPreprosessertMelding(melding.søknadId).deserialiserTilPreprosessertMelding()
        Assertions.assertEquals(6, preprosessertMelding.dokumentId.flatten().size)
        // 2 legeerklæringsvedlegg, 2 samværsavtalevedlegg, 1 søknadPdf og 1 søknadJson.
        assertInnsending(melding)
    }

    @Test
    fun `Forvent riktig format på journalført melding`() {
        val søknadId = UUID.randomUUID().toString()
        val melding = melding.copy(
            søknadId = søknadId
        )

        kafkaTestProducer.leggTilMottak(melding)
        assertInnsending(melding)
    }

    private fun assertInnsending(melding: MeldingV1) {
        cleanupKonsumer
            .hentCleanupMelding(melding.søknadId)
            .assertUtvidetAntallDagerFormat()

        k9DittnavVarselKonsumer.hentK9Beskjed(melding.søknadId)
            .assertGyldigK9Beskjed(melding)
    }

    private fun ventPaaAtRetryMekanismeIStreamProsessering() = runBlocking { delay(Duration.ofSeconds(30)) }
}

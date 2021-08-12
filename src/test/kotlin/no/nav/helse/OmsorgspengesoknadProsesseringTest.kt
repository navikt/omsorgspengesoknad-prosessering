package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import com.typesafe.config.ConfigFactory
import io.ktor.config.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.testing.*
import io.prometheus.client.CollectorRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.delay
import no.nav.common.KafkaEnvironment
import no.nav.helse.SøknadUtils.Companion.barn
import no.nav.helse.SøknadUtils.Companion.melding
import no.nav.helse.SøknadUtils.Companion.søker
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder
import no.nav.helse.k9.assertUtvidetAntallDagerFormat
import no.nav.helse.prosessering.v1.asynkron.deserialiserTilPreprosessertMelding
import org.junit.AfterClass
import org.junit.BeforeClass
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals


class OmsorgspengesoknadProsesseringTest {

    private companion object {

        private val logger: Logger = LoggerFactory.getLogger(OmsorgspengesoknadProsesseringTest::class.java)

        private val wireMockServer: WireMockServer = WireMockBuilder()
            .withNaisStsSupport()
            .withAzureSupport()
            .navnOppslagConfig()
            .build()
            .stubK9DokumentHealth()
            .stubOmsorgspengerJoarkHealth()
            .stubJournalfor()
            .stubLagreDokument()
            .stubSlettDokument()
            .stubAktørRegister("29099012345", "123456")

        private val kafkaEnvironment = KafkaWrapper.bootstrap()
        private val kafkaTestProducer = kafkaEnvironment.meldingsProducer()

        private val cleanupKonsumer = kafkaEnvironment.cleanupKonsumer()
        private val preprossesertKonsumer = kafkaEnvironment.preprossesertKonsumer()

        // Se https://github.com/navikt/dusseldorf-ktor#f%C3%B8dselsnummer
        private val gyldigFodselsnummerA = "02119970078"
        private val gyldigFodselsnummerB = "19066672169"
        private val gyldigFodselsnummerC = "20037473937"
        private val dNummerA = "55125314561"

        private var engine = newEngine(kafkaEnvironment).apply {
            start(wait = true)
        }

        private fun getConfig(kafkaEnvironment: KafkaEnvironment?): ApplicationConfig {
            val fileConfig = ConfigFactory.load()
            val testConfig = ConfigFactory.parseMap(
                TestConfiguration.asMap(
                    wireMockServer = wireMockServer,
                    kafkaEnvironment = kafkaEnvironment
                )
            )
            val mergedConfig = testConfig.withFallback(fileConfig)
            return HoconApplicationConfig(mergedConfig)
        }

        private fun newEngine(kafkaEnvironment: KafkaEnvironment?) = TestApplicationEngine(createTestEnvironment {
            config = getConfig(kafkaEnvironment)
        })

        private fun stopEngine() = engine.stop(5, 60, TimeUnit.SECONDS)

        internal fun restartEngine() {
            stopEngine()
            CollectorRegistry.defaultRegistry.clear()
            engine = newEngine(kafkaEnvironment)
            engine.start(wait = true)
        }

        @BeforeClass
        @JvmStatic
        fun buildUp() {
            wireMockServer.stubAktørRegister(gyldigFodselsnummerA, "666666666")
            wireMockServer.stubAktørRegister(gyldigFodselsnummerB, "777777777")
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            logger.info("Tearing down")
            wireMockServer.stop()
            cleanupKonsumer.close()
            kafkaTestProducer.close()
            stopEngine()
            kafkaEnvironment.tearDown()
            logger.info("Tear down complete")
        }
    }

    @Test
    fun `test isready, isalive, health og metrics`() {
        with(engine) {
            handleRequest(HttpMethod.Get, "/isready") {}.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                handleRequest(HttpMethod.Get, "/isalive") {}.apply {
                    assertEquals(HttpStatusCode.OK, response.status())
                    handleRequest(HttpMethod.Get, "/metrics") {}.apply {
                        assertEquals(HttpStatusCode.OK, response.status())
                        handleRequest(HttpMethod.Get, "/health") {}.apply {
                            assertEquals(HttpStatusCode.OK, response.status())
                        }
                    }
                }
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
    }

    private fun readyGir200HealthGir503() {
        with(engine) {
            handleRequest(HttpMethod.Get, "/isready") {}.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                handleRequest(HttpMethod.Get, "/health") {}.apply {
                    assertEquals(HttpStatusCode.ServiceUnavailable, response.status())
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
        cleanupKonsumer
            .hentCleanupMelding(melding.søknadId)
            .assertUtvidetAntallDagerFormat()
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
        cleanupKonsumer
            .hentCleanupMelding(melding.søknadId)
            .assertUtvidetAntallDagerFormat()
    }

    @Test
    fun `Melding lagt til prosessering selv om oppslag paa aktør ID for barn feiler`() {
        val søknadId = UUID.randomUUID().toString()
        val melding = melding.copy(
            søknadId = søknadId
        )

        wireMockServer.stubAktoerRegisterGetAktoerIdNotFound(gyldigFodselsnummerC)

        kafkaTestProducer.leggTilMottak(melding)
        cleanupKonsumer
            .hentCleanupMelding(melding.søknadId)
            .assertUtvidetAntallDagerFormat()
    }

    @Test
    fun `Bruk barnets fødselsnummer til å slå opp i tps-proxy dersom navnet mangler`() {
        wireMockServer.stubTpsProxyGetNavn("KLØKTIG", "BLUNKENDE", "SUPERKONSOLL")
        val søknadId = UUID.randomUUID().toString()
        val melding = melding.copy(
            søknadId = søknadId,
            barn = barn.copy(
                navn = null
            )
        )

        kafkaTestProducer.leggTilMottak(melding)
        val preprossesertMelding =
            preprossesertKonsumer.hentPreprossesertMelding(melding.søknadId).deserialiserTilPreprosessertMelding()
        assertEquals("KLØKTIG BLUNKENDE SUPERKONSOLL", preprossesertMelding.barn.navn)
        cleanupKonsumer
            .hentCleanupMelding(melding.søknadId)
            .assertUtvidetAntallDagerFormat()
    }

    @Test
    fun `Bruk barnets aktørId til å slå opp i tps-proxy dersom navnet mangler`() {
        wireMockServer.stubAktørRegister(dNummerA, "56789")
        wireMockServer.stubTpsProxyGetNavn("KLØKTIG", "BLUNKENDE", "SUPERKONSOLL")

        val søknadId = UUID.randomUUID().toString()
        val melding = melding.copy(
            søknadId = søknadId,
            barn = barn.copy(
                navn = null,
                aktørId = "56789"
            )
        )

        kafkaTestProducer.leggTilMottak(melding)
        val preprosessertMelding =
            preprossesertKonsumer.hentPreprossesertMelding(melding.søknadId).deserialiserTilPreprosessertMelding()
        assertEquals("KLØKTIG BLUNKENDE SUPERKONSOLL", preprosessertMelding.barn.navn)
        cleanupKonsumer
            .hentCleanupMelding(melding.søknadId)
            .assertUtvidetAntallDagerFormat()
    }

    @Test
    fun `Forvent barnets fødselsnummer dersom den er satt i melding`() {
        wireMockServer.stubAktørRegister(gyldigFodselsnummerB, "56789")

        val forventetFodselsNummer: String = gyldigFodselsnummerB
        val søknadId = UUID.randomUUID().toString()
        val melding = melding.copy(
            søknadId = søknadId,
            barn = barn.copy(
                navn = null,
                norskIdentifikator = forventetFodselsNummer,
                aktørId = "56789"
            )
        )

        kafkaTestProducer.leggTilMottak(melding)
        val preprosessertMelding =
            preprossesertKonsumer.hentPreprossesertMelding(melding.søknadId).deserialiserTilPreprosessertMelding()

        assertEquals(forventetFodselsNummer, preprosessertMelding.barn.norskIdentifikator)

        cleanupKonsumer
            .hentCleanupMelding(melding.søknadId)
            .assertUtvidetAntallDagerFormat()
    }

    @Test
    fun `Forvent 2 legeerklæringer og 2 samværsavtaler dersom den er satt i melding`() {
        wireMockServer.stubAktørRegister(gyldigFodselsnummerB, "56789")

        val søknadId = UUID.randomUUID().toString()
        val melding = melding.copy(
            søknadId = søknadId,
            barn = barn.copy(
                navn = null,
                fødselsdato = null,
                aktørId = "56789"
            ),
            legeerklæring = listOf(URI("http://localhost:8080/vedlegg/1"), URI("http://localhost:8080/vedlegg/2")),
            samværsavtale = listOf(URI("http://localhost:8080/vedlegg/3"), URI("http://localhost:8080/vedlegg/4"))
        )

        kafkaTestProducer.leggTilMottak(melding)
        val preprossesertMelding =
            preprossesertKonsumer.hentPreprossesertMelding(melding.søknadId).deserialiserTilPreprosessertMelding()
        assertEquals(5, preprossesertMelding.dokumentUrls.size)
        // 2 legeerklæringsvedlegg, 2, to samværsavtalevedlegg, og 1 søknadPdf.
        cleanupKonsumer
            .hentCleanupMelding(melding.søknadId)
            .assertUtvidetAntallDagerFormat()
    }

    @Test
    fun `Forvent barnets fødselsnummer blir slått opp dersom den ikke er satt i melding`() {
        val forventetFodselsNummer = gyldigFodselsnummerB

        val søknadId = UUID.randomUUID().toString()
        val melding = melding.copy(
            søker = søker.copy(
                fødselsnummer = gyldigFodselsnummerA
            ),
            søknadId = søknadId,
            barn = barn.copy(
                navn = "Ole Dole Doffen",
                norskIdentifikator = null,
                aktørId = "777777777"
            )
        )

        kafkaTestProducer.leggTilMottak(melding)
        val preprosessertMelding =
            preprossesertKonsumer.hentPreprossesertMelding(melding.søknadId).deserialiserTilPreprosessertMelding()

        assertEquals(forventetFodselsNummer, preprosessertMelding.barn.norskIdentifikator)

        cleanupKonsumer
            .hentCleanupMelding(melding.søknadId)
            .assertUtvidetAntallDagerFormat()
    }

    @Test
    fun `Forvent riktig format på journalført melding`() {
        val søknadId = UUID.randomUUID().toString()
        val melding = melding.copy(
            søknadId = søknadId
        )

        kafkaTestProducer.leggTilMottak(melding)
        cleanupKonsumer
            .hentCleanupMelding(melding.søknadId)
            .assertUtvidetAntallDagerFormat()
    }

    private fun ventPaaAtRetryMekanismeIStreamProsessering() = runBlocking { delay(Duration.ofSeconds(30)) }
}

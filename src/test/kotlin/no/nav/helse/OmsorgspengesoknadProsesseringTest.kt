package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.delay
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder
import org.apache.kafka.clients.producer.KafkaProducer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.KafkaContainer
import java.time.Duration
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OmsorgspengesoknadProsesseringTest {

    private val logger: Logger = LoggerFactory.getLogger(OmsorgspengesoknadProsesseringTest::class.java)

    private lateinit var kafkaContainer: KafkaContainer
    private val wireMockServer: WireMockServer = WireMockBuilder()
        .withAzureSupport()
        .build()
        .stubK9DokumentHealth()
        .stubOmsorgspengerJoarkHealth()
        .stubJournalfor()
        .stubLagreDokument()
        .stubSlettDokument()

    @BeforeAll
    fun setup() {
        kafkaContainer = KafkaWrapper.bootstrap()
        wireMockServer.start()
    }

    //private val kafkaTestProducer = kafkaContainer.meldingsProducer()

    //private val cleanupKonsumer = kafkaContainer.cleanupKonsumer()
    //private val preprosessertKonsumer = kafkaContainer.preprosessertKonsumer()
    //private val k9DittnavVarselKonsumer = kafkaContainer.k9DittnavVarselKonsumer()

    // Se https://github.com/navikt/dusseldorf-ktor#f%C3%B8dselsnummer
    private val dNummerA = "55125314561"

    @Test
    fun `test isready, isalive, health og metrics`() = testApplication {
        mockApp(kafkaContainer, wireMockServer)
        val healthEndpoints = listOf("/isready", "/isalive", "/metrics", "/health")
        val results = healthEndpoints.map {
            client.get(it).status
        }

        results.forEach {
            Assertions.assertEquals(HttpStatusCode.OK, it)
        }
        /*
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

         */
    }

    /*
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
            runBlocking { delay(Duration.ofSeconds(1)) }
            cleanupKonsumer
                .hentCleanupMelding(melding.søknadId)
                .assertUtvidetAntallDagerFormat()
            runBlocking { delay(Duration.ofSeconds(1)) }
            k9DittnavVarselKonsumer.hentK9Beskjed(melding.søknadId)
                .assertGyldigK9Beskjed(melding)
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

            assertEquals(forventetFodselsNummer, preprosessertMelding.barn.norskIdentifikator)

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
            assertEquals(6, preprosessertMelding.dokumentId.flatten().size)
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
    */
    private fun ventPaaAtRetryMekanismeIStreamProsessering() = runBlocking { delay(Duration.ofSeconds(30)) }
}
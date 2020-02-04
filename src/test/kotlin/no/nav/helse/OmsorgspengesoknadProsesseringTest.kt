package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import com.typesafe.config.ConfigFactory
import io.ktor.config.ApplicationConfig
import io.ktor.config.HoconApplicationConfig
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.stop
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.createTestEnvironment
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.delay
import no.nav.common.KafkaEnvironment
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder
import no.nav.helse.prosessering.v1.Barn
import no.nav.helse.prosessering.v1.Medlemskap
import no.nav.helse.prosessering.v1.MeldingV1
import no.nav.helse.prosessering.v1.Søker
import no.nav.helse.prosessering.v1.asynkron.Journalfort
import no.nav.helse.prosessering.v1.asynkron.TopicEntry
import org.junit.AfterClass
import org.junit.BeforeClass
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals


@KtorExperimentalAPI
class OmsorgspengesoknadProsesseringTest {

    @KtorExperimentalAPI
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
        private val kafkaTestConsumer = kafkaEnvironment.testConsumer()
        private val kafkaTestProducer = kafkaEnvironment.testProducer()

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
            kafkaTestConsumer.close()
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
    fun `Gylding melding blir prosessert`() {
        val melding = gyldigMelding(
            fødselsnummerSoker = gyldigFodselsnummerA,
            fødselsnummerBarn = gyldigFodselsnummerB
        )

        kafkaTestProducer.leggSoknadTilProsessering(melding)
        kafkaTestConsumer.hentJournalført(melding.søknadId)
    }

    @Test
    fun `Melding med språk og skal jobbe prosent blir prosessert`() {

        val sprak = "nn"

        val melding = gyldigMelding(
            fødselsnummerSoker = gyldigFodselsnummerA,
            fødselsnummerBarn = gyldigFodselsnummerB,
            sprak = sprak
        )

        kafkaTestProducer.leggSoknadTilProsessering(melding)
        val oppgaveOpprettet = kafkaTestConsumer.hentJournalført(melding.søknadId).data
        assertEquals(sprak, oppgaveOpprettet.melding.språk)
    }

    @Test
    fun `En feilprosessert melding vil bli prosessert etter at tjenesten restartes`() {
        val melding = gyldigMelding(
            fødselsnummerSoker = gyldigFodselsnummerA,
            fødselsnummerBarn = gyldigFodselsnummerB
        )

        wireMockServer.stubJournalfor(500) // Simulerer feil ved journalføring

        kafkaTestProducer.leggSoknadTilProsessering(melding)
        ventPaaAtRetryMekanismeIStreamProsessering()
        readyGir200HealthGir503()

        wireMockServer.stubJournalfor(201) // Simulerer journalføring fungerer igjen
        restartEngine()
        kafkaTestConsumer.hentJournalført(melding.søknadId)
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
        val melding = gyldigMelding(
            fødselsnummerSoker = dNummerA,
            fødselsnummerBarn = gyldigFodselsnummerB
        )

        kafkaTestProducer.leggSoknadTilProsessering(melding)
        kafkaTestConsumer.hentJournalført(melding.søknadId)
    }

    @Test
    fun `Melding lagt til prosessering selv om sletting av vedlegg feiler`() {
        val melding = gyldigMelding(
            fødselsnummerSoker = gyldigFodselsnummerA,
            fødselsnummerBarn = gyldigFodselsnummerB,
            barnetsNavn = "kari"
        )

        kafkaTestProducer.leggSoknadTilProsessering(melding)
        kafkaTestConsumer.hentJournalført(melding.søknadId)
    }

    @Test
    fun `Melding lagt til prosessering selv om oppslag paa aktør ID for barn feiler`() {
        val melding = gyldigMelding(
            fødselsnummerSoker = gyldigFodselsnummerA,
            fødselsnummerBarn = gyldigFodselsnummerC
        )

        wireMockServer.stubAktoerRegisterGetAktoerIdNotFound(gyldigFodselsnummerC)

        kafkaTestProducer.leggSoknadTilProsessering(melding)
        kafkaTestConsumer.hentJournalført(melding.søknadId)
    }

    @Test
    fun `Bruk barnets fødselsnummer til å slå opp i tps-proxy dersom navnet mangler`() {
        wireMockServer.stubTpsProxyGetNavn("KLØKTIG", "BLUNKENDE", "SUPERKONSOLL")
        val melding = gyldigMelding(
            fødselsnummerSoker = gyldigFodselsnummerC,
            fødselsnummerBarn = gyldigFodselsnummerB,
            barnetsNavn = null
        )

        kafkaTestProducer.leggSoknadTilProsessering(melding)
        val hentOpprettetOppgave: TopicEntry<Journalfort> = kafkaTestConsumer.hentJournalført(melding.søknadId)
        assertEquals("KLØKTIG BLUNKENDE SUPERKONSOLL", hentOpprettetOppgave.data.melding.barn.navn)
    }

    @Test
    fun `Bruk barnets aktørId til å slå opp i tps-proxy dersom navnet mangler`() {
        wireMockServer.stubAktørRegister(dNummerA, "56789")
        wireMockServer.stubTpsProxyGetNavn("KLØKTIG", "BLUNKENDE", "SUPERKONSOLL")

        val melding = gyldigMelding(
            fødselsnummerSoker = gyldigFodselsnummerA,
            fødselsnummerBarn = null,
            barnetsNavn = null,
            aktørIdBarn = "56789"
        )

        kafkaTestProducer.leggSoknadTilProsessering(melding)
        val hentOpprettetOppgave: TopicEntry<Journalfort> = kafkaTestConsumer.hentJournalført(melding.søknadId)
        assertEquals("KLØKTIG BLUNKENDE SUPERKONSOLL", hentOpprettetOppgave.data.melding.barn.navn)
    }

    @Test
    fun `Forvent barnets fødselsnummer dersom den er satt i melding`() {
        wireMockServer.stubAktørRegister(gyldigFodselsnummerB, "56789")

        val forventetFodselsNummer = gyldigFodselsnummerB

        val melding = gyldigMelding(
            fødselsnummerSoker = gyldigFodselsnummerA,
            fødselsnummerBarn = forventetFodselsNummer,
            barnetsNavn = null,
            aktørIdBarn = "56789"
        )

        kafkaTestProducer.leggSoknadTilProsessering(melding)
        val hentOpprettetOppgave: TopicEntry<Journalfort> = kafkaTestConsumer.hentJournalført(melding.søknadId)
        assertEquals(forventetFodselsNummer, hentOpprettetOppgave.data.melding.barn.fødselsnummer)
    }

    @Test
    fun `Forvent barnets fødselsnummer blir slått opp dersom den ikke er satt i melding`() {
        wireMockServer.stubAktørRegister(gyldigFodselsnummerB, "666")
        val forventetFodselsNummer = gyldigFodselsnummerB

        val melding = gyldigMelding(
            fødselsnummerSoker = gyldigFodselsnummerA,
            fødselsnummerBarn = null,
            barnetsNavn = null,
            aktørIdBarn = "666"
        )

        kafkaTestProducer.leggSoknadTilProsessering(melding)
        val hentOpprettetOppgave: TopicEntry<Journalfort> = kafkaTestConsumer.hentJournalført(melding.søknadId)
        assertEquals(forventetFodselsNummer, hentOpprettetOppgave.data.melding.barn.fødselsnummer)
    }

    private fun gyldigMelding(
        fødselsnummerSoker: String,
        fødselsnummerBarn: String?,
        barnetsNavn: String? = "kari",
        barnetsFødselsdato: LocalDate? = LocalDate.now().minusDays(100),
        aktørIdBarn: String? = null,
        sprak: String? = null
    ): MeldingV1 = MeldingV1(
        språk = sprak,
        søknadId = UUID.randomUUID().toString(),
        mottatt = ZonedDateTime.now(),
        søker = Søker(
            aktørId = "123456",
            fødselsnummer = fødselsnummerSoker,
            fødselsdato = LocalDate.now().minusDays(1000),
            etternavn = "Nordmann",
            mellomnavn = "Mellomnavn",
            fornavn = "Ola"
        ),
        barn = Barn(
            navn = barnetsNavn,
            fødselsnummer = fødselsnummerBarn,
            aktørId = aktørIdBarn,
            fødselsdato = barnetsFødselsdato
        ),
        relasjonTilBarnet = "Mor",
        harBekreftetOpplysninger = true,
        harForstattRettigheterOgPlikter = true,
        medlemskap = Medlemskap(
            harBoddIUtlandetSiste12Mnd = true,
            skalBoIUtlandetNeste12Mnd = true
        )
    )

    private fun ventPaaAtRetryMekanismeIStreamProsessering() = runBlocking { delay(Duration.ofSeconds(30)) }
}
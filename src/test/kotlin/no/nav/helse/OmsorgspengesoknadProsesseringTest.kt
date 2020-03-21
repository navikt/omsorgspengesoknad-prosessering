package no.nav.helse

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import no.nav.helse.prosessering.v1.*
import no.nav.helse.prosessering.v1.asynkron.Journalfort
import no.nav.helse.prosessering.v1.asynkron.TopicEntry
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Ignore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
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
        private val kafkaTestProducer = kafkaEnvironment.meldingsProducer()
        private val kafkaTestProducerOverforeDager = kafkaEnvironment.meldingOverforeDagersProducer()

        private val journalføringsKonsumer = kafkaEnvironment.journalføringsKonsumer()
        private val journalføringsKonsumerOverforeDager = kafkaEnvironment.journalføringsKonsumerOverforeDager()
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
            journalføringsKonsumer.close()
            journalføringsKonsumerOverforeDager.close()
            kafkaTestProducer.close()
            kafkaTestProducerOverforeDager.close()
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
    //@Ignore
    fun`Gyldig søknad for overføring av dager blir prosessert av journalføringkonsumer`(){
        val søknad = gyldigMeldingOverforeDager(
            fødselsnummerSoker = gyldigFodselsnummerA,
            sprak = "nb"
        )

        kafkaTestProducerOverforeDager.leggTilMottak(søknad)
        journalføringsKonsumerOverforeDager.hentJournalførtMeldingOverforeDager(søknad.søknadId)
    }

    @Test
    fun `Gylding melding blir prosessert av journalføringskonsumer`() {
        val melding = gyldigMelding(
            fødselsnummerSoker = gyldigFodselsnummerA,
            fødselsnummerBarn = gyldigFodselsnummerB,
            barnetsFødselsdato = null
        )

        kafkaTestProducer.leggTilMottak(melding)
        journalføringsKonsumer.hentJournalførtMelding(melding.søknadId)
    }

    @Test
    fun `En feilprosessert melding vil bli prosessert etter at tjenesten restartes`() {
        val melding = gyldigMelding(
            fødselsnummerSoker = gyldigFodselsnummerA,
            fødselsnummerBarn = gyldigFodselsnummerB,
            barnetsFødselsdato = null
        )

        wireMockServer.stubJournalfor(500) // Simulerer feil ved journalføring

        kafkaTestProducer.leggTilMottak(melding)
        ventPaaAtRetryMekanismeIStreamProsessering()
        readyGir200HealthGir503()

        wireMockServer.stubJournalfor(201) // Simulerer journalføring fungerer igjen
        restartEngine()
        journalføringsKonsumer.hentJournalførtMelding(melding.søknadId)
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
            fødselsnummerBarn = gyldigFodselsnummerB, barnetsFødselsdato = null
        )

        kafkaTestProducer.leggTilMottak(melding)
        journalføringsKonsumer.hentJournalførtMelding(melding.søknadId)
    }

    @Test
    fun `Melding lagt til prosessering selv om sletting av vedlegg feiler`() {
        val melding = gyldigMelding(
            fødselsnummerSoker = gyldigFodselsnummerA,
            fødselsnummerBarn = gyldigFodselsnummerB,
            barnetsFødselsdato = null,
            barnetsNavn = "kari"
        )

        kafkaTestProducer.leggTilMottak(melding)
        journalføringsKonsumer.hentJournalførtMelding(melding.søknadId)
    }

    @Test
    fun `Melding lagt til prosessering selv om oppslag paa aktør ID for barn feiler`() {
        val melding = gyldigMelding(
            fødselsnummerSoker = gyldigFodselsnummerA,
            fødselsnummerBarn = gyldigFodselsnummerC,
            barnetsFødselsdato = null
        )

        wireMockServer.stubAktoerRegisterGetAktoerIdNotFound(gyldigFodselsnummerC)

        kafkaTestProducer.leggTilMottak(melding)
        preprossesertKonsumer.hentPreprossesertMelding(melding.søknadId)
    }

    @Test
    fun `Bruk barnets fødselsnummer til å slå opp i tps-proxy dersom navnet mangler`() {
        wireMockServer.stubTpsProxyGetNavn("KLØKTIG", "BLUNKENDE", "SUPERKONSOLL")
        val melding = gyldigMelding(
            fødselsnummerSoker = gyldigFodselsnummerC,
            fødselsnummerBarn = gyldigFodselsnummerB,
            barnetsNavn = null
        )

        kafkaTestProducer.leggTilMottak(melding)
        val preprossesertMelding: TopicEntry<PreprossesertMeldingV1> =
            preprossesertKonsumer.hentPreprossesertMelding(melding.søknadId)
        assertEquals("KLØKTIG BLUNKENDE SUPERKONSOLL", preprossesertMelding.data.barn.navn)
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

        kafkaTestProducer.leggTilMottak(melding)
        val hentOpprettetOppgave: TopicEntry<PreprossesertMeldingV1> =
            preprossesertKonsumer.hentPreprossesertMelding(melding.søknadId)
        assertEquals("KLØKTIG BLUNKENDE SUPERKONSOLL", hentOpprettetOppgave.data.barn.navn)
    }

    @Test
    fun `Forvent barnets fødselsnummer dersom den er satt i melding`() {
        wireMockServer.stubAktørRegister(gyldigFodselsnummerB, "56789")

        val forventetFodselsNummer: String = gyldigFodselsnummerB

        val melding = gyldigMelding(
            fødselsnummerSoker = gyldigFodselsnummerA,
            fødselsnummerBarn = forventetFodselsNummer,
            barnetsFødselsdato = null,
            barnetsNavn = null,
            aktørIdBarn = "56789"
        )

        kafkaTestProducer.leggTilMottak(melding)
        val journalførtMelding: TopicEntry<Journalfort> =
            journalføringsKonsumer.hentJournalførtMelding(melding.søknadId)
        assertEquals(forventetFodselsNummer, journalførtMelding.data.søknad.barn.norskIdentitetsnummer.verdi)
    }

    @Test
    fun `Forvent 2 legeerklæringer og 2 samværsavtaler dersom den er satt i melding`() {
        wireMockServer.stubAktørRegister(gyldigFodselsnummerB, "56789")

        val melding = gyldigMelding(
            fødselsnummerSoker = gyldigFodselsnummerA,
            fødselsnummerBarn = gyldigFodselsnummerB,
            barnetsFødselsdato = null,
            barnetsNavn = null,
            aktørIdBarn = "56789"
        )

        kafkaTestProducer.leggTilMottak(melding)
        val preprossesertMelding: TopicEntry<PreprossesertMeldingV1> =
            preprossesertKonsumer.hentPreprossesertMelding(melding.søknadId)
        assertEquals(5, preprossesertMelding.data.dokumentUrls.size)
        // 2 legeerklæringsvedlegg, 2, to samværsavtalevedlegg, og 1 søknadPdf.
    }

    @Test
    fun `Forvent barnets fødselsnummer blir slått opp dersom den ikke er satt i melding`() {
        val forventetFodselsNummer = gyldigFodselsnummerB

        val melding = gyldigMelding(
            fødselsnummerSoker = gyldigFodselsnummerA,
            fødselsnummerBarn = null,
            barnetsNavn = "Ole Dole Doffen",
            aktørIdBarn = "777777777"
        )

        kafkaTestProducer.leggTilMottak(melding)
        val prossesertMelding: TopicEntry<Journalfort> = journalføringsKonsumer.hentJournalførtMelding(melding.søknadId)
        assertEquals(forventetFodselsNummer, prossesertMelding.data.søknad.barn.norskIdentitetsnummer.verdi)
    }

    @Test
    fun `Forvent riktig format på journalført melding`() {
        val melding = gyldigMelding(
            fødselsnummerSoker = gyldigFodselsnummerA,
            fødselsnummerBarn = null,
            barnetsNavn = "Ole Dole Doffen",
            aktørIdBarn = "777777777"
        )

        kafkaTestProducer.leggTilMottak(melding)
        val prossesertMelding: TopicEntry<Journalfort> = journalføringsKonsumer.hentJournalførtMelding(melding.søknadId)
        println(
            jacksonObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(prossesertMelding)
        )
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
            norskIdentifikator = fødselsnummerBarn,
            aktørId = aktørIdBarn,
            fødselsdato = barnetsFødselsdato
        ),
        legeerklæring = listOf(
            URI("http://localhost:8080/vedlegg/1"),
            URI("http://localhost:8080/vedlegg/2")
        ),
        samværsavtale = listOf(
            URI("http://localhost:8080/vedlegg/3"),
            URI("http://localhost:8080/vedlegg/4")
        ),
        relasjonTilBarnet = "Mor",
        arbeidssituasjon = listOf("Arbeidstaker", "Frilans", "Selvstendig Næringsdrivende"),
        harBekreftetOpplysninger = true,
        harForståttRettigheterOgPlikter = true,
        medlemskap = Medlemskap(
            harBoddIUtlandetSiste12Mnd = true,
            skalBoIUtlandetNeste12Mnd = true
        )
    )

    private fun gyldigMeldingOverforeDager(
        fødselsnummerSoker: String,
        sprak: String
    ) : SøknadOverføreDagerV1 = SøknadOverføreDagerV1(
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
        arbeidssituasjon = listOf(Arbeidssituasjon.ARBEIDSTAKER),
        harBekreftetOpplysninger = true,
        harForståttRettigheterOgPlikter = true,
        medlemskap = Medlemskap(
            harBoddIUtlandetSiste12Mnd = true,
            skalBoIUtlandetNeste12Mnd = true
        ),
        antallDager = 5,
        mottakerAvDagerNorskIdentifikator = gyldigFodselsnummerB
    )

    private fun ventPaaAtRetryMekanismeIStreamProsessering() = runBlocking { delay(Duration.ofSeconds(30)) }
}

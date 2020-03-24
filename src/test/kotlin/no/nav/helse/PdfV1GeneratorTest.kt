package no.nav.helse

import no.nav.helse.aktoer.Fodselsnummer
import no.nav.helse.prosessering.v1.*
import org.junit.Ignore
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.test.Test

class PdfV1GeneratorTest {

    private companion object {
        private val generator = PdfV1Generator()
        private val barnetsIdent = Fodselsnummer("02119970078")
        private val barnetsFødselsdato = LocalDate.now()
        private val fødselsdato = LocalDate.now()
        private val barnetsNavn = "Ole Dole"
    }

    private fun fullGyldigMelding(soknadsId: String, barnetsFødselsdato: LocalDate? = null): MeldingV1 {
        return MeldingV1(
            språk = "nb",
            søknadId = soknadsId,
            mottatt = ZonedDateTime.now(),
            søker = Søker(
                aktørId = "123456",
                fornavn = "Ærling",
                mellomnavn = "Øverbø",
                etternavn = "Ånsnes",
                fødselsnummer = "29099012345",
                fødselsdato = fødselsdato
            ),
            barn = Barn(
                norskIdentifikator = barnetsIdent.getValue(),
                fødselsdato = barnetsFødselsdato,
                aktørId = "123456",
                navn = barnetsNavn
            ),
            relasjonTilBarnet = "Mor",
            arbeidssituasjon = listOf("Arbeidstaker", "Frilans", "Selvstendig Næringsdrivende"),
            medlemskap = Medlemskap(
                harBoddIUtlandetSiste12Mnd = true,
                utenlandsoppholdSiste12Mnd = listOf(
                    Utenlandsopphold(
                        LocalDate.of(2020, 1, 2),
                        LocalDate.of(2020, 1, 3),
                        "US", "USA"
                    )
                ),
                skalBoIUtlandetNeste12Mnd = false
            ),
            harForståttRettigheterOgPlikter = true,
            harBekreftetOpplysninger = true
        )
    }

    private fun gyldigMelding(
        soknadId: String,
        språk: String? = "nb",
        barn: Barn = Barn(
            navn = "Børge Øverbø Ånsnes",
            norskIdentifikator = null,
            aktørId = null,
            fødselsdato = barnetsFødselsdato
        ),
        medlemskap: Medlemskap = Medlemskap(
            harBoddIUtlandetSiste12Mnd = true,
            utenlandsoppholdSiste12Mnd = listOf(
                Utenlandsopphold(
                    LocalDate.of(2020, 1, 2),
                    LocalDate.of(2020, 1, 3),
                    "US", "USA"
                )
            ),
            skalBoIUtlandetNeste12Mnd = false
        )
    ) = MeldingV1(
        språk = språk,
        søknadId = soknadId,
        mottatt = ZonedDateTime.now(),
        søker = Søker(
            aktørId = "123456",
            fornavn = "Ærling",
            mellomnavn = "Øverbø",
            etternavn = "Ånsnes",
            fødselsnummer = "29099012345",
            fødselsdato = fødselsdato
        ),
        barn = barn,
        relasjonTilBarnet = "Onkel & Nærstående ' <> \" {}",
        arbeidssituasjon = listOf("Arbeidstaker", "Frilans", "Selvstendig Næringsdrivende"),
        medlemskap = medlemskap,
        harForståttRettigheterOgPlikter = true,
        harBekreftetOpplysninger = true
    )

    private fun gyldigSoknadOverforeDager() = SøknadOverføreDagerV1(
        språk = "nb",
        antallDager = 5,
        harBekreftetOpplysninger = true,
        harForståttRettigheterOgPlikter = true,
        arbeidssituasjon = listOf(Arbeidssituasjon.ARBEIDSTAKER, Arbeidssituasjon.FRILANSER, Arbeidssituasjon.SELVSTENDIGNÆRINGSDRIVENDE),
        søknadId = "Overføre dager",
        medlemskap = Medlemskap(
            harBoddIUtlandetSiste12Mnd = true,
            utenlandsoppholdSiste12Mnd = listOf(
                Utenlandsopphold(
                    LocalDate.of(2020, 1, 2),
                    LocalDate.of(2020, 1, 3),
                    "US", "USA"
                )
            ),
            skalBoIUtlandetNeste12Mnd = true,
            utenlandsoppholdNeste12Mnd = listOf(
                Utenlandsopphold(
                    fraOgMed = LocalDate.of(2020,2,1),
                    tilOgMed = LocalDate.of(2020,2,24),
                    landkode = "US",
                    landnavn = "USA"
                )
            )
        ),
        fnrMottaker = "123456789",
        mottatt = ZonedDateTime.now(),
        søker = Søker(
            aktørId = "123456",
            fornavn = "Ærling",
            mellomnavn = "Øverbø",
            etternavn = "Ånsnes",
            fødselsnummer = "29099012345",
            fødselsdato = fødselsdato
        ),
        fosterbarn = listOf(
            Fosterbarn("29099012345","Kjell","Olsen"),
            Fosterbarn("02119970078","Torkel","Olsen")
        )
    )

    private fun genererOppsummeringsPdfer(writeBytes: Boolean) {
        var id = "1-full-søknad"
        var pdf = generator.generateSoknadOppsummeringPdf(
            melding = fullGyldigMelding(soknadsId = id),
            barnetsIdent = barnetsIdent,
            barnetsNavn = barnetsNavn
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)

        id = "2-full-søknad-barnets-fødsesldato"
        pdf = generator.generateSoknadOppsummeringPdf(
            melding = fullGyldigMelding(soknadsId = id, barnetsFødselsdato = LocalDate.now().minusDays(4)),
            barnetsIdent = null,
            barnetsNavn = barnetsNavn
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)

        id = "3-full-søknad-overfore-dager"
        pdf = generator.generateSoknadOppsummeringPdfOverforeDager(
            melding = gyldigSoknadOverforeDager()
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)
    }

    private fun pdfPath(soknadId: String) = "${System.getProperty("user.dir")}/generated-pdf-$soknadId.pdf"

    @Test
    fun `generering av oppsummerings-PDF fungerer`() {
        genererOppsummeringsPdfer(false)
    }

    @Test
    @Ignore
    fun `opprett lesbar oppsummerings-PDF`() {
        genererOppsummeringsPdfer(true)
    }
}
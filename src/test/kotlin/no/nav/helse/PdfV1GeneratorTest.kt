package no.nav.helse

import no.nav.helse.aktoer.Fodselsnummer
import no.nav.helse.prosessering.v1.*
import java.io.File
import java.net.URI
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.test.Ignore
import kotlin.test.Test

class PdfV1GeneratorTest {

    private companion object {
        private val generator = PdfV1Generator()
        private val barnetsIdent = Fodselsnummer("02119970078")
        private val barnetsFødselsdato = LocalDate.now()
        private val fødselsdato = LocalDate.now()
        private val barnetsNavn = "Ole Dole"
    }

    private fun fullGyldigMelding(soknadsId: String, barnetsFødselsdato: LocalDate? = null, legeerklæring: List<URI> = listOf()): MeldingV1 {
        return MeldingV1(
            språk = "nb",
            søknadId = soknadsId,
            mottatt = ZonedDateTime.now(),
            legeerklæring = legeerklæring,
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
            arbeidssituasjon = listOf("Arbeidstaker", "Frilans", "Selvstendig næringsdrivende"),
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

        id = "3-full-søknad-legeerklæring-lastet-opp"
        pdf = generator.generateSoknadOppsummeringPdf(
            melding = fullGyldigMelding(soknadsId = id, legeerklæring = listOf(URI("vedlegg"))),
            barnetsIdent = null,
            barnetsNavn = null
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)

    }

    private fun pdfPath(soknadId: String) = "${System.getProperty("user.dir")}/generated-pdf-$soknadId.pdf"

    @Test
    fun `generering av oppsummerings-PDF fungerer`() {
        genererOppsummeringsPdfer(false)
    }

    @Test
    fun `opprett lesbar oppsummerings-PDF`() {
        genererOppsummeringsPdfer(true)
    }
}
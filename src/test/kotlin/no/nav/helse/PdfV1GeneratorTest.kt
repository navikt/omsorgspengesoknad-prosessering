package no.nav.helse


import no.nav.helse.prosessering.Metadata
import no.nav.helse.prosessering.v1.*
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.Test

class PdfV1GeneratorTest {

    private companion object {
        private val generator = PdfV1Generator()
        private val metadata = Metadata(
            soknadDialogCommitSha = "abc-123",
            version = 1,
            correlationId = UUID.randomUUID().toString()
        )
    }

    private fun fullGyldigMelding(
        soknadsId: String,
        legeerklæringVedleggId: List<String> = listOf()
    ): MeldingV1 {
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
                fødselsdato = LocalDate.now().minusYears(20)
            ),
            barn = Barn(
                norskIdentifikator = "02119970078",
                fødselsdato = LocalDate.now(),
                aktørId = "123456",
                navn = "Ole Dole"
            ),
            legeerklæringVedleggId = legeerklæringVedleggId,
            relasjonTilBarnet = SøkerBarnRelasjon.MOR,
            harForståttRettigheterOgPlikter = true,
            harBekreftetOpplysninger = true,
            k9FormatSøknad = SøknadUtils.k9Format
        )
    }

    private fun genererOppsummeringsPdfer(writeBytes: Boolean) {
        var id = "1-full-søknad"
        var pdf = generator.generateSoknadOppsummeringPdf(
            melding = fullGyldigMelding(soknadsId = id),
            metadata = metadata
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)

        id = "2-full-søknad-legeerklæring-lastet-opp"
        pdf = generator.generateSoknadOppsummeringPdf(
            melding = fullGyldigMelding(soknadsId = id, legeerklæringVedleggId = listOf("123")),
            metadata = metadata
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

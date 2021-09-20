package no.nav.helse.prosessering.v1

import no.nav.helse.dokument.Dokument
import no.nav.helse.dokument.DokumentEier
import no.nav.helse.dokument.K9MellomlagringService
import no.nav.helse.dokument.Søknadsformat
import no.nav.helse.felles.AktørId
import no.nav.helse.felles.CorrelationId
import no.nav.helse.prosessering.Metadata
import org.slf4j.LoggerFactory

internal class PreprosesseringV1Service(
    private val pdfV1Generator: PdfV1Generator,
    private val k9MellomlagringService: K9MellomlagringService,
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(PreprosesseringV1Service::class.java)
    }

    internal suspend fun preprosesser(
        melding: MeldingV1,
        metadata: Metadata
    ): PreprosessertMeldingV1 {
        logger.info("Genererer Oppsummerings-PDF av søknaden.")
        val soknadOppsummeringPdf = pdfV1Generator.generateSoknadOppsummeringPdf(melding)

        val dokumentEier = DokumentEier(melding.søker.fødselsnummer)
        val correlationId = CorrelationId(metadata.correlationId)

        logger.info("Mellomlagrer Oppsummerings-PDF.")
        val oppsummeringPdfUrl = k9MellomlagringService.lagreDokument(
            dokument = Dokument(
                eier = dokumentEier,
                content = soknadOppsummeringPdf,
                contentType = "application/pdf",
                title = "Søknad om ekstra omsorgsdager"
            ),
            correlationId = correlationId
        )

        logger.info("Mellomlagrer Oppsummerings-JSON")
        val søknadJsonUrl = k9MellomlagringService.lagreDokument(
            dokument = Dokument(
                eier = dokumentEier,
                content = Søknadsformat.somJson(melding.k9FormatSøknad),
                contentType = "application/json",
                title = "Søknad om omsorgspenger som JSON"
            ),
            correlationId = correlationId
        )

        val komplettDokumentUrls = mutableListOf(
            listOf(
                oppsummeringPdfUrl,
                søknadJsonUrl
            )
        )

        melding.samværsavtale?.let { liste ->
            liste.forEach { komplettDokumentUrls.add(listOf(it)) }
        }

        melding.legeerklæring.forEach { komplettDokumentUrls.add(listOf(it)) }

        logger.info("Totalt ${komplettDokumentUrls.size} dokumentbolker med totalt ${komplettDokumentUrls.flatten().size} dokumenter.")

        val preprosessertMeldingV1 = PreprosessertMeldingV1(
            melding = melding,
            dokumentUrls = komplettDokumentUrls.toList(),
            søkerAktørId = AktørId(melding.søker.aktørId)
        )

        melding.reportMetrics()
        preprosessertMeldingV1.reportMetrics()
        return preprosessertMeldingV1
    }
}

package no.nav.helse.prosessering.v1

import no.nav.helse.dokument.Dokument
import no.nav.helse.dokument.DokumentEier
import no.nav.helse.dokument.K9MellomlagringService
import no.nav.helse.dokument.Søknadsformat
import no.nav.helse.felles.CorrelationId
import no.nav.helse.prosessering.Metadata
import org.slf4j.LoggerFactory
import java.net.URI

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
        val dokumentEier = DokumentEier(melding.søker.fødselsnummer)
        val correlationId = CorrelationId(metadata.correlationId)

        logger.info("Genererer Oppsummerings-PDF av søknaden.")
        val soknadOppsummeringPdf = pdfV1Generator.generateSoknadOppsummeringPdf(melding)

        logger.info("Mellomlagrer Oppsummerings-PDF.")
        val oppsummeringPdfVedleggId = k9MellomlagringService.lagreDokument(
            dokument = Dokument(
                eier = dokumentEier,
                content = soknadOppsummeringPdf,
                contentType = "application/pdf",
                title = "Søknad om ekstra omsorgsdager"
            ),
            correlationId = correlationId
        ).vedleggId()

        logger.info("Mellomlagrer Oppsummerings-JSON")
        val søknadJsonVedleggId = k9MellomlagringService.lagreDokument(
            dokument = Dokument(
                eier = dokumentEier,
                content = Søknadsformat.somJson(melding.k9FormatSøknad),
                contentType = "application/json",
                title = "Søknad om omsorgspenger som JSON"
            ),
            correlationId = correlationId
        ).vedleggId()

        val komplettVedleggId = mutableListOf(
            listOf(
                oppsummeringPdfVedleggId,
                søknadJsonVedleggId
            )
        )

        if(melding.legeerklæringVedleggId.isNotEmpty()){
            logger.info("Legger til vedleggId's for legeerklæring fra melding.")
            melding.legeerklæringVedleggId.forEach { vedleggId ->
                komplettVedleggId.add(listOf(vedleggId))
            }
        }

        if(melding.samværsavtaleVedleggId.isNotEmpty()){
            logger.info("Legger til vedleggId's for samværsavtale fra melding.")
            melding.samværsavtaleVedleggId.forEach { vedleggId ->
                komplettVedleggId.add(listOf(vedleggId))
            }
        }

        melding.samværsavtale?.let { liste ->
            liste.forEach { komplettVedleggId.add(listOf(it.vedleggId())) }
        }

        melding.legeerklæring.forEach { komplettVedleggId.add(listOf(it.vedleggId())) }

        logger.info("Totalt ${komplettVedleggId.size} dokumentbolker med totalt ${komplettVedleggId.flatten().size} dokumenter.")

        val preprosessertMeldingV1 = PreprosessertMeldingV1(
            melding = melding,
            vedleggId = komplettVedleggId.toList()
        )

        melding.reportMetrics()
        preprosessertMeldingV1.reportMetrics()
        return preprosessertMeldingV1
    }
}

fun URI.vedleggId(): String = this.toString().substringAfterLast("/")

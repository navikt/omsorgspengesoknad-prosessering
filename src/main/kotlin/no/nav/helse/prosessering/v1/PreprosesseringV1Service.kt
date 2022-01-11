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
        val oppsummeringPdfDokumentId = k9MellomlagringService.lagreDokument(
            dokument = Dokument(
                eier = dokumentEier,
                content = soknadOppsummeringPdf,
                contentType = "application/pdf",
                title = "Søknad om ekstra omsorgsdager"
            ),
            correlationId = correlationId
        ).dokumentId()

        logger.info("Mellomlagrer Oppsummerings-JSON")
        val søknadJsonDokumentId = k9MellomlagringService.lagreDokument(
            dokument = Dokument(
                eier = dokumentEier,
                content = Søknadsformat.somJson(melding.k9FormatSøknad),
                contentType = "application/json",
                title = "Søknad om omsorgspenger som JSON"
            ),
            correlationId = correlationId
        ).dokumentId()

        val komplettDokumentId = mutableListOf(
            listOf(
                oppsummeringPdfDokumentId,
                søknadJsonDokumentId
            )
        )

        if(melding.legeerklæring.isNotEmpty()){
            logger.info("Legger til dokumentId's for legeerklæring fra melding.")
            melding.legeerklæring.forEach { vedlegg ->
                if(vedlegg.contains("/")){
                    logger.info("Mapper om url til dokumentId")
                    komplettDokumentId.add(listOf(vedlegg.dokumentId()))
                } else komplettDokumentId.add(listOf(vedlegg))
            }
        }

        if(melding.samværsavtale.isNotEmpty()){
            logger.info("Legger til dokumentId's for samværsavtale fra melding.")
            melding.samværsavtale.forEach { vedlegg ->
                if(vedlegg.contains("/")){
                    logger.info("Mapper om url til dokumentId")
                    komplettDokumentId.add(listOf(vedlegg.dokumentId()))
                } else komplettDokumentId.add(listOf(vedlegg))
            }
        }

        logger.info("Totalt ${komplettDokumentId.size} dokumentbolker med totalt ${komplettDokumentId.flatten().size} dokumenter.")

        val preprosessertMeldingV1 = PreprosessertMeldingV1(
            melding = melding,
            dokumentId = komplettDokumentId.toList()
        )

        melding.reportMetrics()
        preprosessertMeldingV1.reportMetrics()
        return preprosessertMeldingV1
    }
}

fun URI.dokumentId(): String = this.toString().substringAfterLast("/")
fun String.dokumentId(): String = this.toString().substringAfterLast("/")

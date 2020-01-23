package no.nav.helse.dokument

import no.nav.helse.CorrelationId
import no.nav.helse.aktoer.AktørId
import no.nav.helse.prosessering.v1.MeldingV1
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI

private val logger: Logger = LoggerFactory.getLogger("nav.DokumentService")

class DokumentService(
    private val dokumentGateway: DokumentGateway
) {
    private suspend fun lagreDokument(
        dokument: DokumentGateway.Dokument,
        aktørId: AktørId,
        correlationId: CorrelationId
    ) : URI {
        return dokumentGateway.lagreDokmenter(
            dokumenter = setOf(dokument),
            correlationId = correlationId,
            aktørId = aktørId
        ).first()
    }

    internal suspend fun lagreSoknadsOppsummeringPdf(
        pdf : ByteArray,
        aktørId: AktørId,
        correlationId: CorrelationId
    ) : URI {
        return lagreDokument(
            dokument = DokumentGateway.Dokument(
                content = pdf,
                contentType = "application/pdf",
                title = "Søknad om omsorgspenger"
            ),
            aktørId = aktørId,
            correlationId = correlationId
        )
    }

    internal suspend fun lagreSoknadsMelding(
        melding: MeldingV1,
        aktørId: AktørId,
        correlationId: CorrelationId
    ) : URI {
        return lagreDokument(
            dokument = DokumentGateway.Dokument(
                content = JournalforingsFormat.somJson(melding),
                contentType = "application/json",
                title = "Søknad om omsorgspenger som JSON"
            ),
            aktørId = aktørId,
            correlationId = correlationId
        )
    }

    internal suspend fun slettDokumeter(
        urlBolks: List<List<URI>>,
        aktørId: AktørId,
        correlationId : CorrelationId
    ) {
        val urls = mutableListOf<URI>()
        urlBolks.forEach { urls.addAll(it) }

        logger.trace("Sletter ${urls.size} dokumenter")
        dokumentGateway.slettDokmenter(
            urls = urls,
            aktørId = aktørId,
            correlationId = correlationId
        )
    }
}


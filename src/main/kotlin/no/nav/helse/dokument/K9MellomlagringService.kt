package no.nav.helse.dokument

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.helse.felles.CorrelationId
import java.net.URI

class K9MellomlagringService(
    private val k9MellomlagringGateway: K9MellomlagringGateway
) {

    internal suspend fun lagreDokument(
        dokument: Dokument,
        correlationId: CorrelationId
    ) : URI {
        return k9MellomlagringGateway.lagreDokmenter(
            dokumenter = setOf(dokument),
            correlationId = correlationId
        ).first()
    }

    internal suspend fun slettDokumeter(
        urlBolks: List<List<URI>>,
        dokumentEier: DokumentEier,
        correlationId : CorrelationId
    ) {
        k9MellomlagringGateway.slettDokmenter(
            urls = urlBolks.flatten(),
            dokumentEier = dokumentEier,
            correlationId = correlationId
        )
    }
}

data class DokumentEier(
    @JsonProperty("eiers_fødselsnummer")
    val eiersFødselsnummer: String
)

data class Dokument(
    val eier: DokumentEier,
    val content: ByteArray,
    @JsonProperty("content_type")
    val contentType: String,
    val title: String
)


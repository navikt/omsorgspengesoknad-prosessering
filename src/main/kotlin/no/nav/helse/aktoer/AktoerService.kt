package no.nav.helse.aktoer

import no.nav.helse.CorrelationId

class AktoerService(
    private val aktoerGateway: AktoerGateway
){
    suspend fun getAktorId(
        ident: NorskIdent,
        correlationId: CorrelationId
    ): AktørId {
        return aktoerGateway.getAktoerId(ident, correlationId)
    }

    suspend fun getIdent(aktoerId: String, correlationId: CorrelationId): NorskIdent {
        return aktoerGateway.hentNorskIdent(aktørId = AktørId(id = aktoerId), correlationId = correlationId)
    }
}
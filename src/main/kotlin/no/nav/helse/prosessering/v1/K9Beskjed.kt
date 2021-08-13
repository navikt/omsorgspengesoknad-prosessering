package no.nav.helse.prosessering.v1

import no.nav.helse.prosessering.Metadata
import no.nav.helse.prosessering.v1.asynkron.Cleanup
import java.util.*

data class K9Beskjed(
    val metadata: Metadata,
    val grupperingsId: String,
    val tekst: String,
    val link: String?,
    val dagerSynlig: Long,
    val søkerFødselsnummer: String,
    val eventId: String,
    val ytelse: String
)

const val TEKST_K9BESKJED: String = "Vi har mottatt søknad fra deg om ekstra omsorgsdager ved kronisk sykt eller funksjonshemmet barn."
const val YTELSE_K9BESKJED: String = "OMSORGSPENGER_UTV_KS"
const val DAGER_SYNLIG_K9BESKJED: Long = 7

fun Cleanup.tilK9Beskjed(): K9Beskjed {
    return K9Beskjed(
        metadata = this.metadata,
        grupperingsId = this.melding.soknadId,
        tekst = TEKST_K9BESKJED,
        søkerFødselsnummer = this.melding.søker.fødselsnummer,
        dagerSynlig = DAGER_SYNLIG_K9BESKJED,
        link = null,
        eventId = UUID.randomUUID().toString(),
        ytelse = YTELSE_K9BESKJED
    )
}
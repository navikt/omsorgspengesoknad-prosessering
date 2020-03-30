package no.nav.helse.prosessering.v1.ettersending

import no.nav.helse.prosessering.v1.Søker
import java.net.URI
import java.time.ZonedDateTime

data class EttersendingV1(
    val søker : Søker,
    val søknadId: String,
    val mottatt: ZonedDateTime,
    val språk: String,
    val vedleggUrls: List<URI>,
    val harForståttRettigheterOgPlikter: Boolean,
    val harBekreftetOpplysninger: Boolean,
    val beskrivelse: String,
    val søknadstype: String,
    val titler: List<String>
)
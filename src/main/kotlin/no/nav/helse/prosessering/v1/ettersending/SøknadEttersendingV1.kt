package no.nav.helse.prosessering.v1.ettersending

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.helse.prosessering.v1.Søker
import java.net.URL
import java.time.ZonedDateTime

data class SøknadEttersending(
    val soker : Søker,
    val søknadId: String,
    val mottatt: ZonedDateTime,
    val språk: String,
    val vedleggUrls: List<URL>,
    val harForståttRettigheterOgPlikter: Boolean,
    val harBekreftetOpplysninger: Boolean,
    val beskrivelse: String,
    val søknadstype: Søknadstype
)

enum class Søknadstype() {
    @JsonProperty("ukjent") UKJENT,
    @JsonProperty("pleiepenger") PLEIEPENGER,
    @JsonProperty("omsorgspenger") OMSORGSPENGER
}
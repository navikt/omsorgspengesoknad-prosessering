package no.nav.helse.prosessering.v1.ettersending

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.helse.prosessering.v1.Medlemskap
import no.nav.helse.prosessering.v1.Søker
import java.net.URI
import java.time.ZonedDateTime

data class SøknadEttersendingV1(
    val søker : Søker,
    val søknadId: String,
    val mottatt: ZonedDateTime,
    val språk: String,
    @JsonProperty("vedlegg_urls")
    @JsonAlias("vedlegg_urls","vedleggUrls")
    val vedleggUrls: List<URI>, //TODO: Fjerne snake_case over til camelCase
    val harForståttRettigheterOgPlikter: Boolean,
    val harBekreftetOpplysninger: Boolean,
    val beskrivelse: String,
    val søknadstype: String
)
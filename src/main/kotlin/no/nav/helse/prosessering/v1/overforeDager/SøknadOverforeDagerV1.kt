package no.nav.helse.prosessering.v1.overforeDager

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.helse.prosessering.v1.Medlemskap
import no.nav.helse.prosessering.v1.Søker
import java.time.ZonedDateTime

data class SøknadOverføreDagerV1 (
    val søknadId: String,
    val mottatt: ZonedDateTime,
    val søker: Søker,
    val språk: String? = "nb",
    val antallDager: Int,
    val fnrMottaker: String,
    val medlemskap: Medlemskap,
    val harForståttRettigheterOgPlikter: Boolean,
    val harBekreftetOpplysninger: Boolean,
    val arbeidssituasjon: List<Arbeidssituasjon>,
    val fosterbarn: List<Fosterbarn>? = listOf()
)

data class Fosterbarn(
    val fødselsnummer: String,
    val fornavn: String,
    val etternavn: String
)

enum class Arbeidssituasjon(val utskriftvennlig: String) {
    @JsonProperty("arbeidstaker") ARBEIDSTAKER("Arbeidstaker"),
    @JsonProperty("selvstendigNæringsdrivende") SELVSTENDIGNÆRINGSDRIVENDE("Selvstendig næringsdrivende"),
    @JsonProperty("frilanser") FRILANSER("Frilanser")
}
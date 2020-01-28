package no.nav.helse.prosessering.v1

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import java.net.URI
import java.time.LocalDate
import java.time.ZonedDateTime

data class MeldingV1 (
    val nyVersjon: Boolean = false,
    val søknadId: String,
    val mottatt: ZonedDateTime,
    val språk: String? = "nb",
    val kroniskEllerFunksjonshemming: Boolean = false,
    val erYrkesaktiv: Boolean = false,
    val barn : Barn,
    val søker : Søker,
    val relasjonTilBarnet : String,
    val delerOmsorg : Boolean = false,
    val sammeAddresse : Boolean = false,
    val medlemskap: Medlemskap,
    val utenlandsopphold: List<Utenlandsopphold> = listOf(),
    val harBekreftetOpplysninger : Boolean,
    var legeerklæring : List<URI> = listOf(),
    var samværsavtale : List<URI> = listOf(),
    val harForstattRettigheterOgPlikter : Boolean
)

data class Søker(
    val fødselsnummer: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    @JsonFormat(pattern = "yyyy-MM-dd") val fødselsdato : LocalDate?,
    val aktørId: String
) {
    override fun toString(): String {
        return "Soker(fornavn='$fornavn', mellomnavn=$mellomnavn, etternavn='$etternavn', fødselsdato=$fødselsdato, aktørId='$aktørId')"
    }
}

data class Barn(
    val navn : String?,
    val fødselsnummer: String?,
    @JsonFormat(pattern = "yyyy-MM-dd") val fødselsdato : LocalDate?,
    val aktørId: String?
) {
    override fun toString(): String {
        return "Barn(navn=$navn, aktørId=$aktørId)"
    }
}

data class Organisasjon(
    val organisasjonsnummer: String,
    val navn: String?,
    val skalJobbe: String? = null,
    val jobberNormaltTimer: Double? = null,
    val skalJobbeProsent: Double?  = null,
    val vetIkkeEkstrainfo: String? = null
)

data class Medlemskap(
    @JsonProperty("har_bodd_i_utlandet_siste_12_mnd")
    val harBoddIUtlandetSiste12Mnd : Boolean,
    @JsonProperty("utenlandsopphold_siste_12_mnd")
    val utenlandsoppholdSiste12Mnd: List<Utenlandsopphold> = listOf(),
    @JsonProperty("skal_bo_i_utlandet_neste_12_mnd")
    val skalBoIUtlandetNeste12Mnd : Boolean,
    @JsonProperty("utenlandsopphold_neste_12_mnd")
    val utenlandsoppholdNeste12Mnd: List<Utenlandsopphold> = listOf()
)

data class Utenlandsopphold(
    @JsonFormat(pattern = "yyyy-MM-dd") val fraOgMed: LocalDate,
    @JsonFormat(pattern = "yyyy-MM-dd") val tilOgMed: LocalDate,
    val landkode: String,
    val landnavn: String
) {
    override fun toString(): String {
        return "Utenlandsopphold(fraOgMed=$fraOgMed, tilOgMed=$tilOgMed, landkode='$landkode', landnavn='$landnavn')"
    }
}
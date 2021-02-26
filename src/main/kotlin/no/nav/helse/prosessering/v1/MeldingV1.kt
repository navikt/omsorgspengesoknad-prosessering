package no.nav.helse.prosessering.v1

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonFormat
import no.nav.k9.søknad.Søknad
import java.net.URI
import java.time.LocalDate
import java.time.ZonedDateTime

data class MeldingV1(
    val nyVersjon: Boolean = false,
    val søknadId: String,
    val mottatt: ZonedDateTime,
    val språk: String? = "nb",
    val kroniskEllerFunksjonshemming: Boolean = false,
    val arbeidssituasjon: List<Arbeidssituasjon>? = null, //TODO 23.02.2021 - Fjernes når frontend er prodsatt
    val barn: Barn,
    val søker: Søker,
    val relasjonTilBarnet: SøkerBarnRelasjon? = null,
    val sammeAdresse: Boolean = false,
    val medlemskap: Medlemskap? = null, //TODO 23.02.2021 - Fjernes når frontend er prodsatt
    var legeerklæring: List<URI> = listOf(),
    var samværsavtale: List<URI> = listOf(),
    val harBekreftetOpplysninger: Boolean,
    val harForståttRettigheterOgPlikter: Boolean,
    val k9FormatSøknad: Søknad? = null
)

data class Søker(
    val fødselsnummer: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    @JsonFormat(pattern = "yyyy-MM-dd") val fødselsdato: LocalDate?,
    val aktørId: String
) {
    override fun toString(): String {
        return "Soker(fornavn='$fornavn', mellomnavn=$mellomnavn, etternavn='$etternavn', fødselsdato=$fødselsdato, aktørId='$aktørId')"
    }
}

data class Barn(
    val navn: String?,
    val norskIdentifikator: String?,
    @JsonFormat(pattern = "yyyy-MM-dd") val fødselsdato: LocalDate? = null,
    val aktørId: String?
) {
    override fun toString(): String {
        return "Barn(navn=$navn, aktørId=$aktørId)"
    }
}

enum class SøkerBarnRelasjon(val utskriftsvennlig: String) {
    @JsonAlias("mor") MOR("Mor"), //TODO 25.02.2021 - Alias kan fjernes når api og prosessering har vært prodsatt en liten stund.
    @JsonAlias("far") FAR("Far"),
    @JsonAlias("adoptivforelder") ADOPTIVFORELDER("Adoptivforelder"),
    @JsonAlias("fosterforelder") FOSTERFORELDER("Fosterforelder")
}

data class Medlemskap( //TODO 23.02.2021 - Fjernes når frontend er prodsatt
    val harBoddIUtlandetSiste12Mnd: Boolean,
    val utenlandsoppholdSiste12Mnd: List<Utenlandsopphold> = listOf(),
    val skalBoIUtlandetNeste12Mnd: Boolean,
    val utenlandsoppholdNeste12Mnd: List<Utenlandsopphold> = listOf()
)

data class Utenlandsopphold( //TODO 23.02.2021 - Fjernes når frontend er prodsatt
    @JsonFormat(pattern = "yyyy-MM-dd") val fraOgMed: LocalDate,
    @JsonFormat(pattern = "yyyy-MM-dd") val tilOgMed: LocalDate,
    val landkode: String,
    val landnavn: String
) {
    override fun toString(): String {
        return "Utenlandsopphold(fraOgMed=$fraOgMed, tilOgMed=$tilOgMed, landkode='$landkode', landnavn='$landnavn')"
    }
}

enum class Arbeidssituasjon(val utskriftsvennlig: String){ //TODO 23.02.2021 - Fjernes når frontend er prodsatt
    SELVSTENDIG_NÆRINGSDRIVENDE("Selvstendig næringsdrivende"),
    ARBEIDSTAKER("Arbeidstaker"),
    FRILANSER("Frilanser")
}

internal fun List<Arbeidssituasjon>.somMapTilPDF(): List<Map<String, Any?>> { //TODO 23.02.2021 - Fjernes når frontend er prodsatt
    return map {
        mapOf<String, Any?>(
            "utskriftsvennlig" to it.utskriftsvennlig
        )
    }
}

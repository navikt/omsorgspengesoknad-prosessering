package no.nav.helse.prosessering.v1

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
    val barn: Barn,
    val søker: Søker,
    val relasjonTilBarnet: SøkerBarnRelasjon? = null,
    val sammeAdresse: Boolean = false,
    var legeerklæring: List<URI> = listOf(),
    var samværsavtale: List<URI>? = listOf(),
    val legeerklæringVedleggId: List<String> = listOf(),
    val samværsavtaleVedleggId: List<String> = listOf(),
    val harBekreftetOpplysninger: Boolean,
    val harForståttRettigheterOgPlikter: Boolean,
    val k9FormatSøknad: Søknad
) {
    override fun toString(): String {
        return "MeldingV1(søknadId='$søknadId', mottatt=$mottatt)"
    }
}

data class Søker(
    val fødselsnummer: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    @JsonFormat(pattern = "yyyy-MM-dd") val fødselsdato: LocalDate?,
    val aktørId: String
) {
    override fun toString(): String {
        return "Soker()"
    }
}

data class Barn(
    val navn: String,
    val norskIdentifikator: String,
    @JsonFormat(pattern = "yyyy-MM-dd") val fødselsdato: LocalDate? = null,
    val aktørId: String?
) {
    override fun toString(): String {
        return "Barn()"
    }
}

enum class SøkerBarnRelasjon(val utskriftsvennlig: String) {
    MOR("Mor"),
    FAR("Far"),
    ADOPTIVFORELDER("Adoptivforelder"),
    FOSTERFORELDER("Fosterforelder")
}
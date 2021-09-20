package no.nav.helse.prosessering.v1

import no.nav.k9.søknad.Søknad
import java.net.URI
import java.time.ZonedDateTime

data class PreprosessertMeldingV1(
    val soknadId: String,
    val mottatt: ZonedDateTime,
    val språk: String?,
    val dokumentUrls: List<List<URI>>,
    val kroniskEllerFunksjonshemming: Boolean,
    val barn: Barn,
    val søker: Søker,
    val relasjonTilBarnet: SøkerBarnRelasjon? = null,
    val sammeAdresse: Boolean = false,
    val harBekreftetOpplysninger: Boolean,
    val harForståttRettigheterOgPlikter: Boolean,
    val k9FormatSøknad: Søknad
) {
    internal constructor(
        melding: MeldingV1,
        dokumentUrls: List<List<URI>>
    ) : this(
        språk = melding.språk,
        soknadId = melding.søknadId,
        mottatt = melding.mottatt,
        dokumentUrls = dokumentUrls,
        kroniskEllerFunksjonshemming = melding.kroniskEllerFunksjonshemming,
        søker = melding.søker,
        sammeAdresse = melding.sammeAdresse,
        barn = melding.barn,
        relasjonTilBarnet = melding.relasjonTilBarnet,
        harForståttRettigheterOgPlikter = melding.harForståttRettigheterOgPlikter,
        harBekreftetOpplysninger = melding.harBekreftetOpplysninger,
        k9FormatSøknad = melding.k9FormatSøknad
    )

    override fun toString(): String {
        return "PreprosessertMeldingV1(soknadId='$soknadId', mottatt=$mottatt)"
    }

}
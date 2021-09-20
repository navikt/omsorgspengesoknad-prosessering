package no.nav.helse.prosessering.v1

import no.nav.helse.felles.AktørId
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
    val søker: PreprossesertSøker,
    val relasjonTilBarnet: SøkerBarnRelasjon? = null,
    val sammeAdresse: Boolean = false,
    val harBekreftetOpplysninger: Boolean,
    val harForståttRettigheterOgPlikter: Boolean,
    val k9FormatSøknad: Søknad
) {
    internal constructor(
        melding: MeldingV1,
        dokumentUrls: List<List<URI>>,
        søkerAktørId: AktørId
    ) : this(
        språk = melding.språk,
        soknadId = melding.søknadId,
        mottatt = melding.mottatt,
        dokumentUrls = dokumentUrls,
        kroniskEllerFunksjonshemming = melding.kroniskEllerFunksjonshemming,
        søker = PreprossesertSøker(melding.søker, søkerAktørId),
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

data class PreprossesertSøker(
    val fødselsnummer: String,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val aktørId: String
) {
    internal constructor(søker: Søker, aktørId: AktørId) : this(
        fødselsnummer = søker.fødselsnummer,
        fornavn = søker.fornavn,
        mellomnavn = søker.mellomnavn,
        etternavn = søker.etternavn,
        aktørId = aktørId.id
    )

    override fun toString(): String {
        return "PreprossesertSøker()"
    }

}
package no.nav.helse.prosessering.v1

import no.nav.helse.aktoer.AktørId
import no.nav.helse.aktoer.Fodselsnummer
import no.nav.helse.aktoer.NorskIdent
import no.nav.k9.søknad.Søknad
import java.net.URI
import java.time.LocalDate
import java.time.ZonedDateTime

data class PreprossesertMeldingV1(
    val soknadId: String,
    val mottatt: ZonedDateTime,
    val språk: String?,
    val dokumentUrls: List<List<URI>>,
    val kroniskEllerFunksjonshemming: Boolean,
    val barn: PreprossesertBarn,
    val søker: PreprossesertSøker,
    val relasjonTilBarnet: SøkerBarnRelasjon? = null,
    val sammeAdresse: Boolean = false,
    val harBekreftetOpplysninger: Boolean,
    val harForståttRettigheterOgPlikter: Boolean,
    val k9FormatSøknad: Søknad? = null
) {
    internal constructor(
        melding: MeldingV1,
        dokumentUrls: List<List<URI>>,
        søkerAktørId: AktørId,
        barnAktørId: AktørId?,
        barnetsNavn: String?,
        barnetsNorskeIdent: NorskIdent?,
        k9FormatSøknad: Søknad? = null
    ) : this(
        språk = melding.språk,
        soknadId = melding.søknadId,
        mottatt = melding.mottatt,
        dokumentUrls = dokumentUrls,
        kroniskEllerFunksjonshemming = melding.kroniskEllerFunksjonshemming,
        søker = PreprossesertSøker(melding.søker, søkerAktørId),
        sammeAdresse = melding.sammeAdresse,
        barn = PreprossesertBarn(melding.barn, melding.barn.fødselsdato, barnetsNavn, barnetsNorskeIdent, barnAktørId),
        relasjonTilBarnet = melding.relasjonTilBarnet,
        harForståttRettigheterOgPlikter = melding.harForståttRettigheterOgPlikter,
        harBekreftetOpplysninger = melding.harBekreftetOpplysninger,
        k9FormatSøknad = melding.k9FormatSøknad ?: k9FormatSøknad
    )
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
}

data class PreprossesertBarn(
    val norskIdentifikator: String?,
    val fødselsDato: LocalDate? = null,
    val navn: String?,
    val aktoerId: String?
) {

    internal constructor(
        barn: Barn,
        barnetsFødselsdato: LocalDate?,
        barnetsNavn: String?,
        barnetsNorskeIdent: NorskIdent?,
        aktørId: AktørId?
    ) : this(
        norskIdentifikator = barn.norskIdentifikator ?: (barnetsNorskeIdent as? Fodselsnummer)?.getValue(),
        fødselsDato = barnetsFødselsdato,
        navn = barnetsNavn,
        aktoerId = aktørId?.id
    )

    override fun toString(): String {
        return "PreprossesertBarn(navn=$navn, aktoerId=$aktoerId)"
    }
}

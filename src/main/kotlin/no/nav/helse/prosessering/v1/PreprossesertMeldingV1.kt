package no.nav.helse.prosessering.v1

import no.nav.helse.aktoer.AktørId
import no.nav.helse.aktoer.Fodselsnummer
import no.nav.helse.aktoer.NorskIdent
import java.net.URI
import java.time.LocalDate
import java.time.ZonedDateTime

data class PreprossesertMeldingV1(
    val soknadId: String,
    val mottatt: ZonedDateTime,
    val språk: String?,
    val dokumentUrls: List<List<URI>>,
    val kroniskEllerFunksjonshemming: Boolean,
    val arbeidssituasjon: List<Arbeidssituasjon>? = null, //TODO 23.02.2021 - Fjernes når frontend er prodsatt
    val barn: PreprossesertBarn,
    val søker: PreprossesertSøker,
    val relasjonTilBarnet: String? = null,
    val sammeAdresse: Boolean = false,
    val medlemskap: Medlemskap? = null, //TODO 23.02.2021 - Fjernes når frontend er prodsatt
    val harBekreftetOpplysninger: Boolean,
    val harForståttRettigheterOgPlikter: Boolean
) {
    internal constructor(
        melding: MeldingV1,
        dokumentUrls: List<List<URI>>,
        søkerAktørId: AktørId,
        barnAktørId: AktørId?,
        barnetsNavn: String?,
        barnetsNorskeIdent: NorskIdent?
    ) : this(
        språk = melding.språk,
        soknadId = melding.søknadId,
        mottatt = melding.mottatt,
        dokumentUrls = dokumentUrls,
        kroniskEllerFunksjonshemming = melding.kroniskEllerFunksjonshemming,
        søker = PreprossesertSøker(melding.søker, søkerAktørId),
        sammeAdresse = melding.sammeAdresse,
        arbeidssituasjon = melding.arbeidssituasjon,
        barn = PreprossesertBarn(melding.barn, melding.barn.fødselsdato, barnetsNavn, barnetsNorskeIdent, barnAktørId),
        relasjonTilBarnet = melding.relasjonTilBarnet,
        medlemskap = melding.medlemskap,
        harForståttRettigheterOgPlikter = melding.harForståttRettigheterOgPlikter,
        harBekreftetOpplysninger = melding.harBekreftetOpplysninger
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
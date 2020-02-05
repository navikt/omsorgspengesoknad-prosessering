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
    val kroniskEllerFunksjonshemming: Boolean,
    val erYrkesaktiv: Boolean = false,
    val barn: PreprossesertBarn,
    val søker: PreprossesertSøker,
    val relasjonTilBarnet: String,
    val delerOmsorg: Boolean = false,
    val sammeAddresse: Boolean = false,
    val medlemskap: Medlemskap,
    val utenlandsopphold: List<Utenlandsopphold> = listOf(),
    val harBekreftetOpplysninger: Boolean,
    val harForstattRettigheterOgPlikter: Boolean,
    var legeerklæring: List<URI> = listOf(),
    var samværsavtale: List<URI> = listOf()
) {
    internal constructor(
        melding: MeldingV1,
        søkerAktørId: AktørId,
        barnAktørId: AktørId?,
        barnetsNavn: String?,
        barnetsNorskeIdent: NorskIdent?
    ) : this(
        språk = melding.språk,
        soknadId = melding.søknadId,
        mottatt = melding.mottatt,
        kroniskEllerFunksjonshemming = melding.kroniskEllerFunksjonshemming,
        søker = PreprossesertSøker(melding.søker, søkerAktørId),
        barn = PreprossesertBarn(melding.barn, melding.barn.fødselsdato, barnetsNavn, barnetsNorskeIdent, barnAktørId),
        relasjonTilBarnet = melding.relasjonTilBarnet,
        medlemskap = melding.medlemskap,
        harForstattRettigheterOgPlikter = melding.harForstattRettigheterOgPlikter,
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
    val fødselsnummer: String?,
    val fødselsDato: LocalDate?,
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
        fødselsnummer = barn.norskIdentifikator ?: (barnetsNorskeIdent as? Fodselsnummer)?.getValue(),
        fødselsDato = barnetsFødselsdato,
        navn = barnetsNavn,
        aktoerId = aktørId?.id
    )

    override fun toString(): String {
        return "PreprossesertBarn(navn=$navn, aktoerId=$aktoerId)"
    }
}
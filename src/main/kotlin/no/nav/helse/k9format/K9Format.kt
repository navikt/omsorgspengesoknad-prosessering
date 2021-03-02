package no.nav.helse.k9format

import no.nav.helse.prosessering.v1.*
import no.nav.k9.søknad.felles.Versjon
import no.nav.k9.søknad.felles.type.NorskIdentitetsnummer
import no.nav.k9.søknad.felles.type.SøknadId
import no.nav.k9.søknad.ytelse.omsorgspenger.utvidetrett.v1.OmsorgspengerKroniskSyktBarn
import no.nav.k9.søknad.Søknad as K9Søknad
import no.nav.k9.søknad.felles.personopplysninger.Barn as K9Barn
import no.nav.k9.søknad.felles.personopplysninger.Søker as K9Søker

private val k9FormatVersjon = Versjon.of("1.0.0")

fun MeldingV1.tilK9Format(): K9Søknad {
    return K9Søknad(
        SøknadId.of(søknadId),
        k9FormatVersjon,
        mottatt,
        søker.tilK9Søker(),
        OmsorgspengerKroniskSyktBarn(
            barn.tilK9Barn(),
            kroniskEllerFunksjonshemming
        )
    )
}


fun PreprossesertMeldingV1.tilK9Format(): K9Søknad {
    return K9Søknad(
        SøknadId.of(soknadId),
        k9FormatVersjon,
        mottatt,
        søker.tilK9Søker(),
        OmsorgspengerKroniskSyktBarn(
            barn.tilK9Barn(),
            kroniskEllerFunksjonshemming
        )
    )
}


fun Søker.tilK9Søker(): K9Søker = K9Søker(NorskIdentitetsnummer.of(fødselsnummer))
fun Barn.tilK9Barn(): K9Barn = K9Barn(NorskIdentitetsnummer.of(this.norskIdentifikator), (fødselsdato))

fun PreprossesertSøker.tilK9Søker(): K9Søker = K9Søker(NorskIdentitetsnummer.of(fødselsnummer))
fun PreprossesertBarn.tilK9Barn(): K9Barn = K9Barn(NorskIdentitetsnummer.of(this.norskIdentifikator), (fødselsDato))

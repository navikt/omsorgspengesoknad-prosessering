package no.nav.helse.prosessering.v1.overforeDager

import no.nav.helse.aktoer.AktørId
import no.nav.helse.prosessering.v1.*
import java.net.URI
import java.time.ZonedDateTime

data class PreprossesertOverforeDagerV1(
    val soknadId: String,
    val mottatt: ZonedDateTime,
    val søker: PreprossesertSøker,
    val språk: String?,
    val antallDager: Int,
    val fnrMottaker: String,
    val medlemskap: Medlemskap,
    val harForståttRettigheterOgPlikter: Boolean,
    val harBekreftetOpplysninger: Boolean,
    val arbeidssituasjon: List<Arbeidssituasjon>,
    val dokumentUrls: List<List<URI>>,
    val fosterbarn: List<Fosterbarn>? = listOf()
) {
    internal constructor(
        melding: SøknadOverføreDagerV1,
        søkerAktørId: AktørId,
        dokumentUrls: List<List<URI>>
    ) : this(
        språk = melding.språk,
        soknadId = melding.søknadId,
        søker = PreprossesertSøker(melding.søker, søkerAktørId),
        mottatt = melding.mottatt,
        arbeidssituasjon = melding.arbeidssituasjon,
        medlemskap = melding.medlemskap,
        harForståttRettigheterOgPlikter = melding.harForståttRettigheterOgPlikter,
        harBekreftetOpplysninger = melding.harBekreftetOpplysninger,
        antallDager = melding.antallDager,
        fnrMottaker = melding.fnrMottaker,
        dokumentUrls = dokumentUrls,
        fosterbarn = melding.fosterbarn
    )
}


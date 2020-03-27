package no.nav.helse.prosessering.v1.ettersending


import no.nav.helse.aktoer.AktørId
import no.nav.helse.prosessering.v1.Medlemskap
import no.nav.helse.prosessering.v1.PreprossesertBarn
import no.nav.helse.prosessering.v1.PreprossesertSøker
import java.net.URI
import java.time.LocalDate
import java.time.ZonedDateTime

data class PreprossesertMeldingV1Ettersending(
    val sprak: String?,
    val soknadId: String,
    val dokumentUrls: List<List<URI>>,
    val mottatt: ZonedDateTime,
    val søker: PreprossesertSøker,
    val harForstattRettigheterOgPlikter: Boolean,
    val harBekreftetOpplysninger: Boolean,
    val beskrivelse: String,
    val søknadstype: Søknadstype,
    val medlemskap: Medlemskap
    ) {
    internal constructor(
        melding: SøknadEttersendingV1,
        dokumentUrls: List<List<URI>>,
        sokerAktoerId: AktørId
    ) : this(
        sprak = melding.språk,
        soknadId = melding.søknadId,
        dokumentUrls = dokumentUrls,
        mottatt = melding.mottatt,
        søker = PreprossesertSøker(melding.søker, sokerAktoerId),
        beskrivelse = melding.beskrivelse,
        søknadstype = melding.søknadstype,
        medlemskap = melding.medlemskap,
        harForstattRettigheterOgPlikter = melding.harForståttRettigheterOgPlikter,
        harBekreftetOpplysninger = melding.harBekreftetOpplysninger
    )
}


package no.nav.helse.prosessering.v1

import no.nav.helse.aktoer.AktørId
import java.net.URI
import java.time.ZonedDateTime

data class PreprossesertMeldingV1OverforeDager(
    val soknadId: String,
    val mottatt: ZonedDateTime,
    val søker: PreprossesertSøker,
    val språk: String,
    val antallDager: Int,
    val mottakerAvDagerNorskIdentifikator: String,
    val medlemskap: Medlemskap,
    val harForståttRettigheterOgPlikter: Boolean,
    val harBekreftetOpplysninger: Boolean,
    val arbeidssituasjon: List<Arbeidssituasjon>,
    val dokumentUrls: List<List<URI>>
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
        mottakerAvDagerNorskIdentifikator = melding.mottakerAvDagerNorskIdentifikator,
        dokumentUrls = dokumentUrls
    )
}


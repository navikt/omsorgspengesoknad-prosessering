package no.nav.helse.prosessering.v1

import io.prometheus.client.Counter
import io.prometheus.client.Histogram
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

private val ZONE_ID = ZoneId.of("Europe/Oslo")

private val jaNeiCounter = Counter.build()
    .name("ja_nei_counter")
    .help("Teller for svar på ja/nei spørsmål i søknaden")
    .labelNames("spm", "svar")
    .register()

private val antallArbeidsSituasjonerCounter = Counter.build()
    .name("antall_arbeidssituasjoner_counter")
    .help("Teller for søkers antall arbeidssituasjoner")
    .labelNames("antall_forhold")
    .register()

private val arbeidsSituasjonCounter = Counter.build()
    .name("arbeidssituasjon_counter")
    .help("Teller for søkers arbeidsforhold")
    .labelNames("forhold")
    .register()

private val relasjonPåSammeAdresse = Counter.build()
    .name("relasjon_paa_samme_adresse")
    .help("Teller for søkere med relasjon på samme adresse som barnet.")
    .labelNames("relasjon", "sammeAdresse")
    .register()

private val medlemskapMedUtenlandsopphold = Counter.build()
    .name("medlemskap_med_utenlandsopphold")
    .help("Teller for søkere med utenlandsopphold.")
    .labelNames("har_bodd_i_utlandet_siste_12_mnd", "utenlandsopphold")
    .register()

internal fun PreprossesertMeldingV1OverforeDager.reportMetrics() {

    jaNeiCounter.labels("har_bodd_i_utlandet_siste_12_mnd", medlemskap.harBoddIUtlandetSiste12Mnd.tilJaEllerNei()).inc()
    jaNeiCounter.labels("skal_bo_i_utlandet_neste_12_mnd", medlemskap.skalBoIUtlandetNeste12Mnd.tilJaEllerNei()).inc()

    medlemskapMedUtenlandsopphold.labels(
        medlemskap.harBoddIUtlandetSiste12Mnd.tilJaEllerNei(),
        medlemskap.utenlandsoppholdSiste12Mnd.size.toString()
    ).inc()

    medlemskapMedUtenlandsopphold.labels(
        medlemskap.skalBoIUtlandetNeste12Mnd.tilJaEllerNei(),
        medlemskap.utenlandsoppholdNeste12Mnd.size.toString()
    ).inc()

    if (arbeidssituasjon.isNotEmpty()) {
        antallArbeidsSituasjonerCounter.labels(arbeidssituasjon.size.toString()).inc()
        val arbeidsSituasjonerSomString = arbeidssituasjon.sortedDescending().joinToString(" & ")
        arbeidsSituasjonCounter.labels(arbeidsSituasjonerSomString).inc()
    }
}

private fun Boolean.tilJaEllerNei(): String = if (this) "Ja" else "Nei"
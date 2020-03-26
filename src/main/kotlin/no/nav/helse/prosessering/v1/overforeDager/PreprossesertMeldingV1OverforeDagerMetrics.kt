package no.nav.helse.prosessering.v1.overforeDager

import io.prometheus.client.Counter
import io.prometheus.client.Histogram

private val antallDagerHistogram = Histogram.build()
    .buckets(1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.0,9.0,10.0,11.0,12.0,13.0,14.0,15.0)
    .name("antall_dager_histogram")
    .help("Antall dager som det blir søkt om")
    .register()

private val jaNeiCounter = Counter.build()
    .name("ja_nei_counter_overfore_dager")
    .help("Teller for svar på ja/nei spørsmål i søknaden")
    .labelNames("spm", "svar")
    .register()

private val antallArbeidsSituasjonerCounter = Counter.build()
    .name("antall_arbeidssituasjoner_counter_overfore_dager")
    .help("Teller for søkers antall arbeidssituasjoner")
    .labelNames("antall_forhold")
    .register()


private val arbeidsSituasjonCounter = Counter.build()
    .name("arbeidssituasjon_counter_overfore_dager")
    .help("Teller for søkers arbeidsforhold")
    .labelNames("forhold")
    .register()

private val medlemskapMedUtenlandsopphold = Counter.build()
    .name("medlemskap_med_utenlandsopphold_overfore_dager")
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

    antallDagerHistogram.observe(antallDager.toDouble())
}

private fun Boolean.tilJaEllerNei(): String = if (this) "Ja" else "Nei"

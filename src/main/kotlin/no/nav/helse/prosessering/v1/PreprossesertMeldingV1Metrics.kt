package no.nav.helse.prosessering.v1

import io.prometheus.client.Counter
import io.prometheus.client.Histogram
import no.nav.helse.utils.aarSiden
import no.nav.helse.utils.erUnderEttAar
import no.nav.helse.utils.tilJaEllerNei
import no.nav.helse.utils.ukerSiden


private val barnetsAlderHistogram = Histogram.build()
    .buckets(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0)
    .name("barnets_alder_histogram")
    .help("Alderen på barnet det søkes for")
    .register()

private val jaNeiCounter = Counter.build()
    .name("ja_nei_counter")
    .help("Teller for svar på ja/nei spørsmål i søknaden")
    .labelNames("spm", "svar")
    .register()

private val barnetsAlderIUkerCounter = Counter.build()
    .name("barnets_alder_i_uker")
    .help("Teller for barn under 1 år, hvor mange uker de er.")
    .labelNames("uker")
    .register()

private val sammeAdreseCounter = Counter.build()
    .name("samme_adresse_counter")
    .help("Teller for antall søkere som ikke har samme folkeregisterert adresse som barnet.")
    .labelNames("spm", "svar")
    .register()

private val søkersRelasjonTilBarnetCounter = Counter.build()
    .name("sokers_relasjon_til_barnet_counter")
    .help("Teller for søkers relasjon til barnet.")
    .labelNames("relasjon")
    .register()

private val relasjonPåSammeAdresse = Counter.build()
    .name("relasjon_paa_samme_adresse")
    .help("Teller for søkere med relasjon på samme adresse som barnet.")
    .labelNames("relasjon", "sammeAdresse")
    .register()

internal fun PreprosessertMeldingV1.reportMetrics() {
    val barnetsFodselsdato = barn.fødselsdato
    if (barnetsFodselsdato != null) {
        val barnetsAlder = barnetsFodselsdato.aarSiden()
        barnetsAlderHistogram.observe(barnetsAlder)
        if (barnetsAlder.erUnderEttAar()) {
            barnetsAlderIUkerCounter.labels(barnetsFodselsdato.ukerSiden()).inc()
        }
    }

    if (relasjonTilBarnet != null) {
        søkersRelasjonTilBarnetCounter.labels(relasjonTilBarnet.utskriftsvennlig).inc()
        relasjonPåSammeAdresse.labels(relasjonTilBarnet.utskriftsvennlig, sammeAdresse.tilJaEllerNei()).inc()
    }

    sammeAdreseCounter.labels("sammeAdresse", sammeAdresse.tilJaEllerNei()).inc()
}

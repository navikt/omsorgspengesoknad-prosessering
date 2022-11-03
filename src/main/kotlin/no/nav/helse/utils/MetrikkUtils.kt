package no.nav.helse.utils

import no.nav.helse.prosessering.v1.Barn
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

fun Double.erUnderEttAar() = 0.0 == this

fun Barn.fodseldato(): LocalDate? {
    return try {
        val dag = norskIdentifikator.substring(0, 2).toInt()
        val maned = norskIdentifikator.substring(2, 4).toInt()
        val ar = "20${norskIdentifikator.substring(4, 6)}".toInt()
        LocalDate.of(ar, maned, dag)
    } catch (cause: Throwable) {
        null
    }
}

fun LocalDate.aarSiden(): Double {
    val alder = ChronoUnit.YEARS.between(this, LocalDate.now(ZONE_ID))
    if (alder in -18..-1) return 19.0
    return alder.absoluteValue.toDouble()
}

fun LocalDate.ukerSiden() =
    ChronoUnit.WEEKS.between(this, LocalDate.now(ZONE_ID)).absoluteValue.toString()

fun Boolean.tilJaEllerNei(): String = if (this) "Ja" else "Nei"

val ZONE_ID = ZoneId.of("Europe/Oslo")

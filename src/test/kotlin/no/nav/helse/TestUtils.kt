package no.nav.helse

import no.nav.helse.prosessering.v1.DAGER_SYNLIG_K9BESKJED
import no.nav.helse.prosessering.v1.MeldingV1
import no.nav.helse.prosessering.v1.TEKST_K9BESKJED
import no.nav.helse.prosessering.v1.YTELSE_K9BESKJED
import org.json.JSONObject
import kotlin.test.assertEquals

internal fun String.assertGyldigK9Beskjed(melding: MeldingV1) {
    val k9Beskjed = JSONObject(this)

    assertEquals(melding.søknadId, k9Beskjed.getString("grupperingsId"))
    assertEquals(TEKST_K9BESKJED, k9Beskjed.getString("tekst"))
    assertEquals(YTELSE_K9BESKJED, k9Beskjed.getString("ytelse"))
    assertEquals(DAGER_SYNLIG_K9BESKJED, k9Beskjed.getLong("dagerSynlig"))
}
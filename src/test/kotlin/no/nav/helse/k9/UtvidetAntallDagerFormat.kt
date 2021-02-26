package no.nav.helse.k9

import no.nav.k9.søknad.JsonUtils
import no.nav.k9.søknad.Søknad
import org.json.JSONObject
import org.skyscreamer.jsonassert.JSONAssert
import kotlin.test.assertNotNull

internal fun String.assertUtvidetAntallDagerFormat() {
    val rawJson = JSONObject(this)

    val metadata = assertNotNull(rawJson.getJSONObject("metadata"))
    assertNotNull(metadata.getString("correlationId"))

    val data = assertNotNull(rawJson.getJSONObject("data"))
    assertNotNull(data.getJSONObject("journalførtMelding").getString("journalpostId"))

    val søknad = assertNotNull(data.getJSONObject("melding")).getJSONObject("k9FormatSøknad")

    val rekonstruertSøknad = JsonUtils.fromString(søknad.toString(), Søknad::class.java)

    val rekonstruertSøknadSomString = JsonUtils.toString(rekonstruertSøknad)
    JSONAssert.assertEquals(søknad.toString(), rekonstruertSøknadSomString, true)
}

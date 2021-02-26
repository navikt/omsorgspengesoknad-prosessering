package no.nav.helse

import no.nav.helse.SøknadUtils.Companion.melding
import no.nav.helse.dokument.Søknadsformat
import no.nav.helse.k9format.tilK9Format
import org.skyscreamer.jsonassert.JSONAssert
import java.time.ZonedDateTime
import java.util.*
import kotlin.test.Test

class SøknadsformatTest {

    @Test
    fun `Soknaden journalfoeres som JSON uten vedlegg`() {
        val søknadId = UUID.randomUUID().toString()
        val mottatt = ZonedDateTime.parse("2018-01-02T03:04:05.006Z")
        val melding = melding.copy(søknadId = søknadId, mottatt = mottatt)
        val json = Søknadsformat.somJson(melding.tilK9Format())
        println(String(json))
        JSONAssert.assertEquals(
            //language=json
            """{
                  "søknadId": "$søknadId",
                  "mottattDato": "2018-01-02T03:04:05.006Z",
                  "språk": "nb",
                  "versjon" : "1.0.0",
                  "søker": {
                    "norskIdentitetsnummer": "26104500284"
                  },
                  "ytelse" : {
                      "type" : "OMP_UTV_KS",
                      "kroniskEllerFunksjonshemming": false,
                      "barn": {
                          "norskIdentitetsnummer": "02119970078",
                          "fødselsdato": "2020-01-01"
                      }
                  }
              }
        """.trimIndent(), String(json), true
        )
    }
}

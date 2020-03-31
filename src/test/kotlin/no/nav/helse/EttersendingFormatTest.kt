package no.nav.helse

import no.nav.helse.dokument.Søknadsformat
import no.nav.helse.prosessering.v1.Søker
import no.nav.helse.prosessering.v1.ettersending.EttersendingV1
import org.junit.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.net.URI
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

class EttersendingFormatTest{

    @Test
    fun `Ettersending journalføres som JSON`(){
        val søknadId = UUID.randomUUID().toString()
        val json = Søknadsformat.somJsonEttersending(melding(søknadId))
        println(String(json))
        JSONAssert.assertEquals(
            """
                {
                  "søker": {
                    "fødselsnummer": "1212",
                    "fornavn": "Ola",
                    "mellomnavn": "Mellomnavn",
                    "etternavn": "Nordmann",
                    "fødselsdato": null,
                    "aktørId": "123456"
                  },
                  "søknadId": "$søknadId",
                  "mottatt": "2018-01-02T03:04:05.000000006Z",
                  "språk": "nb",
                  "vedleggUrls": [
                    "http://localhost.com/vedlegg1"
                  ],
                  "harForståttRettigheterOgPlikter": true,
                  "harBekreftetOpplysninger": true,
                  "beskrivelse": "Blablabla beskrivelse",
                  "søknadstype": "Omsorgspenger",
                  "titler": [
                    "vedlegg1"
                  ]
                }
            """.trimIndent(), String(json), true
        )
    }

    private fun melding(soknadId: String): EttersendingV1 = EttersendingV1(
        søknadId = soknadId,
        mottatt = ZonedDateTime.of(2018, 1, 2, 3, 4, 5, 6, ZoneId.of("UTC")),
        søker = Søker(
            aktørId = "123456",
            fødselsnummer = "1212",
            etternavn = "Nordmann",
            mellomnavn = "Mellomnavn",
            fornavn = "Ola",
            fødselsdato = null
        ),
        harBekreftetOpplysninger = true,
        harForståttRettigheterOgPlikter = true,
        beskrivelse = "Blablabla beskrivelse",
        søknadstype = "Omsorgspenger",
        vedleggUrls = listOf(URI("http://localhost.com/vedlegg1")),
        titler = listOf("vedlegg1")
        )
}
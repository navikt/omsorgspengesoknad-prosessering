package no.nav.helse

import no.nav.helse.dokument.JournalforingsFormat
import no.nav.helse.prosessering.v1.Barn
import no.nav.helse.prosessering.v1.Medlemskap
import no.nav.helse.prosessering.v1.MeldingV1
import no.nav.helse.prosessering.v1.Søker
import org.skyscreamer.jsonassert.JSONAssert
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import kotlin.test.Test

class JournalforingsFormatTest {

    @Test
    fun `Soknaden journalfoeres som JSON uten vedlegg`() {
        val søknadId = UUID.randomUUID().toString()
        val json = JournalforingsFormat.somJson(melding(søknadId))
        println(String(json))
        JSONAssert.assertEquals(
            """{
                  "nyVersjon": false,
                  "søknadId": "$søknadId",
                  "mottatt": "2018-01-02T03:04:05.000000006Z",
                  "språk": "nb",
                  "kroniskEllerFunksjonshemming": false,
                  "erYrkesaktiv": false,
                  "barn": {
                    "navn": "Kari",
                    "norskIdentifikator": "2323",
                    "fødselsdato": null,
                    "aktørId": null
                  },
                  "søker": {
                    "fødselsnummer": "1212",
                    "fornavn": "Ola",
                    "mellomnavn": "Mellomnavn",
                    "etternavn": "Nordmann",
                    "fødselsdato": null,
                    "aktørId": "123456"
                  },
                  "relasjonTilBarnet": "Mor",
                  "delerOmsorg": false,
                  "sammeAddresse": false,
                  "medlemskap": {
                    "harBoddIUtlandetSiste12Mnd": true,
                    "utenlandsoppholdSiste12Mnd": [],
                    "skalBoIUtlandetNeste12Mnd": true,
                    "utenlandsoppholdNeste12Mnd": []
                  },
                  "utenlandsopphold": [],
                  "harBekreftetOpplysninger": true,
                  "legeerklæring": [],
                  "samværsavtale": [],
                  "harForstattRettigheterOgPlikter": true
                }

        """.trimIndent(), String(json), true
        )

    }

    private fun melding(soknadId: String): MeldingV1 = MeldingV1(
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
        barn = Barn(
            navn = "Kari",
            norskIdentifikator = "2323",
            fødselsdato = null,
            aktørId = null
        ),
        relasjonTilBarnet = "Mor",
        medlemskap = Medlemskap(
            harBoddIUtlandetSiste12Mnd = true,
            skalBoIUtlandetNeste12Mnd = true,
            utenlandsoppholdSiste12Mnd = listOf(),
            utenlandsoppholdNeste12Mnd = listOf()
        ),
        harBekreftetOpplysninger = true,
        harForstattRettigheterOgPlikter = true
    )
}

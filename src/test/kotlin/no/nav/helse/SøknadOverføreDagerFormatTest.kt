package no.nav.helse

import no.nav.helse.dokument.Søknadsformat
import no.nav.helse.prosessering.v1.*
import org.skyscreamer.jsonassert.JSONAssert
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import kotlin.test.Test

class SøknadOverføreDagerFormatTest {

    @Test
    fun `Soknaden journalfoeres som JSON`() {
        val søknadId = UUID.randomUUID().toString()
        val json = Søknadsformat.somJsonOverforeDager(melding(søknadId))
        println(String(json))
        JSONAssert.assertEquals(
            """{
                  "søknadId": "$søknadId",
                  "mottatt": "2018-01-02T03:04:05.000000006Z",
                  "språk": "nb",
                  "arbeidssituasjon": ["arbeidstaker", "frilanser", "selvstendigNæringsdrivende"],
                  "søker": {
                    "fødselsnummer": "1212",
                    "fornavn": "Ola",
                    "mellomnavn": "Mellomnavn",
                    "etternavn": "Nordmann",
                    "fødselsdato": null,
                    "aktørId": "123456"
                  },
                  "antallDager": 10,
                  "fnrMottaker": "123456789",
                  "medlemskap": {
                    "harBoddIUtlandetSiste12Mnd": true,
                    "utenlandsoppholdSiste12Mnd": [],
                    "skalBoIUtlandetNeste12Mnd": true,
                    "utenlandsoppholdNeste12Mnd": []
                  },
                  "harBekreftetOpplysninger": true,
                  "harForståttRettigheterOgPlikter": true,
                  "fosterbarn" : [
                    {
                    "fødselsnummer": "123456789"
                    }
                  ]
                }

        """.trimIndent(), String(json), true
        )

    }

    private fun melding(soknadId: String): SøknadOverføreDagerV1 = SøknadOverføreDagerV1(
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
        arbeidssituasjon = listOf(Arbeidssituasjon.ARBEIDSTAKER, Arbeidssituasjon.FRILANSER, Arbeidssituasjon.SELVSTENDIGNÆRINGSDRIVENDE),
        medlemskap = Medlemskap(
            harBoddIUtlandetSiste12Mnd = true,
            skalBoIUtlandetNeste12Mnd = true,
            utenlandsoppholdSiste12Mnd = listOf(),
            utenlandsoppholdNeste12Mnd = listOf()
        ),
        harBekreftetOpplysninger = true,
        harForståttRettigheterOgPlikter = true,
        antallDager = 10,
        fnrMottaker = "123456789",
        fosterbarn = listOf(
            Fosterbarn("123456789")
        )
    )
}

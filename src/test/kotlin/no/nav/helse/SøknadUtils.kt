package no.nav.helse

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.prosessering.v1.Barn
import no.nav.helse.prosessering.v1.MeldingV1
import no.nav.helse.prosessering.v1.Søker
import no.nav.helse.prosessering.v1.SøkerBarnRelasjon
import no.nav.k9.søknad.Søknad
import no.nav.k9.søknad.felles.Versjon
import no.nav.k9.søknad.felles.type.NorskIdentitetsnummer
import no.nav.k9.søknad.felles.type.SøknadId
import no.nav.k9.søknad.ytelse.omsorgspenger.utvidetrett.v1.OmsorgspengerKroniskSyktBarn
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.*

class SøknadUtils {
    companion object {

        val søknadId = UUID.randomUUID().toString()
        val mottatt = ZonedDateTime.now()
        val k9FormatVersjon = Versjon.of("1.0.0")

        val søker = Søker(
            aktørId = "12345",
            fødselsdato = LocalDate.parse("2000-01-01"),
            fornavn = "Kjell",
            mellomnavn = null,
            etternavn = "Kjeller",
            fødselsnummer = "26104500284"
        )

        val barn = Barn(
            norskIdentifikator = "02119970078",
            navn = "Ole Dole Doffen",
            aktørId = "123456",
            fødselsdato = LocalDate.parse("2020-01-01")
        )
        val k9Format = Søknad(
            SøknadId.of(søknadId),
            k9FormatVersjon,
            mottatt,
            no.nav.k9.søknad.felles.personopplysninger.Søker(
                NorskIdentitetsnummer.of("26104500284")
            ),
            OmsorgspengerKroniskSyktBarn(
                no.nav.k9.søknad.felles.personopplysninger.Barn().medNorskIdentitetsnummer(NorskIdentitetsnummer.of("02119970078")),
                true
            )
        )
        val melding = MeldingV1(
            nyVersjon = false,
            søknadId = søknadId,
            mottatt = mottatt,
            språk = "nb",
            søker = søker,
            kroniskEllerFunksjonshemming = false,
            barn = barn,
            sammeAdresse = true,
            relasjonTilBarnet = SøkerBarnRelasjon.FAR,
            samværsavtaleVedleggId = listOf("1234"),
            legeerklæringVedleggId = listOf("5678"),
            harForståttRettigheterOgPlikter = true,
            harBekreftetOpplysninger = true,
            k9FormatSøknad = k9Format
        )
    }
}

val objectMapper: ObjectMapper = jacksonObjectMapper().dusseldorfConfigured()
    .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
    .configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
package no.nav.helse.prosessering.v1.asynkron

import no.nav.helse.CorrelationId
import no.nav.helse.aktoer.AktørId
import no.nav.helse.joark.JoarkGateway
import no.nav.helse.kafka.KafkaConfig
import no.nav.helse.kafka.ManagedKafkaStreams
import no.nav.helse.kafka.ManagedStreamHealthy
import no.nav.helse.kafka.ManagedStreamReady
import no.nav.helse.prosessering.v1.PreprossesertBarn
import no.nav.helse.prosessering.v1.PreprossesertMeldingV1
import no.nav.helse.prosessering.v1.PreprossesertSøker
import no.nav.k9.søknad.felles.Barn
import no.nav.k9.søknad.felles.NorskIdentitetsnummer
import no.nav.k9.søknad.felles.Søker
import no.nav.k9.søknad.felles.SøknadId
import no.nav.k9.søknad.omsorgspenger.OmsorgspengerSøknad
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Produced
import org.slf4j.LoggerFactory
import java.net.URI

internal class JournalforingsStream(
    joarkGateway: JoarkGateway,
    kafkaConfig: KafkaConfig
) {

    private val stream = ManagedKafkaStreams(
        name = NAME,
        properties = kafkaConfig.stream(NAME),
        topology = topology(joarkGateway),
        unreadyAfterStreamStoppedIn = kafkaConfig.unreadyAfterStreamStoppedIn
    )

    internal val ready = ManagedStreamReady(stream)
    internal val healthy = ManagedStreamHealthy(stream)

    private companion object {
        private const val NAME = "JournalforingV1"
        private val logger = LoggerFactory.getLogger("no.nav.$NAME.topology")

        private fun topology(joarkGateway: JoarkGateway): Topology {
            val builder = StreamsBuilder()
            val fraPreprossesert: Topic<TopicEntry<PreprossesertMeldingV1>> = Topics.PREPROSSESERT
            val tilCleanup: Topic<TopicEntry<Cleanup>> = Topics.CLEANUP

            val mapValues = builder
                .stream<String, TopicEntry<PreprossesertMeldingV1>>(
                    fraPreprossesert.name,
                    Consumed.with(fraPreprossesert.keySerde, fraPreprossesert.valueSerde)
                )
                .filter { _, entry -> 1 == entry.metadata.version }
                .mapValues { soknadId, entry ->
                    process(NAME, soknadId, entry) {

                        val list = mutableListOf<URI>()
                        if (!entry.data.samværsavtale.isNullOrEmpty()) {
                            list.addAll(entry.data.samværsavtale)
                        }
                        list.addAll(entry.data.legeerklæring)

                        val dokumenter = listOf(list)
                        logger.info("Journalfører dokumenter: {}", dokumenter)
                        val journaPostId = joarkGateway.journalfør(
                            mottatt = entry.data.mottatt,
                            aktørId = AktørId(entry.data.søker.aktørId),
                            norskIdent = entry.data.søker.fødselsnummer,
                            correlationId = CorrelationId(entry.metadata.correlationId),
                            dokumenter = dokumenter
                        )
                        logger.info("Dokumenter journalført med ID = ${journaPostId.journalpostId}.")
                        val journalfort = Journalfort(
                            journalpostId = journaPostId.journalpostId,
                            søknad = entry.data.tilK9Omsorgspengesøknad()
                        )
                        Cleanup(
                            metadata = entry.metadata,
                            melding = entry.data,
                            journalførtMelding = journalfort
                        )
                    }
                }
            mapValues
                .to(tilCleanup.name, Produced.with(tilCleanup.keySerde, tilCleanup.valueSerde))
            return builder.build()
        }
    }

    internal fun stop() = stream.stop(becauseOfError = false)
}

private fun PreprossesertMeldingV1.tilK9Omsorgspengesøknad(): OmsorgspengerSøknad = OmsorgspengerSøknad.builder()
    .søknadId(SøknadId.of(soknadId))
    .mottattDato(mottatt)
    .barn(barn.tilK9Barn())
    .søker(søker.tilK9Søker())
    .build()

private fun PreprossesertSøker.tilK9Søker(): Søker = Søker.builder()
    .norskIdentitetsnummer(NorskIdentitetsnummer.of(fødselsnummer))
    .build()

private fun PreprossesertBarn.tilK9Barn(): Barn {
    return when {
        !norskIdentifikator.isNullOrBlank() -> Barn.builder().norskIdentitetsnummer(
            NorskIdentitetsnummer.of(
                norskIdentifikator
            )
        ).build()
        fødselsDato != null -> Barn.builder().fødselsdato(fødselsDato).build()
        else -> throw IllegalArgumentException("Ikke tillatt med barn som mangler både fødselsdato og fødselnummer.")
    }
}

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
import no.nav.helse.prosessering.v1.PreprossesertMeldingV1OverforeDager
import no.nav.helse.prosessering.v1.PreprossesertSøker
import no.nav.k9.søknad.felles.Barn
import no.nav.k9.søknad.felles.NorskIdentitetsnummer
import no.nav.k9.søknad.felles.Søker
import no.nav.k9.søknad.felles.SøknadId
import no.nav.k9.søknad.omsorgspenger.OmsorgspengerSøknad
import no.nav.k9.søknad.omsorgspenger.overføring.Mottaker
import no.nav.k9.søknad.omsorgspenger.overføring.OmsorgspengerOverføringSøknad
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Produced
import org.slf4j.LoggerFactory
import java.net.URI

internal class JournalforingsStreamOverforeDager(
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
        private const val NAME = "JournalforingV1OverforeDager"
        private val logger = LoggerFactory.getLogger("no.nav.$NAME.topology")

        private fun topology(joarkGateway: JoarkGateway): Topology {
            val builder = StreamsBuilder()
            val fraPreprossesert: Topic<TopicEntry<PreprossesertMeldingV1OverforeDager>> = Topics.PREPROSSESERT_OVERFOREDAGER
            val tilCleanup: Topic<TopicEntry<CleanupOverforeDager>> = Topics.CLEANUP_OVERFOREDAGER

            val mapValues = builder
                .stream<String, TopicEntry<PreprossesertMeldingV1OverforeDager>>(
                    fraPreprossesert.name,
                    Consumed.with(fraPreprossesert.keySerde, fraPreprossesert.valueSerde)
                )
                .filter { _, entry -> 1 == entry.metadata.version }
                .mapValues { soknadId, entry ->
                    process(NAME, soknadId, entry) {

                        val dokumenter = entry.data.dokumentUrls
                        logger.info("Journalfører overføre dager dokumenter: {}", dokumenter)
                        val journaPostId = joarkGateway.journalførOverforeDager(
                            mottatt = entry.data.mottatt,
                            aktørId = AktørId(entry.data.søker.aktørId),
                            norskIdent = entry.data.søker.fødselsnummer,
                            correlationId = CorrelationId(entry.metadata.correlationId),
                            dokumenter = dokumenter
                        )
                        logger.info("Dokumenter journalført med ID = ${journaPostId.journalpostId}.")

                        val journalfort = JournalfortOverforeDager(
                            journalpostId = journaPostId.journalpostId,
                            søknad = entry.data.tilK9OmsorgspengerOverføringSøknad()
                        )

                        CleanupOverforeDager(
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

private fun PreprossesertMeldingV1OverforeDager.tilK9OmsorgspengerOverføringSøknad() = OmsorgspengerOverføringSøknad.builder()
    .søknadId(SøknadId.of(soknadId))
    .mottattDato(mottatt)
    .søker(søker.tilK9Søker())
    .barn(tilK9Barn())
    .mottaker(fnrMottaker.tilK9Mottaker())
    .build()

// TODO: Når det er en liste med fosterbarn i søknaden må det mappes inn i K9-Format-søknaden her.
private fun PreprossesertMeldingV1OverforeDager.tilK9Barn() = listOf<Barn>()

private fun PreprossesertSøker.tilK9Søker() = Søker.builder()
    .norskIdentitetsnummer(NorskIdentitetsnummer.of(fødselsnummer))
    .build()

private fun String.tilK9Mottaker() = Mottaker.builder()
    .norskIdentitetsnummer(NorskIdentitetsnummer.of(this))
    .build()
package no.nav.helse.prosessering.v1.asynkron.overforeDager

import no.nav.helse.CorrelationId
import no.nav.helse.aktoer.AktørId
import no.nav.helse.dokument.DokumentService
import no.nav.helse.kafka.KafkaConfig
import no.nav.helse.kafka.ManagedKafkaStreams
import no.nav.helse.kafka.ManagedStreamHealthy
import no.nav.helse.kafka.ManagedStreamReady
import no.nav.helse.prosessering.v1.asynkron.*
import no.nav.helse.prosessering.v1.asynkron.Topic
import no.nav.helse.prosessering.v1.asynkron.Topics
import no.nav.helse.prosessering.v1.asynkron.process
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Produced
import org.slf4j.LoggerFactory

internal class CleanupStreamOverforeDager(
    kafkaConfig: KafkaConfig,
    dokumentService: DokumentService
) {
    private val stream = ManagedKafkaStreams(
        name = NAME,
        properties = kafkaConfig.stream(NAME),
        topology = topology(dokumentService),
        unreadyAfterStreamStoppedIn = kafkaConfig.unreadyAfterStreamStoppedIn
    )

    internal val ready = ManagedStreamReady(stream)
    internal val healthy = ManagedStreamHealthy(stream)

    private companion object {
        private const val NAME = "CleanupV1OverforeDager"
        private val logger = LoggerFactory.getLogger("no.nav.$NAME.topology")

        private fun topology(dokumentService: DokumentService): Topology {
            val builder = StreamsBuilder()
            val fraCleanup: Topic<TopicEntry<CleanupOverforeDager>> = Topics.CLEANUP_OVERFOREDAGER
            val tilJournalfort: Topic<TopicEntry<JournalfortOverforeDager>> = Topics.JOURNALFORT_OVERFOREDAGER

            builder
                .stream<String, TopicEntry<CleanupOverforeDager>>(
                    fraCleanup.name, Consumed.with(fraCleanup.keySerde, fraCleanup.valueSerde)
                )
                .filter { _, entry -> 1 == entry.metadata.version }
                .mapValues { soknadId, entry ->
                    process(NAME, soknadId, entry) {
                        logger.info("Sletter overfore dager dokumenter.")
                        dokumentService.slettDokumeter(
                            urlBolks = entry.data.meldingV1.dokumentUrls,
                            aktørId = AktørId(entry.data.meldingV1.søker.aktørId),
                            correlationId = CorrelationId(entry.metadata.correlationId)
                        )
                        logger.info("Dokumenter slettet.")
                        logger.info("Videresender journalført overføre dager melding")
                        entry.data.journalførtMelding
                    }
                }
                .to(tilJournalfort.name, Produced.with(tilJournalfort.keySerde, tilJournalfort.valueSerde))
            return builder.build()
        }
    }

    internal fun stop() = stream.stop(becauseOfError = false)
}
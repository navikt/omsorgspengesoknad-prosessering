package no.nav.helse.prosessering.v1.asynkron

import no.nav.helse.CorrelationId
import no.nav.helse.aktoer.AktørId
import no.nav.helse.dokument.DokumentService
import no.nav.helse.kafka.KafkaConfig
import no.nav.helse.kafka.ManagedKafkaStreams
import no.nav.helse.kafka.ManagedStreamHealthy
import no.nav.helse.kafka.ManagedStreamReady
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.slf4j.LoggerFactory
import java.net.URI

internal class CleanupStream(
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
        private const val NAME = "CleanupV1"
        private val logger = LoggerFactory.getLogger("no.nav.$NAME.topology")

        private fun topology(dokumentService: DokumentService): Topology {
            val builder = StreamsBuilder()
            val fromTopic = Topics.JOURNALFORT

            builder
                .stream<String, TopicEntry<Journalfort>>(
                    fromTopic.name,
                    Consumed.with(fromTopic.keySerde, fromTopic.valueSerde)
                )
                .filter { _, entry -> 1 == entry.metadata.version }
                .foreach { soknadId, entry ->
                    try {
                        process(NAME, soknadId, entry) {
                            logger.info("Sletter dokumenter.")
                            val list = mutableListOf<URI>()
                            list.addAll(entry.data.melding.samværsavtale)
                            list.addAll(entry.data.melding.legeerklæring)

                            dokumentService.slettDokumeter(
                                urlBolks = listOf(list),
                                aktørId = AktørId(entry.data.melding.søker.aktørId),
                                correlationId = CorrelationId(entry.metadata.correlationId)
                            )
                            logger.info("Dokumenter slettet.")
                        }
                    } catch (ignore: Throwable) {
                    }
                }
            return builder.build()
        }
    }

    internal fun stop() = stream.stop(becauseOfError = false)
}
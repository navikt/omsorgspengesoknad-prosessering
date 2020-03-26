package no.nav.helse.prosessering.v1.asynkron.ettersending

import no.nav.helse.kafka.KafkaConfig
import no.nav.helse.kafka.ManagedKafkaStreams
import no.nav.helse.kafka.ManagedStreamHealthy
import no.nav.helse.kafka.ManagedStreamReady
import no.nav.helse.prosessering.v1.MeldingV1
import no.nav.helse.prosessering.v1.PreprosseseringV1Service
import no.nav.helse.prosessering.v1.asynkron.TopicEntry
import no.nav.helse.prosessering.v1.asynkron.Topics
import no.nav.helse.prosessering.v1.asynkron.process
import no.nav.helse.prosessering.v1.ettersending.SøknadEttersendingV1
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Produced
import org.slf4j.LoggerFactory

internal class PreprosseseringStreamEttersending(
    preprosseseringV1Service: PreprosseseringV1Service,
    kafkaConfig: KafkaConfig
) {
    private val stream = ManagedKafkaStreams(
        name = NAME,
        properties = kafkaConfig.stream(NAME),
        topology = topology(preprosseseringV1Service),
        unreadyAfterStreamStoppedIn = kafkaConfig.unreadyAfterStreamStoppedIn
    )

    internal val ready = ManagedStreamReady(stream)
    internal val healthy = ManagedStreamHealthy(stream)

    private companion object {

        private const val NAME = "PreprosesseringV1Ettersending"
        private val logger = LoggerFactory.getLogger("no.nav.$NAME.topology")

        private fun topology(preprosseseringV1Service: PreprosseseringV1Service): Topology {
            val builder = StreamsBuilder()
            val fromMottatt = Topics.MOTTATT_ETTERSENDING
            val tilPreprossesert = Topics.PREPROSSESERT_ETTERSENDING

            builder
                .stream<String, TopicEntry<SøknadEttersendingV1>>(
                    fromMottatt.name,
                    Consumed.with(fromMottatt.keySerde, fromMottatt.valueSerde)
                )
                .filter { _, entry -> 1 == entry.metadata.version }
                .mapValues { soknadId, entry ->
                    process(NAME, soknadId, entry) {
                        logger.info("Preprosesserer søknad for ettersending.")
                        val preprossesertMelding = preprosseseringV1Service.preprosseserEttersending(
                            melding = entry.data,
                            metadata = entry.metadata
                        )
                        logger.info("Preprossesering av søknad for ettersending ferdig.")
                        preprossesertMelding
                    }
                }
                .to(tilPreprossesert.name, Produced.with(tilPreprossesert.keySerde, tilPreprossesert.valueSerde))
            return builder.build()
        }
    }

    internal fun stop() = stream.stop(becauseOfError = false)
}
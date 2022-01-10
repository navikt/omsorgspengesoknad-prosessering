package no.nav.helse.prosessering.v1.asynkron

import no.nav.helse.dokument.DokumentEier
import no.nav.helse.dokument.K9MellomlagringService
import no.nav.helse.erEtter
import no.nav.helse.felles.CorrelationId
import no.nav.helse.formaterStatuslogging
import no.nav.helse.kafka.KafkaConfig
import no.nav.helse.kafka.ManagedKafkaStreams
import no.nav.helse.kafka.ManagedStreamHealthy
import no.nav.helse.kafka.ManagedStreamReady
import no.nav.helse.prosessering.v1.tilK9Beskjed
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime

internal class CleanupStream(
    kafkaConfig: KafkaConfig,
    dokumentService: K9MellomlagringService,
    søknadMottattEtter: ZonedDateTime
) {
    private val stream = ManagedKafkaStreams(
        name = NAME,
        properties = kafkaConfig.stream(NAME),
        topology = topology(dokumentService, søknadMottattEtter),
        unreadyAfterStreamStoppedIn = kafkaConfig.unreadyAfterStreamStoppedIn
    )

    internal val ready = ManagedStreamReady(stream)
    internal val healthy = ManagedStreamHealthy(stream)

    private companion object {
        private const val NAME = "CleanupV1"
        private val logger = LoggerFactory.getLogger("no.nav.$NAME.topology")

        private fun topology(dokumentService: K9MellomlagringService, gittDato: ZonedDateTime): Topology {
            val builder = StreamsBuilder()
            val fraCleanup = Topics.CLEANUP
            val tilK9DittnavVarsel = Topics.K9_DITTNAV_VARSEL

            builder
                .stream(fraCleanup.name, fraCleanup.consumed)
                .filter { _, entry -> entry.deserialiserTilCleanup().melding.mottatt.erEtter(gittDato) }
                .filter { _, entry -> 1 == entry.metadata.version }
                .mapValues { soknadId, entry ->
                    process(NAME, soknadId, entry) {
                        val cleanup = entry.deserialiserTilCleanup()
                        logger.info(formaterStatuslogging(cleanup.melding.soknadId, "kjører cleanup"))

                        logger.info("Sletter dokumenter.")
                        dokumentService.slettDokumeter(
                            vedleggIdBolks = cleanup.melding.vedleggId,
                            dokumentEier = DokumentEier(cleanup.melding.søker.fødselsnummer),
                            correlationId = CorrelationId(entry.metadata.correlationId)
                        )

                        val k9Beskjed = cleanup.tilK9Beskjed()
                        logger.info("Sender K9Beskjed viderer til ${tilK9DittnavVarsel.name}")
                        k9Beskjed.serialiserTilData()
                    }
                }
                .to(tilK9DittnavVarsel.name, tilK9DittnavVarsel.produced)
            return builder.build()
        }
    }

    internal fun stop() = stream.stop(becauseOfError = false)
}
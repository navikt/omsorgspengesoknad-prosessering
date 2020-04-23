package no.nav.helse.prosessering.v1.asynkron

import no.nav.helse.dokument.DokumentService
import no.nav.helse.joark.JoarkGateway
import no.nav.helse.kafka.KafkaConfig
import no.nav.helse.prosessering.v1.PreprosseseringV1Service
import no.nav.helse.prosessering.v1.asynkron.ettersending.CleanupStreamEttersending
import no.nav.helse.prosessering.v1.asynkron.ettersending.JournalføringStreamEttersending
import no.nav.helse.prosessering.v1.asynkron.ettersending.PreprosseseringStreamEttersending
import org.slf4j.LoggerFactory

internal class AsynkronProsesseringV1Service(
    kafkaConfig: KafkaConfig,
    preprosseseringV1Service: PreprosseseringV1Service,
    joarkGateway: JoarkGateway,
    dokumentService: DokumentService
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(AsynkronProsesseringV1Service::class.java)
    }

    private val preprosseseringStream = PreprosseseringStream(
        kafkaConfig = kafkaConfig,
        preprosseseringV1Service = preprosseseringV1Service
    )

    private val preprosseseringStreamEttersending = PreprosseseringStreamEttersending(
        kafkaConfig = kafkaConfig,
        preprosseseringV1Service = preprosseseringV1Service
    )

    private val journalforingsStream = JournalforingsStream(
        kafkaConfig = kafkaConfig,
        joarkGateway = joarkGateway
    )

    private val journalforingsStreamEttersending = JournalføringStreamEttersending(
        kafkaConfig = kafkaConfig,
        joarkGateway = joarkGateway
    )

    private val cleanupStream = CleanupStream(
        kafkaConfig = kafkaConfig,
        dokumentService = dokumentService
    )

    private val cleanupStreamEttersending = CleanupStreamEttersending(
        kafkaConfig = kafkaConfig,
        dokumentService = dokumentService
    )

    private val healthChecks = setOf(
        preprosseseringStream.healthy,
        preprosseseringStreamEttersending.healthy,
        journalforingsStream.healthy,
        journalforingsStreamEttersending.healthy,
        cleanupStream.healthy,
        cleanupStreamEttersending.healthy
    )

    private val isReadyChecks = setOf(
        preprosseseringStream.ready,
        preprosseseringStreamEttersending.ready,
        journalforingsStream.ready,
        journalforingsStreamEttersending.ready,
        cleanupStream.ready,
        cleanupStreamEttersending.ready
    )

    internal fun stop() {
        logger.info("Stopper streams.")
        preprosseseringStream.stop()
        preprosseseringStreamEttersending.stop()
        journalforingsStream.stop()
        journalforingsStreamEttersending.stop()
        cleanupStream.stop()
        cleanupStreamEttersending.stop()
        logger.info("Alle streams stoppet.")
    }

    internal fun healthChecks() = healthChecks
    internal fun isReadyChecks() = isReadyChecks
}
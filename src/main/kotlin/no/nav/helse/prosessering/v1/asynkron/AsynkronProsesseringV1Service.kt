package no.nav.helse.prosessering.v1.asynkron

import no.nav.helse.dokument.DokumentService
import no.nav.helse.joark.JoarkGateway
import no.nav.helse.kafka.KafkaConfig
import no.nav.helse.prosessering.v1.PreprosseseringV1Service
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

    private val preprosseseringStreamOverforeDager = PreprosseseringStreamOverforeDager(
        kafkaConfig = kafkaConfig,
        preprosseseringV1Service = preprosseseringV1Service
    )

    private val journalforingsStream = JournalforingsStream(
        kafkaConfig = kafkaConfig,
        joarkGateway = joarkGateway
    )

    private val journalforingsStreamOverforeDager = JournalforingsStreamOverforeDager(
        kafkaConfig = kafkaConfig,
        joarkGateway = joarkGateway
    )

    private val cleanupStream = CleanupStream(
        kafkaConfig = kafkaConfig,
        dokumentService = dokumentService
    )

    private val cleanupStreamOverforeDager = CleanupStreamOverforeDager(
        kafkaConfig = kafkaConfig,
        dokumentService = dokumentService
    )

    private val healthChecks = setOf(
        preprosseseringStream.healthy,
        preprosseseringStreamOverforeDager.healthy,
        journalforingsStream.healthy,
        journalforingsStreamOverforeDager.healthy,
        cleanupStream.healthy,
        cleanupStreamOverforeDager.healthy
    )

    private val isReadyChecks = setOf(
        preprosseseringStream.ready,
        preprosseseringStreamOverforeDager.ready,
        journalforingsStream.ready,
        journalforingsStreamOverforeDager.ready,
        cleanupStream.ready,
        cleanupStreamOverforeDager.ready
    )

    internal fun stop() {
        logger.info("Stopper streams.")
        preprosseseringStream.stop()
        preprosseseringStreamOverforeDager.stop()
        journalforingsStream.stop()
        journalforingsStreamOverforeDager.stop()
        cleanupStream.stop()
        cleanupStreamOverforeDager.stop()
        logger.info("Alle streams stoppet.")
    }

    internal fun healthChecks() = healthChecks
    internal fun isReadyChecks() = isReadyChecks
}
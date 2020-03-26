package no.nav.helse.prosessering.v1.asynkron

import no.nav.helse.dokument.DokumentService
import no.nav.helse.joark.JoarkGateway
import no.nav.helse.kafka.KafkaConfig
import no.nav.helse.prosessering.v1.PreprosseseringV1Service
import no.nav.helse.prosessering.v1.asynkron.ettersending.CleanupStreamEttersending
import no.nav.helse.prosessering.v1.asynkron.ettersending.JournalføringStreamEttersending
import no.nav.helse.prosessering.v1.asynkron.ettersending.PreprosseseringStreamEttersending
import no.nav.helse.prosessering.v1.asynkron.overforeDager.CleanupStreamOverforeDager
import no.nav.helse.prosessering.v1.asynkron.overforeDager.JournalforingsStreamOverforeDager
import no.nav.helse.prosessering.v1.asynkron.overforeDager.PreprosseseringStreamOverforeDager
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

    private val preprosseseringStreamEttersending = PreprosseseringStreamEttersending(
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

    private val journalforingsStreamEttersending = JournalføringStreamEttersending(
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

    private val cleanupStreamEttersending = CleanupStreamEttersending(
        kafkaConfig = kafkaConfig,
        dokumentService = dokumentService
    )

    private val healthChecks = setOf(
        preprosseseringStream.healthy,
        preprosseseringStreamOverforeDager.healthy,
        preprosseseringStreamEttersending.healthy,
        journalforingsStream.healthy,
        journalforingsStreamOverforeDager.healthy,
        journalforingsStreamEttersending.healthy,
        cleanupStream.healthy,
        cleanupStreamOverforeDager.healthy,
        cleanupStreamEttersending.healthy
    )

    private val isReadyChecks = setOf(
        preprosseseringStream.ready,
        preprosseseringStreamOverforeDager.ready,
        preprosseseringStreamEttersending.ready,
        journalforingsStream.ready,
        journalforingsStreamOverforeDager.ready,
        journalforingsStreamEttersending.ready,
        cleanupStream.ready,
        cleanupStreamOverforeDager.ready,
        cleanupStreamEttersending.ready
    )

    internal fun stop() {
        logger.info("Stopper streams.")
        preprosseseringStream.stop()
        preprosseseringStreamOverforeDager.stop()
        preprosseseringStreamEttersending.stop()
        journalforingsStream.stop()
        journalforingsStreamOverforeDager.stop()
        journalforingsStreamEttersending.stop()
        cleanupStream.stop()
        cleanupStreamOverforeDager.stop()
        cleanupStreamEttersending.stop()
        logger.info("Alle streams stoppet.")
    }

    internal fun healthChecks() = healthChecks
    internal fun isReadyChecks() = isReadyChecks
}
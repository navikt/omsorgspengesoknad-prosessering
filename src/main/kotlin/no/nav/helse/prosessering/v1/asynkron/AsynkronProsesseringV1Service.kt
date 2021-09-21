package no.nav.helse.prosessering.v1.asynkron

import no.nav.helse.dokument.K9MellomlagringService
import no.nav.helse.joark.JoarkGateway
import no.nav.helse.kafka.KafkaConfig
import no.nav.helse.prosessering.v1.PreprosesseringV1Service

import org.slf4j.LoggerFactory
import java.time.ZonedDateTime

internal class AsynkronProsesseringV1Service(
    kafkaConfig: KafkaConfig,
    preprosesseringV1Service: PreprosesseringV1Service,
    joarkGateway: JoarkGateway,
    dokumentService: K9MellomlagringService,
    datoMottattEtter: ZonedDateTime,
    datoMottattEtterCleanup: ZonedDateTime
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(AsynkronProsesseringV1Service::class.java)
    }

    private val preprosesseringStream = PreprosesseringStream(
        kafkaConfig = kafkaConfig,
        preprosesseringV1Service = preprosesseringV1Service,
        søknadMottattEtter = datoMottattEtter
    )

    private val journalforingsStream = JournalforingsStream(
        kafkaConfig = kafkaConfig,
        joarkGateway = joarkGateway,
        søknadMottattEtter = datoMottattEtter
    )


    private val cleanupStream = CleanupStream(
        kafkaConfig = kafkaConfig,
        dokumentService = dokumentService,
        søknadMottattEtter = datoMottattEtterCleanup
    )

    private val healthChecks = setOf(
        preprosesseringStream.healthy,
        journalforingsStream.healthy,
        cleanupStream.healthy
    )

    private val isReadyChecks = setOf(
        preprosesseringStream.ready,
        journalforingsStream.ready,
        cleanupStream.ready
    )

    internal fun stop() {
        logger.info("Stopper streams.")
        preprosesseringStream.stop()
        journalforingsStream.stop()
        cleanupStream.stop()
        logger.info("Alle streams stoppet.")
    }

    internal fun healthChecks() = healthChecks
    internal fun isReadyChecks() = isReadyChecks
}

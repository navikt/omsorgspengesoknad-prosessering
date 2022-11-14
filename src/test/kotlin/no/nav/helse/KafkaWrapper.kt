package no.nav.helse

import no.nav.helse.prosessering.Metadata
import no.nav.helse.prosessering.v1.MeldingV1
import no.nav.helse.prosessering.v1.asynkron.Data
import no.nav.helse.prosessering.v1.asynkron.TopicEntry
import no.nav.helse.prosessering.v1.asynkron.Topics.CLEANUP
import no.nav.helse.prosessering.v1.asynkron.Topics.K9_DITTNAV_VARSEL
import no.nav.helse.prosessering.v1.asynkron.Topics.MOTTATT_V2
import no.nav.helse.prosessering.v1.asynkron.Topics.PREPROSESSERT
import no.nav.helse.prosessering.v1.asynkron.omsorgspengesoknadKonfigurertMapper
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.*
import kotlin.test.assertEquals

private lateinit var kafkaContainer: KafkaContainer
private const val confluentVersion = "7.2.1"

object KafkaWrapper {
    fun bootstrap(): KafkaContainer {
        kafkaContainer = KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:$confluentVersion")
        )
        kafkaContainer.start()
        kafkaContainer.createTopicsForTest()
        return kafkaContainer
    }
}

private fun KafkaContainer.createTopicsForTest() {
    // Dette er en workaround for att testcontainers (pr. versjon 1.17.5) ikke håndterer autocreate topics
    AdminClient.create(testProducerProperties("admin")).createTopics(
        listOf(
            NewTopic(CLEANUP.name, 1, 1),
            NewTopic(K9_DITTNAV_VARSEL.name, 1, 1),
            NewTopic(MOTTATT_V2.name, 1, 1),
            NewTopic(PREPROSESSERT.name, 1, 1),
        )
    )
}

private fun KafkaContainer.testConsumerProperties(groupId: String): MutableMap<String, Any>? {
    return HashMap<String, Any>().apply {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
    }
}

private fun KafkaContainer.testProducerProperties(clientId: String): MutableMap<String, Any>? {
    return HashMap<String, Any>().apply {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        put(ProducerConfig.CLIENT_ID_CONFIG, clientId)
    }
}

fun KafkaContainer.cleanupKonsumer(): KafkaConsumer<String, String> {
    val consumer = KafkaConsumer(
        testConsumerProperties("OmsorgspengesøknadCleanupKonsumer"),
        StringDeserializer(),
        StringDeserializer()
    )
    consumer.subscribe(listOf(CLEANUP.name))
    return consumer
}

fun KafkaContainer.preprosessertKonsumer(): KafkaConsumer<String, TopicEntry> {
    val consumer = KafkaConsumer(
        testConsumerProperties("OmsorgspengesøknadPreprosessertKonsumer"),
        StringDeserializer(),
        PREPROSESSERT.serDes
    )
    consumer.subscribe(listOf(PREPROSESSERT.name))
    return consumer
}

fun KafkaContainer.k9DittnavVarselKonsumer(): KafkaConsumer<String, String> {
    val consumer = KafkaConsumer(
        testConsumerProperties("K9DittnavVarselKonsumer"),
        StringDeserializer(),
        StringDeserializer()
    )
    consumer.subscribe(listOf(K9_DITTNAV_VARSEL.name))
    return consumer
}

fun KafkaContainer.meldingsProducer() = KafkaProducer(
    testProducerProperties("OmsorgspengesoknadProsesseringTestProducer"),
    MOTTATT_V2.keySerializer,
    MOTTATT_V2.serDes
)

fun KafkaConsumer<String, TopicEntry>.hentPreprosessertMelding(
    soknadId: String,
    maxWaitInSeconds: Long = 60
): TopicEntry {
    val end = System.currentTimeMillis() + Duration.ofSeconds(maxWaitInSeconds).toMillis()
    while (System.currentTimeMillis() < end) {
        seekToBeginning(assignment())
        val entries = poll(Duration.ofSeconds(1))
            .records(PREPROSESSERT.name)
            .filter { it.key() == soknadId }

        if (entries.isNotEmpty()) {
            assertEquals(1, entries.size)
            return entries.first().value()
        }
    }
    throw IllegalStateException("Fant ikke opprettet oppgave for søknad $soknadId etter $maxWaitInSeconds sekunder.")
}

fun KafkaConsumer<String, String>.hentK9Beskjed(
    soknadId: String,
    maxWaitInSeconds: Long = 60
): String {
    val end = System.currentTimeMillis() + Duration.ofSeconds(maxWaitInSeconds).toMillis()
    while (System.currentTimeMillis() < end) {
        seekToBeginning(assignment())
        val entries = poll(Duration.ofSeconds(5))
            .records(K9_DITTNAV_VARSEL.name)
            .filter { it.key() == soknadId }

        if (entries.isNotEmpty()) {
            assertEquals(1, entries.size)
            return entries.first().value()
        }
    }
    throw IllegalStateException("Fant ikke opprettet K9Beskjed for søknad $soknadId etter $maxWaitInSeconds sekunder.")
}

fun KafkaConsumer<String, String>.hentCleanupMelding(
    soknadId: String,
    maxWaitInSeconds: Long = 60
): String {
    val end = System.currentTimeMillis() + Duration.ofSeconds(maxWaitInSeconds).toMillis()
    while (System.currentTimeMillis() < end) {
        seekToBeginning(assignment())
        val entries = poll(Duration.ofSeconds(1))
            .records(CLEANUP.name)
            .filter { it.key() == soknadId }

        if (entries.isNotEmpty()) {
            assertEquals(1, entries.size)
            return entries.first().value()
        }
    }
    throw IllegalStateException("Fant ikke opprettet oppgave for søknad $soknadId etter $maxWaitInSeconds sekunder.")
}

fun KafkaProducer<String, TopicEntry>.leggTilMottak(soknad: MeldingV1) {
    send(
        ProducerRecord(
            MOTTATT_V2.name,
            soknad.søknadId,
            TopicEntry(
                metadata = Metadata(
                    version = 1,
                    correlationId = UUID.randomUUID().toString()
                ),
                data = Data(omsorgspengesoknadKonfigurertMapper().writeValueAsString(soknad))
            )
        )
    ).get()
}
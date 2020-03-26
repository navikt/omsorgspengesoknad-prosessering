package no.nav.helse.prosessering.v1.asynkron

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.prosessering.Metadata
import no.nav.helse.prosessering.v1.MeldingV1
import no.nav.helse.prosessering.v1.PreprossesertMeldingV1
import no.nav.helse.prosessering.v1.overforeDager.PreprossesertMeldingV1OverforeDager
import no.nav.helse.prosessering.v1.overforeDager.SøknadOverføreDagerV1
import no.nav.k9.søknad.omsorgspenger.OmsorgspengerSøknad
import no.nav.k9.søknad.omsorgspenger.overføring.OmsorgspengerOverføringSøknad
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer

data class TopicEntry<V>(val metadata: Metadata, val data: V)
data class Cleanup(val metadata: Metadata, val melding: PreprossesertMeldingV1, val journalførtMelding: Journalfort)
data class CleanupOverforeDager(val metadata: Metadata, val melding: PreprossesertMeldingV1OverforeDager, val journalførtMelding: JournalfortOverforeDager)
data class Journalfort(val journalpostId: String, val søknad: OmsorgspengerSøknad)
data class JournalfortOverforeDager(val journalpostId: String, val søknad: OmsorgspengerOverføringSøknad)

internal data class Topic<V>(
    val name: String,
    val serDes : SerDes<V>
) {
    val keySerializer = StringSerializer()
    val keySerde = Serdes.String()
    val valueSerde = Serdes.serdeFrom(serDes, serDes)
}

internal object Topics {
    val MOTTATT = Topic(
        name = "privat-omsorgspengesoknad-mottatt",
        serDes = MottattSoknadSerDes()
    )
    val PREPROSSESERT = Topic(
        name = "privat-omsorgspengesoknad-preprossesert",
        serDes = PreprossesertSerDes()
    )
    val CLEANUP = Topic(
        name = "privat-omsorgspengesoknad-cleanup",
        serDes = CleanupSerDes()
    )
    val JOURNALFORT = Topic(
        name = "privat-omsorgspengesoknad-journalfort",
        serDes = JournalfortSerDes()
    )
    val MOTTATT_OVERFOREDAGER = Topic(
        name = "privat-overfore-omsorgsdager-soknad-mottatt",
        serDes = MottattSoknadSerDesOverforeDager()
    )
    val PREPROSSESERT_OVERFOREDAGER = Topic(
        name = "privat-overfore-omsorgsdager-soknad-preprossesert",
        serDes = PreprossesertSerDesOverforeDager()
    )
    val CLEANUP_OVERFOREDAGER = Topic(
        name = "privat-overfore-omsorgsdager-soknad-cleanup",
        serDes = CleanupSerDesOverforeDager()
    )
    val JOURNALFORT_OVERFOREDAGER = Topic(
        name = "privat-overfore-omsorgsdager-soknad-journalfort",
        serDes = JournalfortSerDesOverforeDager()
    )
}

internal abstract class SerDes<V> : Serializer<V>, Deserializer<V> {
    protected val objectMapper = jacksonObjectMapper()
        .dusseldorfConfigured()
        .setPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CAMEL_CASE)
        .configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
    override fun serialize(topic: String?, data: V): ByteArray? {
        return data?.let {
            objectMapper.writeValueAsBytes(it)
        }
    }
    override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) {}
    override fun close() {}
}
private class MottattSoknadSerDes: SerDes<TopicEntry<MeldingV1>>() {
    override fun deserialize(topic: String?, data: ByteArray?): TopicEntry<MeldingV1>? {
        return data?.let {
            objectMapper.readValue<TopicEntry<MeldingV1>>(it)
        }
    }
}
private class PreprossesertSerDes: SerDes<TopicEntry<PreprossesertMeldingV1>>() {
    override fun deserialize(topic: String?, data: ByteArray?): TopicEntry<PreprossesertMeldingV1>? {
        return data?.let {
            objectMapper.readValue(it)
        }
    }
}
private class CleanupSerDes: SerDes<TopicEntry<Cleanup>>() {
    override fun deserialize(topic: String?, data: ByteArray?): TopicEntry<Cleanup>? {
        return data?.let {
            objectMapper.readValue(it)
        }
    }
}
private class JournalfortSerDes: SerDes<TopicEntry<Journalfort>>() {
    override fun deserialize(topic: String?, data: ByteArray?): TopicEntry<Journalfort>? {
        return data?.let {
            objectMapper.readValue(it)
        }
    }
}

private class MottattSoknadSerDesOverforeDager: SerDes<TopicEntry<SøknadOverføreDagerV1>>() {
    override fun deserialize(topic: String?, data: ByteArray?): TopicEntry<SøknadOverføreDagerV1>? {
        return data?.let {
            objectMapper.readValue<TopicEntry<SøknadOverføreDagerV1>>(it)
        }
    }
}

private class PreprossesertSerDesOverforeDager: SerDes<TopicEntry<PreprossesertMeldingV1OverforeDager>>() {
    override fun deserialize(topic: String?, data: ByteArray?): TopicEntry<PreprossesertMeldingV1OverforeDager>? {
        return data?.let {
            objectMapper.readValue(it)
        }
    }
}

private class CleanupSerDesOverforeDager: SerDes<TopicEntry<CleanupOverforeDager>>() {
    override fun deserialize(topic: String?, data: ByteArray?): TopicEntry<CleanupOverforeDager>? {
        return data?.let {
            objectMapper.readValue(it)
        }
    }
}

private class JournalfortSerDesOverforeDager: SerDes<TopicEntry<JournalfortOverforeDager>>() {
    override fun deserialize(topic: String?, data: ByteArray?): TopicEntry<JournalfortOverforeDager>? {
        return data?.let {
            objectMapper.readValue(it)
        }
    }
}
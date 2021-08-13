package no.nav.helse.prosessering.v1.asynkron

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.prosessering.Metadata
import no.nav.helse.prosessering.v1.MeldingV1
import no.nav.helse.prosessering.v1.PreprossesertMeldingV1
import no.nav.k9.søknad.Søknad
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Produced
import org.json.JSONObject

data class Data(val rawJson: String)

data class Cleanup(val metadata: Metadata, val melding: PreprossesertMeldingV1, val journalførtMelding: Journalfort)

data class Journalfort(val journalpostId: String, val søknad: Søknad)

internal object Topics {
    val MOTTATT = Topic(
        name = "dusseldorf.privat-omsorgspengesoknad-mottatt",
        serDes = SerDes()
    )
    val PREPROSSESERT = Topic(
        name = "dusseldorf.privat-omsorgspengesoknad-preprosessert",
        serDes = SerDes()
    )
    val CLEANUP = Topic(
        name = "dusseldorf.privat-omsorgspengesoknad-cleanup",
        serDes = SerDes()
    )

    val K9_DITTNAV_VARSEL = Topic(
        name = "dusseldorf.privat-k9-dittnav-varsel-beskjed",
        serDes = SerDes()
    )
}

class SerDes : Serializer<TopicEntry>, Deserializer<TopicEntry> {
    override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) {}
    override fun close() {}
    override fun deserialize(topic: String, entry: ByteArray): TopicEntry = TopicEntry(String(entry))
    override fun serialize(topic: String, entry: TopicEntry): ByteArray{
        return when(topic){
            Topics.K9_DITTNAV_VARSEL.name -> entry.data.rawJson.toByteArray()
            else -> entry.rawJson.toByteArray()
        }
    }
}

internal fun TopicEntry.deserialiserTilCleanup(): Cleanup  = omsorgspengesoknadKonfigurertMapper().readValue(data.rawJson)
internal fun TopicEntry.deserialiserTilMelding(): MeldingV1 = omsorgspengesoknadKonfigurertMapper().readValue(data.rawJson)
internal fun TopicEntry.deserialiserTilPreprosessertMelding(): PreprossesertMeldingV1  = omsorgspengesoknadKonfigurertMapper().readValue(data.rawJson)
internal fun Any.serialiserTilData() = Data(omsorgspengesoknadKonfigurertMapper().writeValueAsString(this))

data class TopicEntry(val rawJson: String) {
    constructor(metadata: Metadata, data: Data) : this(
        JSONObject(
            mapOf(
                "metadata" to JSONObject(
                    mapOf(
                        "version" to metadata.version,
                        "correlationId" to metadata.correlationId
                    )
                ),
                "data" to JSONObject(data.rawJson)
            )
        ).toString()
    )

    private val entityJson = JSONObject(rawJson)
    private val metadataJson = requireNotNull(entityJson.getJSONObject("metadata"))
    private val dataJson = requireNotNull(entityJson.getJSONObject("data"))
    val metadata = Metadata(
        version = requireNotNull(metadataJson.getInt("version")),
        correlationId = requireNotNull(metadataJson.getString("correlationId"))
    )
    val data = Data(dataJson.toString())
}

internal data class Topic(
    val name: String,
    val serDes: SerDes
) {
    val keySerializer = StringSerializer()
    private val keySerde = Serdes.String()
    private val valueSerde = Serdes.serdeFrom(SerDes(), SerDes())
    val consumed = Consumed.with(keySerde, valueSerde)
    val produced = Produced.with(keySerde, valueSerde)
}

fun omsorgspengesoknadKonfigurertMapper(): ObjectMapper {
    return jacksonObjectMapper().dusseldorfConfigured()
        .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
        .configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
}
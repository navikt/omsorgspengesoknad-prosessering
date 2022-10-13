package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import no.nav.helse.dusseldorf.testsupport.jws.ClientCredentials
import no.nav.helse.dusseldorf.testsupport.wiremock.getAzureV2WellKnownUrl
import org.testcontainers.containers.KafkaContainer

object TestConfiguration {

    fun asMap(
        wireMockServer: WireMockServer? = null,
        kafkaContainer: KafkaContainer? = null,
        port : Int = 8080,
        omsorgspengerJoarkBaseUrl : String? = wireMockServer?.getOmsorgspengerJoarkBaseUrl(),
        k9MellomlagringBaseUrl : String? = wireMockServer?.getK9MellomlagringBaseUrl()
    ) : Map<String, String>{
        val map = mutableMapOf(
            Pair("ktor.deployment.port","$port"),
            Pair("nav.K9_JOARK_BASE_URL","$omsorgspengerJoarkBaseUrl"),
            Pair("nav.k9_mellomlagring_base_url","$k9MellomlagringBaseUrl"),
            Pair("nav.kafka.unready_after_stream_stopped_in.amount", "15"),
            Pair("nav.kafka.unready_after_stream_stopped_in.unit", "MINUTES"),
            Pair("prosesser_soknader_mottatt_etter", "2021-01-19T10:00:00.000+01"),
            Pair("cleanup_soknader_mottatt_etter", "2021-01-19T10:00:00.000+01"),
            Pair("cleanup_soknader_mottatt_etter", "2021-01-19T10:00:00.000+01"),
            Pair("nav.prosesser_soknader_mottatt_etter", "2021-01-19T10:00:00.000+01"),
            Pair("nav.cleanup_soknader_mottatt_etter", "2021-01-19T10:00:00.000+01"),
        )

        // Clients
        if (wireMockServer != null) {
            map["nav.auth.clients.0.alias"] = "azure-v2"
            map["nav.auth.clients.0.client_id"] = "omsorgspengesoknad-prosessering"
            map["nav.auth.clients.0.private_key_jwk"] = ClientCredentials.ClientA.privateKeyJwk
            map["nav.auth.clients.0.discovery_endpoint"] = wireMockServer.getAzureV2WellKnownUrl()
            map["nav.auth.scopes.k9_mellomlagring"] = "k9-mellomlagring/.default"
            map["nav.auth.scopes.journalfore"] = "omsorgspenger-joark/.default"
        }

        kafkaContainer?.let {
            map["nav.kafka.bootstrap_servers"] = it.bootstrapServers
            map["nav.kafka.auto_offset_reset"] = "earliest"
        }

        return map.toMap()
    }
}

package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import com.typesafe.config.ConfigFactory
import io.ktor.server.config.*
import io.ktor.server.testing.*
import org.testcontainers.containers.KafkaContainer


internal fun TestApplicationBuilder.mockApp(
    kafkaContainer: KafkaContainer,
    wireMockServer: WireMockServer
) {

    val env = TestConfiguration.asMap(
        wireMockServer = wireMockServer,
        kafkaContainer = kafkaContainer,
        port = 8092
    )

    Runtime.getRuntime().addShutdownHook(object : Thread() {
        override fun run() {
            wireMockServer.stop()
            kafkaContainer.stop()
        }
    })


    environment {
        config = HoconApplicationConfig(ConfigFactory.parseMap(env))
    }

    return application { omsorgspengesoknadProsessering() }
}
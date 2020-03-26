package no.nav.helse.dokument

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.prosessering.v1.MeldingV1
import no.nav.helse.prosessering.v1.overforeDager.SøknadOverføreDagerV1

class Søknadsformat {
    companion object {
        private val objectMapper = jacksonObjectMapper()
            .dusseldorfConfigured()
            .setPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CAMEL_CASE)
            .configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)

        internal fun somJson(
            meldingV1: MeldingV1
        ): ByteArray {
            val node = objectMapper.valueToTree<ObjectNode>(meldingV1)
            node.remove("legeerklæring")
            node.remove("samværsavtale")
            return objectMapper.writeValueAsBytes(node)
        }

        internal fun somJsonOverforeDager(
            soknadOverforeDagerV1: SøknadOverføreDagerV1
        ): ByteArray {
            val node = objectMapper.valueToTree<ObjectNode>(soknadOverforeDagerV1)
            return objectMapper.writeValueAsBytes(node)
        }
    }
}
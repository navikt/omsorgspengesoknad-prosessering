package no.nav.helse.prosessering.v1.asynkron

import no.nav.helse.CorrelationId
import no.nav.helse.aktoer.AktørId
import no.nav.helse.erEtter
import no.nav.helse.joark.JoarkGateway
import no.nav.helse.joark.Navn
import no.nav.helse.kafka.KafkaConfig
import no.nav.helse.kafka.ManagedKafkaStreams
import no.nav.helse.kafka.ManagedStreamHealthy
import no.nav.helse.kafka.ManagedStreamReady
import no.nav.helse.prosessering.v1.PreprossesertBarn
import no.nav.helse.prosessering.v1.PreprossesertMeldingV1
import no.nav.helse.prosessering.v1.PreprossesertSøker
import no.nav.k9.søknad.felles.Barn
import no.nav.k9.søknad.felles.NorskIdentitetsnummer
import no.nav.k9.søknad.felles.Søker
import no.nav.k9.søknad.felles.SøknadId
import no.nav.k9.søknad.omsorgspenger.OmsorgspengerSøknad
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Produced
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime

internal class JournalforingsStream(
    joarkGateway: JoarkGateway,
    kafkaConfig: KafkaConfig,
    søknadMottattEtter: ZonedDateTime
) {

    private val stream = ManagedKafkaStreams(
        name = NAME,
        properties = kafkaConfig.stream(NAME),
        topology = topology(joarkGateway, søknadMottattEtter),
        unreadyAfterStreamStoppedIn = kafkaConfig.unreadyAfterStreamStoppedIn
    )

    internal val ready = ManagedStreamReady(stream)
    internal val healthy = ManagedStreamHealthy(stream)

    private companion object {
        private const val NAME = "JournalforingV1"
        private val logger = LoggerFactory.getLogger("no.nav.$NAME.topology")

        private val dissalowedCorrelationIds = listOf(
            "generated-d9484337-d647-4430-b4bd-173101dc68d7",
            "generated-2760518d-7e75-400f-8766-08f2a3e06747",
            "generated-ae916ab1-031c-4484-9b93-8b4d54553ec1",
            "generated-9ba3a09f-aff5-4b61-93db-e81a8a9c165f",
            "generated-47ffb18c-495f-44f1-b45e-0595f14f316d",
            "generated-0ac0a2b0-8f6e-41a4-81b2-0d32df183603",
            "generated-a0d30068-841a-4001-a2f5-38c14efac6ec",
            "generated-e7a35e83-bd31-4f6e-9f90-8496b2c39ce0",
            "generated-46b331a1-881e-4d9d-a102-74f81e191fb4",
            "generated-bf1b1140-c77f-4c10-80fd-65a33b4c9f16",
            "generated-a24e80d6-259c-433c-8456-d311557178ba"
        )

        private fun topology(joarkGateway: JoarkGateway, gittDato: ZonedDateTime): Topology {
            val builder = StreamsBuilder()
            val fraPreprossesert: Topic<TopicEntry<PreprossesertMeldingV1>> = Topics.PREPROSSESERT
            val tilCleanup: Topic<TopicEntry<Cleanup>> = Topics.CLEANUP

            val mapValues = builder
                .stream<String, TopicEntry<PreprossesertMeldingV1>>(
                    fraPreprossesert.name,
                    Consumed.with(fraPreprossesert.keySerde, fraPreprossesert.valueSerde)
                )
                .filter { _, entry -> entry.data.mottatt.erEtter(gittDato) }
                .filter { _, entry -> !dissalowedCorrelationIds.contains(entry.metadata.correlationId) }
                .filter { _, entry -> 1 == entry.metadata.version }
                .mapValues { soknadId, entry ->
                    process(NAME, soknadId, entry) {

                        val dokumenter = entry.data.dokumentUrls
                        logger.info("Journalfører dokumenter: {}", dokumenter)
                        val journaPostId = joarkGateway.journalfør(
                            mottatt = entry.data.mottatt,
                            aktørId = AktørId(entry.data.søker.aktørId),
                            norskIdent = entry.data.søker.fødselsnummer,
                            correlationId = CorrelationId(entry.metadata.correlationId),
                            dokumenter = dokumenter,
                            navn = Navn(
                                fornavn = entry.data.søker.fornavn,
                                mellomnavn = entry.data.søker.mellomnavn,
                                etternavn = entry.data.søker.etternavn
                            )
                        )
                        logger.info("Dokumenter journalført med ID = ${journaPostId.journalpostId}.")
                        val journalfort = Journalfort(
                            journalpostId = journaPostId.journalpostId,
                            søknad = entry.data.tilK9Omsorgspengesøknad()
                        )
                        Cleanup(
                            metadata = entry.metadata,
                            melding = entry.data,
                            journalførtMelding = journalfort
                        )
                    }
                }
            mapValues
                .to(tilCleanup.name, Produced.with(tilCleanup.keySerde, tilCleanup.valueSerde))
            return builder.build()
        }
    }

    internal fun stop() = stream.stop(becauseOfError = false)
}

private fun PreprossesertMeldingV1.tilK9Omsorgspengesøknad(): OmsorgspengerSøknad = OmsorgspengerSøknad.builder()
    .søknadId(SøknadId.of(soknadId))
    .mottattDato(mottatt)
    .barn(barn.tilK9Barn())
    .søker(søker.tilK9Søker())
    .build()

private fun PreprossesertSøker.tilK9Søker(): Søker = Søker.builder()
    .norskIdentitetsnummer(NorskIdentitetsnummer.of(fødselsnummer))
    .build()

private fun PreprossesertBarn.tilK9Barn(): Barn {
    return when {
        !norskIdentifikator.isNullOrBlank() -> Barn.builder().norskIdentitetsnummer(
            NorskIdentitetsnummer.of(
                norskIdentifikator
            )
        ).build()
        fødselsDato != null -> Barn.builder().fødselsdato(fødselsDato).build()
        else -> throw IllegalArgumentException("Ikke tillatt med barn som mangler både fødselsdato og fødselnummer.")
    }
}

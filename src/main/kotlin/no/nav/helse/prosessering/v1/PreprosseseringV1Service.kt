package no.nav.helse.prosessering.v1

import no.nav.helse.CorrelationId
import no.nav.helse.aktoer.AktoerService
import no.nav.helse.aktoer.AktørId
import no.nav.helse.aktoer.Fodselsnummer
import no.nav.helse.aktoer.NorskIdent
import no.nav.helse.barn.BarnOppslag
import no.nav.helse.dokument.DokumentService
import no.nav.helse.prosessering.Metadata
import no.nav.helse.prosessering.SoknadId
import no.nav.helse.tpsproxy.Ident
import no.nav.helse.tpsproxy.TpsNavn
import org.slf4j.LoggerFactory

internal class PreprosseseringV1Service(
    private val aktoerService: AktoerService,
    private val pdfV1Generator: PdfV1Generator,
    private val dokumentService: DokumentService,
    private val barnOppslag: BarnOppslag
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(PreprosseseringV1Service::class.java)
    }

    internal suspend fun preprosseser(
        melding: MeldingV1,
        metadata: Metadata
    ): PreprossesertMeldingV1 {
        val soknadId = SoknadId(melding.soknadId)
        logger.info("Preprosseserer $soknadId")

        val correlationId = CorrelationId(metadata.correlationId)

        val søkerAktørId = AktørId(melding.søker.aktørId)

        logger.info("Søkerens AktørID = $søkerAktørId")

        logger.trace("Henter AktørID for barnet.")
        val barnAktørId: AktørId? = when {
            melding.barn.aktørId.isNullOrBlank() -> hentBarnetsAktoerId(barn = melding.barn, correlationId = correlationId)
            else -> AktørId(melding.barn.aktørId)
        }
        logger.info("Barnets AktørID = $barnAktørId")

        val barnetsIdent: NorskIdent? = when {
            barnAktørId != null -> aktoerService.getIdent(barnAktørId.id, correlationId = correlationId)
            else -> null
        }

        val barnetsNavn: String? = slaaOppBarnetsNavn(melding.barn, barnetsIdent = barnetsIdent, correlationId = correlationId)

        logger.trace("Genererer Oppsummerings-PDF av søknaden.")

        val soknadOppsummeringPdf = pdfV1Generator.generateSoknadOppsummeringPdf(melding, barnetsIdent, barnetsNavn)

        logger.trace("Generering av Oppsummerings-PDF OK.")
        logger.trace("Mellomlagrer Oppsummerings-PDF.")

        val soknadOppsummeringPdfUrl = dokumentService.lagreSoknadsOppsummeringPdf(
            pdf = soknadOppsummeringPdf,
            correlationId = correlationId,
            aktørId = søkerAktørId
        )

        logger.trace("Mellomlagring av Oppsummerings-PDF OK")

        logger.trace("Mellomlagrer Oppsummerings-JSON")

        val soknadJsonUrl = dokumentService.lagreSoknadsMelding(
            melding = melding,
            aktørId = søkerAktørId,
            correlationId = correlationId
        )

        logger.trace("Mellomlagrer Oppsummerings-JSON OK.")


        val komplettDokumentUrls = mutableListOf(
            listOf(
                soknadOppsummeringPdfUrl,
                soknadJsonUrl
            )
        )

        if (melding.samværsavtale != null) {
             komplettDokumentUrls.add(listOf(melding.samværsavtale!!))
        }

        if (melding.legeerklæring != null) {
             komplettDokumentUrls.add(listOf(melding.legeerklæring!!))
        }


        logger.trace("Totalt ${komplettDokumentUrls.size} dokumentbolker.")


        val preprossesertMeldingV1 = PreprossesertMeldingV1(
            melding = melding,
            søkerAktørId = søkerAktørId,
            barnAktørId = barnAktørId,
            barnetsNavn = barnetsNavn,
            barnetsNorskeIdent = barnetsIdent
        )
        melding.reportMetrics()
        preprossesertMeldingV1.reportMetrics()
        return preprossesertMeldingV1
    }

    /**
     * Slår opp barnets navn, gitt enten alternativId, fødselsNummer eller aktørId.
     */
    private suspend fun slaaOppBarnetsNavn(
        barn: Barn,
        correlationId: CorrelationId,
        barnetsIdent: NorskIdent?
    ): String? {

        return when {
            // Dersom barnet har navn, returner navnet.
            !barn.navn.isNullOrBlank() -> barn.navn

            // Dersom barnet har et norsk ident...
            barnetsIdent != null -> {
                // Slå opp på i barneOppslag med barnets ident ...
                logger.info("Henter barnets navn gitt fødselsnummer ...")
                return try {
                    getFullNavn(ident = barnetsIdent.getValue(), correlationId = correlationId)
                } catch (e: Exception) {
                    logger.warn("Oppslag for barnets navn feilet. Prosesserer melding uten barnets navn.")
                    null
                }
            }

            // Ellers returner null
            else -> {
                logger.warn("Kunne ikke finne barnets navn!")
                null
            }
        }
    }

    private suspend fun getFullNavn(ident: String, correlationId: CorrelationId): String {
        val tpsNavn: TpsNavn = barnOppslag.navn(Ident(ident), correlationId)
        return "${tpsNavn.fornavn} ${tpsNavn.mellomnavn} ${tpsNavn.etternavn}"
    }

    private suspend fun hentBarnetsAktoerId(
        barn: Barn,
        correlationId: CorrelationId
    ): AktørId? {
        return try {
            when {
                !barn.fødselsnummer.isNullOrBlank() -> aktoerService.getAktorId(
                    ident = Fodselsnummer(barn.fødselsnummer),
                    correlationId = correlationId
                )
                else -> null
            }
        } catch (cause: Throwable) {
            logger.warn("Feil ved oppslag på Aktør ID basert på barnets fødselsnummer. Kan være at det ikke er registrert i Aktørregisteret enda. ${cause.message}")
            null
        }
    }
}
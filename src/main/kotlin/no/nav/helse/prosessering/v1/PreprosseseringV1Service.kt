package no.nav.helse.prosessering.v1

import no.nav.helse.CorrelationId
import no.nav.helse.aktoer.AktoerService
import no.nav.helse.aktoer.AktørId
import no.nav.helse.aktoer.Fodselsnummer
import no.nav.helse.aktoer.NorskIdent
import no.nav.helse.barn.BarnOppslag
import no.nav.helse.dokument.Dokument
import no.nav.helse.dokument.DokumentEier
import no.nav.helse.dokument.K9MellomlagringService
import no.nav.helse.dokument.Søknadsformat
import no.nav.helse.prosessering.Metadata
import no.nav.helse.prosessering.SoknadId
import no.nav.helse.tpsproxy.Ident
import no.nav.helse.tpsproxy.TpsNavn
import org.slf4j.LoggerFactory

internal class PreprosseseringV1Service(
    private val aktoerService: AktoerService,
    private val pdfV1Generator: PdfV1Generator,
    private val k9MellomlagringService: K9MellomlagringService,
    private val barnOppslag: BarnOppslag
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(PreprosseseringV1Service::class.java)
    }

    internal suspend fun preprosseser(
        melding: MeldingV1,
        metadata: Metadata
    ): PreprossesertMeldingV1 {
        val søknadId = SoknadId(melding.søknadId)
        logger.info("Preprosseserer $søknadId")

        val correlationId = CorrelationId(metadata.correlationId)

        val søkerAktørId = AktørId(melding.søker.aktørId)

        logger.trace("Henter AktørID for barnet.")
        val barnAktørId: AktørId? = when {
            melding.barn.aktørId.isNullOrBlank() -> hentBarnetsAktoerId(
                barn = melding.barn,
                correlationId = correlationId
            )
            else -> AktørId(melding.barn.aktørId)
        }

        val barnetsIdent: NorskIdent? = when {
            !melding.barn.norskIdentifikator.isNullOrBlank() -> Fodselsnummer(melding.barn.norskIdentifikator)
            melding.barn.norskIdentifikator.isNullOrBlank() && barnAktørId != null -> aktoerService.getIdent(
                barnAktørId.id,
                correlationId = correlationId
            )
            else -> null
        }

        val barnetsNavn: String? =
            slaaOppBarnetsNavn(melding.barn, barnetsIdent = barnetsIdent, correlationId = correlationId)

        logger.info("Genererer Oppsummerings-PDF av søknaden.")
        val soknadOppsummeringPdf = pdfV1Generator.generateSoknadOppsummeringPdf(melding, barnetsIdent, barnetsNavn)

        val dokumentEier = DokumentEier(melding.søker.fødselsnummer)

        logger.info("Mellomlagrer Oppsummerings-PDF.")
        val oppsummeringPdfUrl = k9MellomlagringService.lagreDokument(
            dokument = Dokument(
                eier = dokumentEier,
                content = soknadOppsummeringPdf,
                contentType = "application/pdf",
                title = "Søknad om ekstra omsorgsdager"
            ),
            correlationId = correlationId
        )

        logger.info("Mellomlagrer Oppsummerings-JSON")
        val søknadJsonUrl = k9MellomlagringService.lagreDokument(
            dokument = Dokument(
                eier = dokumentEier,
                content = Søknadsformat.somJson(melding.k9FormatSøknad),
                contentType = "application/json",
                title = "Søknad om omsorgspenger som JSON"
            ),
            correlationId = correlationId
        )

        val komplettDokumentUrls = mutableListOf(
            listOf(
                oppsummeringPdfUrl,
                søknadJsonUrl
            )
        )

        if (!melding.samværsavtale.isNullOrEmpty()) {
            melding.samværsavtale.forEach { komplettDokumentUrls.add(listOf(it)) }
        }
        melding.legeerklæring.forEach { komplettDokumentUrls.add(listOf(it)) }

        logger.info("Totalt ${komplettDokumentUrls.size} dokumentbolker med totalt ${komplettDokumentUrls.flatten().size} dokumenter.")

        val preprossesertMeldingV1 = PreprossesertMeldingV1(
            melding = melding,
            dokumentUrls = komplettDokumentUrls.toList(),
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
                !barn.norskIdentifikator.isNullOrBlank() -> aktoerService.getAktorId(
                    ident = Fodselsnummer(barn.norskIdentifikator),
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

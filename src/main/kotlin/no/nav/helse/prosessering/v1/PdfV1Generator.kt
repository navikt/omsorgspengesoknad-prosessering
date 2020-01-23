package no.nav.helse.prosessering.v1

import com.github.jknack.handlebars.Context
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Helper
import com.github.jknack.handlebars.context.MapValueResolver
import com.github.jknack.handlebars.io.ClassPathTemplateLoader
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import no.nav.helse.aktoer.NorskIdent
import no.nav.helse.dusseldorf.ktor.core.fromResources
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

internal class PdfV1Generator  {
    private companion object {
        private const val ROOT = "handlebars"
        private const val SOKNAD = "soknad"

        private val REGULAR_FONT = "$ROOT/fonts/SourceSansPro-Regular.ttf".fromResources().readBytes()
        private val BOLD_FONT = "$ROOT/fonts/SourceSansPro-Bold.ttf".fromResources().readBytes()
        private val ITALIC_FONT = "$ROOT/fonts/SourceSansPro-Italic.ttf".fromResources().readBytes()


        private val images = loadImages()
        private val handlebars = Handlebars(ClassPathTemplateLoader("/$ROOT")).apply {
            registerHelper("image", Helper<String> { context, _ ->
                if (context == null) "" else images[context]
            })
            registerHelper("eq", Helper<String> { context, options ->
                if (context == options.param(0)) options.fn() else options.inverse()
            })
            registerHelper("fritekst", Helper<String> { context, _ ->
                if (context == null) "" else {
                    val text = Handlebars.Utils.escapeExpression(context)
                        .toString()
                        .replace(Regex("\\r\\n|[\\n\\r]"), "<br/>")
                    Handlebars.SafeString(text)
                }
            })

            infiniteLoops(true)
        }

        private val soknadTemplate = handlebars.compile(SOKNAD)

        private val ZONE_ID = ZoneId.of("Europe/Oslo")
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZONE_ID)
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZONE_ID)

        private fun loadPng(name: String): String {
            val bytes = "$ROOT/images/$name.png".fromResources().readBytes()
            val base64string = Base64.getEncoder().encodeToString(bytes)
            return "data:image/png;base64,$base64string"
        }
        private fun loadImages() = mapOf(
            "Checkbox_off.png" to loadPng("Checkbox_off"),
            "Checkbox_on.png" to loadPng("Checkbox_on"),
            "Hjelp.png" to loadPng("Hjelp"),
            "Navlogo.png" to loadPng("Navlogo"),
            "Personikon.png" to loadPng("Personikon"),
            "Fritekst.png" to loadPng("Fritekst")
        )
    }

    internal fun generateSoknadOppsummeringPdf(
        melding: MeldingV1,
        barnetsIdent: NorskIdent?,
        barnetsNavn: String?
    ) : ByteArray {
        soknadTemplate.apply(Context
            .newBuilder(mapOf(
                "soknad_id" to melding.soknadId,
                "soknad_mottatt_dag" to melding.mottatt.withZoneSameInstant(ZONE_ID).norskDag(),
                "soknad_mottatt" to DATE_TIME_FORMATTER.format(melding.mottatt),
                "søker" to mapOf(
                    "navn" to melding.søker.formatertNavn(),
                    "fødselsnummer" to melding.søker.fødselsnummer,
                    "relasjon_til_barnet" to melding.relasjonTilBarnet
                ),
                "barn" to mapOf(
                    "navn" to barnetsNavn,
                    "id" to barnetsIdent?.getValue()
                ),
                "kroniskEllerFunksjonshemming" to melding.kroniskEllerFunksjonshemming,
                "erYrkesaktiv" to melding.erYrkesaktiv,
                "delerOmsorg" to melding.delerOmsorg,
                "sammeAddresse" to melding.sammeAddresse,
                "medlemskap" to mapOf(
                    "har_bodd_i_utlandet_siste_12_mnd" to melding.medlemskap.harBoddIUtlandetSiste12Mnd,
                    "utenlandsopphold_siste_12_mnd" to melding.medlemskap.utenlandsoppholdSiste12Mnd.somMapUtenlandsopphold(),
                    "skal_bo_i_utlandet_neste_12_mnd" to melding.medlemskap.skalBoIUtlandetNeste12Mnd,
                    "utenlandsopphold_neste_12_mnd" to melding.medlemskap.utenlandsoppholdNeste12Mnd.somMapUtenlandsopphold()
                ),
                "samtykke" to mapOf(
                    "har_forstatt_rettigheter_og_plikter" to melding.harForstattRettigheterOgPlikter,
                    "har_bekreftet_opplysninger" to melding.harBekreftetOpplysninger
                ),
                "hjelp" to mapOf(
                    "språk" to melding.språk?.sprakTilTekst()
                )
            ))
            .resolver(MapValueResolver.INSTANCE)
            .build()).let { html ->
            val outputStream = ByteArrayOutputStream()

            PdfRendererBuilder()
                .useFastMode()
                .withHtmlContent(html, "")
                .medFonter()
                .toStream(outputStream)
                .buildPdfRenderer()
                .createPDF()

            return outputStream.use {
                it.toByteArray()
            }
        }
    }


    private fun PdfRendererBuilder.medFonter() =
        useFont({ ByteArrayInputStream(REGULAR_FONT) }, "Source Sans Pro", 400, BaseRendererBuilder.FontStyle.NORMAL, false)
        .useFont({ ByteArrayInputStream(BOLD_FONT) }, "Source Sans Pro", 700, BaseRendererBuilder.FontStyle.NORMAL, false)
        .useFont({ ByteArrayInputStream(ITALIC_FONT) }, "Source Sans Pro", 400, BaseRendererBuilder.FontStyle.ITALIC, false)
}

private fun List<Utenlandsopphold>.somMapUtenlandsopphold(): List<Map<String, Any?>> {
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Oslo"))
    return map {
        mapOf<String,Any?>(
            "landnavn" to it.landnavn,
            "fraOgMed" to dateFormatter.format(it.fraOgMed),
            "tilOgMed" to dateFormatter.format(it.tilOgMed)
            )
    }
}


private fun Søker.formatertNavn() = if (mellomnavn != null) "$fornavn $mellomnavn $etternavn" else "$fornavn $etternavn"
private fun String.sprakTilTekst() = when (this.toLowerCase()) {
    "nb" -> "bokmål"
    "nn" -> "nynorsk"
    else -> this
}
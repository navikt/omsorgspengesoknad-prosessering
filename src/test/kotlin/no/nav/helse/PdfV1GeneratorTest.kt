package no.nav.helse

import no.nav.helse.aktoer.Fodselsnummer
import no.nav.helse.prosessering.v1.*
import org.junit.Ignore
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.test.Test

class PdfV1GeneratorTest {

    private companion object {
        private val generator = PdfV1Generator()
        private val barnetsIdent = Fodselsnummer("02119970078")
        private val barnetsFødselsdato = LocalDate.now()
        private val fødselsdato = LocalDate.now()
        private val barnetsNavn = "Ole Dole"
    }

    private fun fullGyldigMelding(soknadsId: String): MeldingV1 {
        return MeldingV1(
            språk = "nb",
            soknadId = soknadsId,
            mottatt = ZonedDateTime.now(),
            søker = Søker(
                aktørId = "123456",
                fornavn = "Ærling",
                mellomnavn = "Øverbø",
                etternavn = "Ånsnes",
                fødselsnummer = "29099012345",
                fødselsdato = fødselsdato
            ),
            barn = Barn(
                fødselsnummer = barnetsIdent.getValue(),
                fødselsdato = barnetsFødselsdato,
                aktørId = "123456",
                navn = barnetsNavn
            ),
            relasjonTilBarnet = "Mor",
            medlemskap = Medlemskap(
                harBoddIUtlandetSiste12Mnd = true,
                utenlandsoppholdSiste12Mnd = listOf(
                    Utenlandsopphold(
                        LocalDate.of(2020, 1, 2),
                        LocalDate.of(2020, 1, 3),
                        "US", "USA"
                    )
                ),
                skalBoIUtlandetNeste12Mnd = false
            ),
            harForstattRettigheterOgPlikter = true,
            harBekreftetOpplysninger = true
        )
    }

    private fun gyldigMelding(
        soknadId: String,
        språk: String? = "nb",
        organisasjoner: List<Organisasjon> = listOf(
            Organisasjon(
                organisasjonsnummer = "987564785",
                navn = "NAV"
            ),
            Organisasjon(
                organisasjonsnummer = "975124568",
                navn = "Kiwi"
            ),
            Organisasjon(
                organisasjonsnummer = "952352687",
                navn = "Bjerkheim gård"
            ),
            Organisasjon(
                organisasjonsnummer = "952352655",
                navn = "Hopp i havet"
            )
        ),
        barn: Barn = Barn(
            navn = "Børge Øverbø Ånsnes",
            fødselsnummer = null,
            aktørId = null,
            fødselsdato = barnetsFødselsdato
        ),
        medlemskap: Medlemskap = Medlemskap(
            harBoddIUtlandetSiste12Mnd = true,
            utenlandsoppholdSiste12Mnd = listOf(
                Utenlandsopphold(
                    LocalDate.of(2020, 1, 2),
                    LocalDate.of(2020, 1, 3),
                    "US", "USA"
                )
            ),
            skalBoIUtlandetNeste12Mnd = false
        )
    ) = MeldingV1(
        språk = språk,
        soknadId = soknadId,
        mottatt = ZonedDateTime.now(),
        søker = Søker(
            aktørId = "123456",
            fornavn = "Ærling",
            mellomnavn = "Øverbø",
            etternavn = "Ånsnes",
            fødselsnummer = "29099012345",
            fødselsdato = fødselsdato
        ),
        barn = barn,
        relasjonTilBarnet = "Onkel & Nærstående ' <> \" {}",
        medlemskap = medlemskap,
        harForstattRettigheterOgPlikter = true,
        harBekreftetOpplysninger = true
    )

    private fun genererOppsummeringsPdfer(writeBytes: Boolean) {
        var id = "1-full-søknad"
        var pdf = generator.generateSoknadOppsummeringPdf(
            melding = fullGyldigMelding(soknadsId = id),
            barnetsIdent = barnetsIdent,
            barnetsNavn = barnetsNavn
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)

        id = "2-utenMedsoker"
        pdf = generator.generateSoknadOppsummeringPdf(
            melding = gyldigMelding(soknadId = id),
            barnetsIdent = barnetsIdent,
            barnetsNavn = barnetsNavn
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)

        id = "3-medsøkerSamtidigHjemme"
        pdf = generator.generateSoknadOppsummeringPdf(
            melding = gyldigMelding(
                soknadId = id,
                barn = Barn(fødselsnummer = null,  navn = null, aktørId = null, fødselsdato = null)
            ),
            barnetsIdent = barnetsIdent,
            barnetsNavn = barnetsNavn
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)

        id = "4-medsøkerIkkeSamtidigHjemme"
        pdf = generator.generateSoknadOppsummeringPdf(
            melding = gyldigMelding(
                soknadId = id,
                barn = Barn(fødselsnummer = null,  navn = null, aktørId = null, fødselsdato = null)
            ),
            barnetsIdent = barnetsIdent,
            barnetsNavn = barnetsNavn
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)

        id = "5-utenSprak"
        pdf = generator.generateSoknadOppsummeringPdf(
            melding = gyldigMelding(soknadId = id, språk = null),
            barnetsIdent = barnetsIdent,
            barnetsNavn = barnetsNavn
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)

        id = "6-utenArbeidsgivere"
        pdf = generator.generateSoknadOppsummeringPdf(
            melding = gyldigMelding(soknadId = id, organisasjoner = listOf()),
            barnetsIdent = barnetsIdent,
            barnetsNavn = barnetsNavn
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)

        id = "7-utenInfoPaaBarn"
        pdf = generator.generateSoknadOppsummeringPdf(
            melding = gyldigMelding(
                soknadId = id,
                organisasjoner = listOf(),
                barn = Barn(fødselsnummer = null,  navn = null, aktørId = null, fødselsdato = null)
            ),
            barnetsIdent = barnetsIdent,
            barnetsNavn = barnetsNavn
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)

        id = "8-utenGrad"
        pdf = generator.generateSoknadOppsummeringPdf(
            melding = gyldigMelding(
                soknadId = id,
                organisasjoner = listOf(),
                barn = Barn(fødselsnummer = null,  navn = null, aktørId = null, fødselsdato = null)
            ),
            barnetsIdent = barnetsIdent,
            barnetsNavn = barnetsNavn
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)

        id = "9-utenDagerBorteFraJobb"
        pdf = generator.generateSoknadOppsummeringPdf(
            melding = gyldigMelding(
                soknadId = id,
                organisasjoner = listOf(),
                barn = Barn(fødselsnummer = null,  navn = null, aktørId = null, fødselsdato = null)
            ),
            barnetsIdent = barnetsIdent,
            barnetsNavn = barnetsNavn
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)

        id = "10-medTilsynsOrdningJa"
        pdf = generator.generateSoknadOppsummeringPdf(
            melding = gyldigMelding(
                soknadId = id,
                organisasjoner = listOf(
                    Organisasjon(
                        organisasjonsnummer = "987564785",
                        navn = "NAV",
                        skalJobbeProsent = 22.5
                    ),
                    Organisasjon(
                        organisasjonsnummer = "975124568",
                        navn = "Kiwi",
                        skalJobbeProsent = 88.3123
                    )
                ),
                barn = Barn(fødselsnummer = null,  navn = null, aktørId = null, fødselsdato = null)
            ), barnetsIdent = barnetsIdent, barnetsNavn = barnetsNavn
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)

        id = "11-medTilsynsordningVetIkke"
        pdf = generator.generateSoknadOppsummeringPdf(
            melding = gyldigMelding(
                soknadId = id,
                organisasjoner = listOf(),
                barn = Barn(fødselsnummer = null,  navn = null, aktørId = null,  fødselsdato = null)
            ), barnetsIdent = barnetsIdent, barnetsNavn = barnetsNavn
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)


        id = "12-medTilsynsordningNei"
        pdf = generator.generateSoknadOppsummeringPdf(
            melding = gyldigMelding(
                soknadId = id,
                organisasjoner = listOf(),
                barn = Barn(fødselsnummer = null,  navn = null, aktørId = null, fødselsdato = null)
            ), barnetsIdent = barnetsIdent, barnetsNavn = barnetsNavn
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)

        id = "13-skalJobbeRedusert"
        pdf = generator.generateSoknadOppsummeringPdf(
            melding = gyldigMelding(
                soknadId = id,
                organisasjoner = listOf(
                    Organisasjon(
                        organisasjonsnummer = "952352655",
                        navn = "Hopp i havet",
                        skalJobbe = "redusert",
                        jobberNormaltTimer = 30.0,
                        skalJobbeProsent = 50.0
                    )
                ),
                barn = Barn(fødselsnummer = null,  navn = null, aktørId = null, fødselsdato = null)
            ),
            barnetsIdent = barnetsIdent,
            barnetsNavn = barnetsNavn
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)

        id = "14-skalJobbeVetIkke"
        pdf = generator.generateSoknadOppsummeringPdf(
            melding = gyldigMelding(
                soknadId = id,
                organisasjoner = listOf(
                    Organisasjon(
                        organisasjonsnummer = "952352655",
                        navn = "Hopp i havet",
                        skalJobbe = "vet_ikke",
                        jobberNormaltTimer = 30.0,
                        vetIkkeEkstrainfo = "Vondt i hode, skulker, kne og tå, kne og tå"
                    )
                ),
                barn = Barn(fødselsnummer = null,  navn = null, aktørId = null, fødselsdato = null)
            ),
            barnetsIdent = barnetsIdent,
            barnetsNavn = barnetsNavn
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)

        id = "15-skalJobbeJa"
        pdf = generator.generateSoknadOppsummeringPdf(
            melding = gyldigMelding(
                soknadId = id,
                organisasjoner = listOf(
                    Organisasjon(
                        organisasjonsnummer = "952352655",
                        navn = "Hopp i havet",
                        skalJobbe = "ja",
                        skalJobbeProsent = 100.0
                    )
                ),
                barn = Barn(fødselsnummer = null,  navn = null, aktørId = null, fødselsdato = null)
            ),
            barnetsIdent = barnetsIdent,
            barnetsNavn = barnetsNavn
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)

        id = "16-skalJobbeNei"
        pdf = generator.generateSoknadOppsummeringPdf(
            melding = gyldigMelding(
                soknadId = id,
                organisasjoner = listOf(
                    Organisasjon(
                        organisasjonsnummer = "952352655",
                        navn = "Hopp i havet",
                        skalJobbe = "nei",
                        skalJobbeProsent = 0.0
                    )
                ),
                barn = Barn(fødselsnummer = null,  navn = null, aktørId = null, fødselsdato = null)
            ),
            barnetsIdent = barnetsIdent,
            barnetsNavn = barnetsNavn
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)

        id = "17-flereArbeidsgivereSkalJobbeJaNeiVetIkkeRedusert"
        pdf = generator.generateSoknadOppsummeringPdf(
            melding = gyldigMelding(
                soknadId = id,
                organisasjoner = listOf(
                    Organisasjon(
                        organisasjonsnummer = "952352655",
                        navn = "Arbeidsgiver 1",
                        skalJobbe = "ja",
                        skalJobbeProsent = 100.0
                    ),
                    Organisasjon(
                        organisasjonsnummer = "952352655",
                        navn = "Arbeidsgiver 2",
                        skalJobbe = "nei",
                        skalJobbeProsent = 0.0
                    ),
                    Organisasjon(
                        organisasjonsnummer = "952352655",
                        navn = "Arbeidsgiver 3",
                        skalJobbe = "vet_ikke",
                        jobberNormaltTimer = 30.0,
                        vetIkkeEkstrainfo = "Vondt i hode, skulker, kne og tå, kne og tå"
                    ),
                    Organisasjon(
                        organisasjonsnummer = "952352655",
                        navn = "Arbeidsgiver 4",
                        skalJobbe = "redusert",
                        jobberNormaltTimer = 30.0,
                        skalJobbeProsent = 50.0
                    )
                ),
                barn = Barn(fødselsnummer = null,  navn = null, aktørId = null, fødselsdato = null)
            ),
            barnetsIdent = barnetsIdent,
            barnetsNavn = barnetsNavn
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)

        id = "18-flerePlanlagteUtenlandsopphold"
        pdf = generator.generateSoknadOppsummeringPdf(
            melding = gyldigMelding(
                soknadId = id,
                medlemskap = Medlemskap(
                    harBoddIUtlandetSiste12Mnd = false,
                    utenlandsoppholdSiste12Mnd = listOf(),
                    skalBoIUtlandetNeste12Mnd = true,
                    utenlandsoppholdNeste12Mnd = listOf(
                        Utenlandsopphold(
                            LocalDate.of(2022, 1, 2),
                            LocalDate.of(2022, 1, 3),
                            "US", "USA"
                        ) ,Utenlandsopphold(
                            LocalDate.of(2022, 1, 3),
                            LocalDate.of(2022, 1, 4),
                            "DK", "Danmark"
                        )
                    )
                )
            ),
            barnetsIdent = barnetsIdent,
            barnetsNavn = barnetsNavn
        )
        if (writeBytes) File(pdfPath(soknadId = id)).writeBytes(pdf)

    }

    private fun pdfPath(soknadId: String) = "${System.getProperty("user.dir")}/generated-pdf-$soknadId.pdf"

    @Test
    fun `generering av oppsummerings-PDF fungerer`() {
        genererOppsummeringsPdfer(false)
    }

    @Test
    @Ignore
    fun `opprett lesbar oppsummerings-PDF`() {
        genererOppsummeringsPdfer(true)
    }
}
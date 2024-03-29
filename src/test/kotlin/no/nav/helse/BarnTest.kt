package no.nav.helse

import io.prometheus.client.CollectorRegistry
import no.nav.helse.prosessering.v1.*
import no.nav.helse.utils.aarSiden
import no.nav.helse.utils.erUnderEttAar
import no.nav.helse.utils.fodseldato
import no.nav.helse.utils.ukerSiden
import org.junit.Before
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BarnTest {
    private companion object {
        val now = LocalDate.now(ZoneId.of("Europe/Oslo"))
    }

    @Before
    fun setUp() {
        CollectorRegistry.defaultRegistry.clear()
    }

    @Test
    fun `Test barnets alder i aar`() {
        for (i in 0..18) {
            assertEquals(barn(i.toLong()).fodseldato()!!.aarSiden(), i.toDouble())
        }

        for (i in 19..99) {
            assertTrue(barn(i.toLong()).fodseldato()!!.aarSiden() > 18.00)
        }
    }

    @Test
    fun `Barn under 1 aar i uker`() {
        for (i in 0..52) {
            val fodseldato = LocalDate.now().minusWeeks(i.toLong())
            assertTrue(fodseldato.aarSiden().erUnderEttAar())
            assertEquals("$i", fodseldato.ukerSiden())
        }
        val fodseldato = LocalDate.now().minusWeeks(53)
        assertFalse(fodseldato.aarSiden().erUnderEttAar())
    }

    private fun barn(forventetAlder: Long): Barn {
        val fodselsdato = if (forventetAlder == 0L) now.minusDays(1) else now.minusYears(forventetAlder)
        val dag = fodselsdato.dayOfMonth.toString().padStart(2, '0')
        val maned = fodselsdato.monthValue.toString().padStart(2, '0')
        val ar = fodselsdato.year.toString().substring(2, 4)
        val fodselsnummer = "$dag$maned${ar}12345"
        return Barn(
            norskIdentifikator = fodselsnummer,
            fødselsdato = null,
            navn = "Barn Barnesen",
            aktørId = null
        )
    }
}

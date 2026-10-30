package de.memet.nckreis

import de.memet.nckreis.core.Kante
import de.memet.nckreis.core.KantenEingabe
import de.memet.nckreis.core.NcProgramm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KanteTest {
    /** Werte, die am 29.10. an der Maschine ohne Alarm 308 liefen. */
    private val maschine = """
        G01 X87.98 Z-4.175
        G02 X87.919 Z-3.828 I-2 K0
        G01 X86.861 Z-0.826
        G02 X84.891 Z0 I-0.985 K-0.174
    """.trimIndent()

    @Test fun zeichnungErgibtMaschinenwerte() {
        val e = Kante.berechne(KantenEingabe(87.98, -4.0, 0.0, 10.0, 2.0, 1.0))
        assertEquals(maschine, NcProgramm.ncBlock(e))
        val (d1, d2) = Kante.radiusAbweichungGerundet(e)
        assertTrue(d1 < 0.001 && d2 < 0.001)
    }

    @Test fun fehlerhafterCodeWirdKorrigiert() {
        val falsch = """
            N120 G00 X88.2 Z-4.18
            N130 G01 X87.98
            N140 G02 G64 X87.92 Z-3.81 I-2 K0
            N150 G01 G64 G08 X86.86 Z-0.83 F=2.8
            N160 G02 G64 X84.891 Z0 I-0.985 K-0.174
        """.trimIndent()
        val p = NcProgramm(falsch.lines())
        assertTrue(p.kreise.first().fehlerhaft)
        val m = p.findeMuster()!!
        val (neu, _) = p.ersetze(m, Kante.berechne(p.eingabeAus(m)))
        val zeilen = neu.lines()
        assertEquals("N120 G00 X88.2 Z-4.175", zeilen[0])
        assertEquals("N140 G02 G64 X87.919 Z-3.828 I-2 K0", zeilen[2])
        assertEquals("N150 G01 G64 G08 X86.861 Z-0.826 F=2.8", zeilen[3])
        assertEquals("N160 G02 G64 X84.891 Z0 I-0.985 K-0.174", zeilen[4])
        assertTrue(NcProgramm(zeilen).kreise.none { it.fehlerhaft })
    }

    @Test fun gespiegelteKante() {
        val e = Kante.berechne(KantenEingabe(87.98, 4.0, 0.0, 10.0, 2.0, 1.0))
        assertEquals("G03", e.gKreis)
        assertEquals(0.826, e.start2.z, 0.0006)
    }
}

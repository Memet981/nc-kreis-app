package de.memet.nckreis.core

import kotlin.math.abs
import kotlin.math.hypot

/** Ein Wort im Satz, z.B. X87.98, F=2.4, Z R55. Position bezieht sich auf die Originalzeile. */
data class Wort(val adresse: String, val text: String, val zahl: Double?, val rRef: Int?, val von: Int, val bis: Int)

enum class Art { G00, G01, G02, G03 }

/** Ein Fahrsatz mit bekannten (oder unbekannten) Positionen. */
data class Fahrsatz(
    val zeile: Int,
    val art: Art,
    val start: P?,
    val ende: P?,
    val i: Double = 0.0,
    val k: Double = 0.0,
) {
    val istKreis get() = art == Art.G02 || art == Art.G03
    val mitte: P? get() = if (istKreis && start != null) P(start.z + k, start.r + i) else null
    /** Radius Start→Mitte und Ende→Mitte */
    val radien: Pair<Double, Double>? get() {
        val m = mitte ?: return null
        val e = ende ?: return null
        val s = start ?: return null
        return hypot(s.z - m.z, s.r - m.r) to hypot(e.z - m.z, e.r - m.r)
    }
    val deltaR: Double? get() = radien?.let { abs(it.first - it.second) }
    val fehlerhaft: Boolean get() = (deltaR ?: 0.0) > NcProgramm.TOLERANZ
}

/** Erkannte Kante: Kreis – Gerade – Kreis, wie in Satz N140…N160. */
data class Muster(val kreis1: Fahrsatz, val gerade: Fahrsatz, val kreis2: Fahrsatz, val zStartZeile: Int?)

class NcProgramm(val zeilen: List<String>) {

    val fahrsaetze: List<Fahrsatz>
    val hinweise = mutableListOf<String>()
    private val rWerte = mutableMapOf<Int, Double>()

    init {
        val liste = mutableListOf<Fahrsatz>()
        var modal = Art.G00
        var z: Double? = null
        var r: Double? = null
        var inkremental = false
        zeilen.forEachIndexed { idx, zeile ->
            val woerter = woerter(zeile)
            // R-Zuweisungen zuerst (R30=87.98)
            woerter.filter { it.adresse.matches(Regex("R\\d+")) && it.text.contains('=') && it.zahl != null }
                .forEach { rWerte[it.adresse.substring(1).toInt()] = it.zahl!! }
            var neueArt: Art? = null
            woerter.filter { it.adresse == "G" }.forEach { w ->
                when (w.zahl?.toInt()) {
                    0 -> neueArt = Art.G00; 1 -> neueArt = Art.G01
                    2 -> neueArt = Art.G02; 3 -> neueArt = Art.G03
                    90 -> inkremental = false; 91 -> inkremental = true
                }
            }
            neueArt?.let { modal = it }
            val xw = woerter.firstOrNull { it.adresse == "X" }
            val zw = woerter.firstOrNull { it.adresse == "Z" }
            if (xw == null && zw == null) return@forEachIndexed
            if (inkremental) hinweise.add("Zeile ${idx + 1}: G91 (inkremental) wird nicht ausgewertet.")
            val start = if (z != null && r != null) P(z!!, r!!) else null
            if (xw != null) r = wert(xw)?.div(2)
            if (zw != null) z = wert(zw)
            val ende = if (z != null && r != null) P(z!!, r!!) else null
            val i = woerter.firstOrNull { it.adresse == "I" }?.let { wert(it) } ?: 0.0
            val k = woerter.firstOrNull { it.adresse == "K" }?.let { wert(it) } ?: 0.0
            liste.add(Fahrsatz(idx, modal, start, ende, i, k))
        }
        fahrsaetze = liste
    }

    private fun wert(w: Wort): Double? = w.zahl ?: w.rRef?.let { rWerte[it] }

    val kreise: List<Fahrsatz> get() = fahrsaetze.filter { it.istKreis }

    /** Sucht Kreis–Gerade–Kreis. Bevorzugt Muster mit fehlerhaftem Kreis. */
    fun findeMuster(): Muster? {
        val kandidaten = mutableListOf<Muster>()
        for (j in 0 until fahrsaetze.size - 2) {
            val a = fahrsaetze[j]; val l = fahrsaetze[j + 1]; val b = fahrsaetze[j + 2]
            if (!a.istKreis || l.art != Art.G01 || !b.istKreis) continue
            if (a.start == null || a.ende == null || l.ende == null || b.ende == null) continue
            val zStart = (j - 1 downTo 0).map { fahrsaetze[it] }
                .firstOrNull { woerter(zeilen[it.zeile]).any { w -> w.adresse == "Z" } }?.zeile
            kandidaten.add(Muster(a, l, b, zStart))
        }
        return kandidaten.firstOrNull { it.kreis1.fehlerhaft || it.kreis2.fehlerhaft } ?: kandidaten.firstOrNull()
    }

    /** Zeichnungswerte aus dem (evtl. fehlerhaften) Code ableiten – als Vorschlag für den Dialog. */
    fun eingabeAus(m: Muster): KantenEingabe {
        val s0 = m.kreis1.start!!
        val r0 = s0.r
        val lA = m.kreis1.ende!!; val lE = m.gerade.ende!!
        val winkel = Kante.winkelAus(lA, lE)
        val seite = if (m.kreis2.ende!!.z >= s0.z) 1.0 else -1.0
        // Zeichnungswerte sind meist rund: Winkel auf 0.5°, Ecke auf 0.1 mm einrasten, wenn nah dran
        val wGerastet = Math.round(winkel * 2) / 2.0
        val w = if (abs(wGerastet - winkel) < 0.2) wGerastet else Math.round(winkel * 100) / 100.0
        val ta = kotlin.math.tan(Math.toRadians(w))
        val zRoh = lE.z + seite * (lE.r - r0) / ta
        val zGerastet = Math.round(zRoh * 10) / 10.0
        val zEcke = if (abs(zGerastet - zRoh) < 0.03) zGerastet else zRoh
        return KantenEingabe(
            durchmesser = Fmt.rund(2 * r0),
            zEcke = Fmt.rund(zEcke),
            zPlan = Fmt.rund(m.kreis2.ende.z),
            winkel = w,
            radiusZylinder = Fmt.rund(hypot(m.kreis1.i, m.kreis1.k)),
            radiusPlan = Fmt.rund(hypot(m.kreis2.i, m.kreis2.k)),
        )
    }

    /** Neuer Programmtext mit korrigierten Werten. Liefert Text und die geänderten Zeilennummern. */
    fun ersetze(m: Muster, e: KantenErgebnis): Pair<String, Set<Int>> {
        val neu = zeilen.toMutableList()
        val geaendert = mutableSetOf<Int>()
        fun setze(zeile: Int, vararg werte: Pair<String, Double>) {
            var t = neu[zeile]
            for ((a, v) in werte) t = setzeWort(t, a, Fmt.zahl(v))
            if (t != neu[zeile]) { neu[zeile] = t; geaendert.add(zeile) }
        }
        fun setzeG(zeile: Int, g: String) {
            val t = setzeGKreis(neu[zeile], g)
            if (t != neu[zeile]) { neu[zeile] = t; geaendert.add(zeile) }
        }
        m.zStartZeile?.let { setze(it, "Z" to e.start.z) }
        setzeG(m.kreis1.zeile, e.gKreis)
        setze(m.kreis1.zeile, "X" to e.ende1.x, "Z" to e.ende1.z, "I" to e.i1, "K" to e.k1)
        setze(m.gerade.zeile, "X" to e.start2.x, "Z" to e.start2.z)
        setzeG(m.kreis2.zeile, e.gKreis)
        setze(m.kreis2.zeile, "X" to e.ende2.x, "Z" to e.ende2.z, "I" to e.i2, "K" to e.k2)
        return neu.joinToString("\n") to geaendert
    }

    companion object {
        const val TOLERANZ = 0.001

        private val NUM = "-?(?:\\d+\\.?\\d*|\\.\\d+)"
        private val MIT_GLEICH = Regex("([A-Z]{1,3})(\\d*)\\s*=\\s*($NUM|-?R\\d+)")
        private val R_REF = Regex("([XZIKF])\\s*(-?)R(\\d+)")
        private val EINFACH = Regex("([A-Z])\\s*($NUM)")

        fun gross(s: String) = String(CharArray(s.length) { s[it].uppercaseChar() })

        /** Kommentar ab ';' oder in Klammern wird ignoriert. */
        fun woerter(zeile: String): List<Wort> {
            val u = gross(zeile)
            val ende = u.indexOf(';').let { if (it < 0) u.length else it }
            val ergebnis = mutableListOf<Wort>()
            var p = 0
            while (p < ende) {
                val c = u[p]
                if (c == '(') { p = u.indexOf(')', p).let { if (it < 0) ende else it + 1 }; continue }
                if (!c.isLetter() || (p > 0 && u[p - 1].isLetter())) { p++; continue }
                val m1 = MIT_GLEICH.matchAt(u, p)
                if (m1 != null && m1.range.last < ende) {
                    val v = m1.groupValues[3]
                    val ref = Regex("(-?)R(\\d+)").matchEntire(v)
                    ergebnis.add(Wort(m1.groupValues[1] + m1.groupValues[2], m1.value, if (ref == null) v.toDoubleOrNull() else null,
                        ref?.groupValues?.get(2)?.toInt(), m1.range.first, m1.range.last + 1))
                    p = m1.range.last + 1; continue
                }
                val m2 = R_REF.matchAt(u, p)
                if (m2 != null && m2.range.last < ende) {
                    ergebnis.add(Wort(m2.groupValues[1], m2.value, null, m2.groupValues[3].toInt(), m2.range.first, m2.range.last + 1))
                    p = m2.range.last + 1; continue
                }
                val m3 = EINFACH.matchAt(u, p)
                if (m3 != null && m3.range.last < ende) {
                    ergebnis.add(Wort(m3.groupValues[1], m3.value, m3.groupValues[2].toDoubleOrNull(), null, m3.range.first, m3.range.last + 1))
                    p = m3.range.last + 1; continue
                }
                p++
            }
            return ergebnis
        }

        /** Ersetzt oder ergänzt ein Adresswort (X, Z, I, K). */
        fun setzeWort(zeile: String, adresse: String, wert: String): String {
            val w = woerter(zeile).firstOrNull { it.adresse == adresse }
            if (w != null) return zeile.substring(0, w.von) + adresse + wert + zeile.substring(w.bis)
            // anhängen hinter dem letzten Achswort, sonst vor dem Kommentar
            val achsen = woerter(zeile).filter { it.adresse in listOf("X", "Z", "I", "K") }
            val pos = achsen.maxOfOrNull { it.bis } ?: zeile.indexOf(';').let { if (it < 0) zeile.trimEnd().length else it }
            return zeile.substring(0, pos) + " " + adresse + wert + zeile.substring(pos)
        }

        fun setzeGKreis(zeile: String, g: String): String {
            val w = woerter(zeile).firstOrNull { it.adresse == "G" && it.zahl?.toInt() in listOf(2, 3) }
            return if (w != null) zeile.substring(0, w.von) + g + zeile.substring(w.bis) else zeile
        }

        /** Typische Lesefehler der Texterkennung bereinigen. */
        fun ocrBereinigen(text: String): String = text.lines().joinToString("\n") { z ->
            var t = gross(z).replace('—', '-').replace('–', '-').replace('−', '-')
            // 87,98 -> 87.98
            t = t.replace(Regex("(\\d),(\\d)"), "$1.$2")
            // O als 0 innerhalb von Zahlen nach Adressbuchstaben
            // "X 87.98" -> "X87.98", "X- 4" -> "X-4"
            t = t.replace(Regex("\\b([XZIKNGL])\\s+(-?)\\s*([\\dO.])"), "$1$2$3")
            t = t.replace(Regex("(?<=[XZIKFGNR=\\-][\\d.]{0,10})O(?=[\\d.\\s]|$)"), "0")
            // 9L9 / 9|9 -> 919
            t = t.replace(Regex("(?<=\\d)[L|](?=\\d)"), "1")
            t = t.replace(Regex("-\\s+(\\d)"), "-$1")
            t.replace(Regex("\\s{2,}"), " ").trim()
        }.trim()

        fun ncBlock(e: KantenErgebnis): String {
            val g = e.gKreis
            fun f(v: Double) = Fmt.zahl(v)
            return listOf(
                "G01 X${f(e.eingabe.durchmesser)} Z${f(e.start.z)}",
                "$g X${f(e.ende1.x)} Z${f(e.ende1.z)} I${f(e.i1)} K${f(e.k1)}",
                "G01 X${f(e.start2.x)} Z${f(e.start2.z)}",
                "$g X${f(e.ende2.x)} Z${f(e.ende2.z)} I${f(e.i2)} K${f(e.k2)}",
            ).joinToString("\n")
        }

        fun l170Aufruf(e: KantenEingabe, anhalten: Boolean = true): String {
            fun f(v: Double) = Fmt.zahl(v)
            return "R30=${f(e.durchmesser)} R31=${f(e.zEcke)} R32=${f(e.zPlan)} R33=${f(e.winkel)} " +
                "R34=${f(e.radiusZylinder)} R35=${f(e.radiusPlan)} R36=${e.seite} R37=${if (anhalten) 1 else 0}\n" +
                "L170\n" +
                "G01 X R30 Z R55\n" +
                "${if (e.seite > 0) "G02" else "G03"} X R56 Z R57 I R58 K R59\n" +
                "G01 X R60 Z R61\n" +
                "${if (e.seite > 0) "G02" else "G03"} X R62 Z R63 I R64 K R65"
        }
    }
}

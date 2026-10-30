package de.memet.nckreis.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/** Punkt in der Drehteil-Ebene: z = Längsachse, r = Radius (X/2). */
data class P(val z: Double, val r: Double) {
    val x: Double get() = 2 * r
}

/**
 * Eingaben wie Zyklus L170.
 * R30 Ø Zylinder, R31 Z scharfe Ecke, R32 Z Planfläche, R33 Winkel der Schräge (zur Z-Achse),
 * R34 Radius an der Ecke Zylinder/Schräge, R35 Radius an der Ecke Schräge/Planfläche.
 * Die Seite (R36) ergibt sich aus R31/R32: Planfläche bei größerem Z = +1, sonst -1.
 */
data class KantenEingabe(
    val durchmesser: Double,
    val zEcke: Double,
    val zPlan: Double,
    val winkel: Double,
    val radiusZylinder: Double,
    val radiusPlan: Double,
) {
    val seite: Int get() = if (zPlan >= zEcke) 1 else -1
}

/** Ergebnis in derselben Belegung wie L170 (R55…R65). */
data class KantenErgebnis(
    val eingabe: KantenEingabe,
    val start: P,          // R55: Tangentenpunkt auf dem Zylinder (Z), X = R30
    val ende1: P,          // R56/R57
    val mitte1: P,
    val i1: Double, val k1: Double, // R58/R59
    val start2: P,         // R60/R61
    val ende2: P,          // R62/R63
    val mitte2: P,
    val i2: Double, val k2: Double, // R64/R65
    val ecke1: P,          // scharfe Ecke Zylinder/Schräge
    val ecke2: P,          // scharfe Ecke Schräge/Planfläche
) {
    val gKreis: String get() = if (eingabe.seite > 0) "G02" else "G03"
    /** R34 = 0: scharfe Ecke Zylinder/Schräge, kein erster Kreis. */
    val ohneKreis1: Boolean get() = eingabe.radiusZylinder == 0.0
    /** R35 = 0: scharfe Ecke Schräge/Planfläche, kein zweiter Kreis. */
    val ohneKreis2: Boolean get() = eingabe.radiusPlan == 0.0

    fun rParameter(): List<Pair<String, Double>> = listOf(
        "R55" to start.z, "R56" to ende1.x, "R57" to ende1.z, "R58" to i1, "R59" to k1,
        "R60" to start2.x, "R61" to start2.z, "R62" to ende2.x, "R63" to ende2.z,
        "R64" to i2, "R65" to k2,
    )
}

class GeometrieFehler(msg: String) : Exception(msg)

object Kante {
    fun berechne(e: KantenEingabe): KantenErgebnis {
        if (e.durchmesser <= 0) throw GeometrieFehler("Ø muss größer als 0 sein.")
        if (e.winkel <= 0 || e.winkel >= 90) throw GeometrieFehler("Winkel muss zwischen 0 und 90° liegen.")
        if (e.radiusZylinder < 0 || e.radiusPlan < 0) throw GeometrieFehler("Radien dürfen nicht negativ sein (0 = scharfe Ecke).")
        val s = e.seite.toDouble()
        val a = Math.toRadians(e.winkel)
        val ca = cos(a); val sa = sin(a); val ta = tan(a)
        val r0 = e.durchmesser / 2
        val laengeZ = abs(e.zPlan - e.zEcke)
        if (laengeZ <= 0) throw GeometrieFehler("Z Ecke und Z Planfläche dürfen nicht gleich sein.")
        val rB = r0 - laengeZ * ta
        if (rB <= 0) throw GeometrieFehler("Die Schräge läuft unter die Drehmitte – Winkel oder Z prüfen.")

        val t1 = e.radiusZylinder * tan(a / 2)
        val t2 = e.radiusPlan * tan((Math.PI / 2 - a) / 2)
        val schraege = laengeZ / ca
        if (t1 + t2 > schraege + 1e-9)
            throw GeometrieFehler(String.format(Locale.US,
                "Schräge zu kurz für beide Radien (Länge %.3f, gebraucht %.3f).", schraege, t1 + t2))
        if (t2 > rB) throw GeometrieFehler("Radius an der Planfläche zu groß.")

        val ecke1 = P(e.zEcke, r0)
        val ecke2 = P(e.zPlan, rB)
        val p0 = P(e.zEcke - s * t1, r0)
        val p1 = P(e.zEcke + s * t1 * ca, r0 - t1 * sa)
        val m1 = P(p0.z, r0 - e.radiusZylinder)
        val p2 = P(e.zPlan - s * t2 * ca, rB + t2 * sa)
        val p3 = P(e.zPlan, rB - t2)
        val m2 = P(e.zPlan - s * e.radiusPlan, rB - t2)
        return KantenErgebnis(
            e, p0, p1, m1, m1.r - p0.r, m1.z - p0.z,
            p2, p3, m2, m2.r - p2.r, m2.z - p2.z, ecke1, ecke2,
        )
    }

    /** Gerundete Werte so prüfen, wie die Steuerung sie sieht (3 Nachkommastellen). */
    fun radiusAbweichungGerundet(erg: KantenErgebnis): Pair<Double, Double> {
        fun r3(v: Double) = Fmt.rund(v)
        fun dr(s: P, e: P, i: Double, k: Double): Double {
            val sz = r3(s.z); val sr = r3(s.x) / 2
            val ez = r3(e.z); val er = r3(e.x) / 2
            val cz = sz + r3(k); val cr = sr + r3(i)
            return abs(hypot(sz - cz, sr - cr) - hypot(ez - cz, er - cr))
        }
        return dr(erg.start, erg.ende1, erg.i1, erg.k1) to dr(erg.start2, erg.ende2, erg.i2, erg.k2)
    }

    /** Winkel aus zwei Punkten einer Schräge (zur Z-Achse, in Grad). */
    fun winkelAus(a: P, b: P): Double = Math.toDegrees(atan(abs(b.r - a.r) / abs(b.z - a.z)))
}

object Fmt {
    fun rund(v: Double): Double = Math.round(v * 1000.0) / 1000.0

    /** 87.9190 -> "87.919", -2.000 -> "-2", -0.0 -> "0" */
    fun zahl(v: Double): String {
        var s = String.format(Locale.US, "%.3f", rund(v))
        s = s.trimEnd('0').trimEnd('.')
        if (s == "-0" || s.isEmpty()) s = "0"
        return s
    }
}

package de.memet.nckreis.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.memet.nckreis.core.P
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Ein Stück Bahn: Gerade (mitte == null) oder Kreis um mitte. */
data class GSeg(
    val a: P,
    val b: P,
    val mitte: P? = null,
    val uhrzeiger: Boolean = true,
    val fehler: Boolean = false,
    val eilgang: Boolean = false,
)

data class GPunkt(val p: P, val text: String)

/** Was gerade hervorgehoben wird (z.B. das Feld, das man ausfüllt). */
data class GHervor(val linien: List<Pair<P, P>> = emptyList(), val bogen: GSeg? = null, val punkt: P? = null, val text: String = "")

@Composable
fun KonturGrafik(
    bahn: List<GSeg>,
    modifier: Modifier = Modifier,
    zeichnung: List<P> = emptyList(),
    punkte: List<GPunkt> = emptyList(),
    mitten: List<P> = emptyList(),
    hervor: GHervor? = null,
    hoehe: Int = 260,
) {
    val cs = MaterialTheme.colorScheme
    val farbeBahn = cs.primary
    val farbeFehler = Color(0xFFD32F2F)
    val farbeHervor = Color(0xFFFF8F00)
    val farbeHilfe = cs.onSurfaceVariant.copy(alpha = 0.55f)
    val farbeText = cs.onSurface
    val messer = rememberTextMeasurer()
    var zoom by remember { mutableFloatStateOf(1f) }
    var verschub by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .fillMaxWidth()
            .height(hoehe.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.35f))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, z, _ ->
                    zoom = (zoom * z).coerceIn(0.5f, 40f)
                    verschub += pan
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { zoom = 1f; verschub = Offset.Zero })
            }
    ) {
        Canvas(Modifier.matchParentSize()) {
            val alle = buildList {
                bahn.forEach { add(it.a); add(it.b) }
                addAll(zeichnung); addAll(mitten); punkte.forEach { add(it.p) }
            }
            if (alle.isEmpty()) return@Canvas
            var zMin = alle.minOf { it.z }; var zMax = alle.maxOf { it.z }
            var rMin = alle.minOf { it.r }; var rMax = alle.maxOf { it.r }
            val spanne = max(max(zMax - zMin, rMax - rMin), 0.5)
            val rand = spanne * 0.18
            zMin -= rand; zMax += rand; rMin -= rand; rMax += rand
            val basis = min(size.width / (zMax - zMin), size.height / (rMax - rMin)).toFloat()
            val s = basis * zoom
            val mz = ((zMin + zMax) / 2).toFloat(); val mr = ((rMin + rMax) / 2).toFloat()
            fun bild(p: P) = Offset(
                size.width / 2 + (p.z.toFloat() - mz) * s + verschub.x,
                size.height / 2 - (p.r.toFloat() - mr) * s + verschub.y,
            )

            // Zeichnung (scharfe Ecken) gestrichelt
            if (zeichnung.size >= 2) {
                val pfad = Path().apply {
                    moveTo(bild(zeichnung[0]).x, bild(zeichnung[0]).y)
                    zeichnung.drop(1).forEach { lineTo(bild(it).x, bild(it).y) }
                }
                drawPath(pfad, farbeHilfe, style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
            }
            // Bahn
            bahn.forEach { seg ->
                val farbe = if (seg.fehler) farbeFehler else farbeBahn
                val breite = if (seg.eilgang) 2f else 5f
                val effekt = if (seg.eilgang) PathEffect.dashPathEffect(floatArrayOf(6f, 8f)) else null
                drawPath(pfadVon(seg, ::bild), farbe, style = Stroke(breite, pathEffect = effekt))
            }
            // Hervorhebung
            hervor?.let { h ->
                h.linien.forEach { (a, b) -> drawLine(farbeHervor, bild(a), bild(b), strokeWidth = 7f) }
                h.bogen?.let { drawPath(pfadVon(it, ::bild), farbeHervor, style = Stroke(9f)) }
                h.punkt?.let { drawCircle(farbeHervor, 11f, bild(it)) }
            }
            // Mittelpunkte
            mitten.forEach { m ->
                val o = bild(m)
                drawLine(farbeHilfe, o + Offset(-9f, 0f), o + Offset(9f, 0f), 2f)
                drawLine(farbeHilfe, o + Offset(0f, -9f), o + Offset(0f, 9f), 2f)
            }
            // Punkte mit Beschriftung
            punkte.forEach { gp ->
                val o = bild(gp.p)
                drawCircle(farbeText, 6f, o)
                val passt = o.x >= 0f && o.x < size.width - 60f && o.y > 34f && o.y < size.height
                if (gp.text.isNotEmpty() && passt) {
                    drawText(messer, gp.text, o + Offset(10f, -34f), TextStyle(color = farbeText, fontSize = 11.sp))
                }
            }
        }
        if (hervor != null && hervor.text.isNotEmpty()) {
            Text(
                hervor.text,
                color = Color(0xFFFF8F00),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
        }
        Text(
            "Zoomen mit zwei Fingern · Doppeltipp = zurücksetzen",
            style = MaterialTheme.typography.labelSmall,
            color = farbeHilfe,
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
        )
    }
}

private fun pfadVon(seg: GSeg, bild: (P) -> Offset): Path {
    val pfad = Path()
    val a = bild(seg.a)
    pfad.moveTo(a.x, a.y)
    val m = seg.mitte
    if (m == null) {
        val b = bild(seg.b); pfad.lineTo(b.x, b.y); return pfad
    }
    val ra = hypot(seg.a.z - m.z, seg.a.r - m.r)
    val rb = hypot(seg.b.z - m.z, seg.b.r - m.r)
    val ta = atan2(seg.a.r - m.r, seg.a.z - m.z)
    val tb = atan2(seg.b.r - m.r, seg.b.z - m.z)
    var lauf = tb - ta
    if (seg.uhrzeiger && lauf > 0) lauf -= 2 * PI
    if (!seg.uhrzeiger && lauf < 0) lauf += 2 * PI
    val n = max(12, (abs(lauf) * 30).toInt())
    for (i in 1..n) {
        val f = i.toDouble() / n
        val t = ta + lauf * f
        val r = ra + (rb - ra) * f
        val p = bild(P(m.z + r * cos(t), m.r + r * sin(t)))
        pfad.lineTo(p.x, p.y)
    }
    return pfad
}

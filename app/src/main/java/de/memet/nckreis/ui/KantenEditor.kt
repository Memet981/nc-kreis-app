package de.memet.nckreis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.memet.nckreis.core.Fmt
import de.memet.nckreis.core.GeometrieFehler
import de.memet.nckreis.core.Kante
import de.memet.nckreis.core.KantenEingabe
import de.memet.nckreis.core.KantenErgebnis
import de.memet.nckreis.core.NcProgramm
import de.memet.nckreis.core.P
import kotlin.math.tan

private data class Feld(val r: String, val titel: String, val hilfe: String)

private val FELDER = listOf(
    Feld("R30", "Zylinder-Ø", "Durchmesser des Zylinders vor der Kante (X-Wert)."),
    Feld("R31", "Z scharfe Ecke", "Z, wo Zylinder und Schräge ohne Radius zusammentreffen (aus der Zeichnung)."),
    Feld("R32", "Z Planfläche", "Z der Planfläche am Ende der Schräge."),
    Feld("R33", "Winkel Schräge in °", "Winkel der Schräge zur Z-Achse (zum Zylinder), zwischen 0 und 90."),
    Feld("R34", "Radius Ecke Zylinder", "Radius an der Ecke Zylinder → Schräge."),
    Feld("R35", "Radius Ecke Planfläche", "Radius an der Ecke Schräge → Planfläche."),
)

private val BEISPIEL = KantenEingabe(87.98, -4.0, 0.0, 10.0, 2.0, 1.0)

private fun zahlText(v: Double) = Fmt.zahl(v)
private fun lies(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

/**
 * Assistent wie Zyklus L170: Werte aus der Zeichnung eingeben, Tangentenpunkte und I/K werden berechnet.
 * Mit „Schritt für Schritt“ wird jeder Wert einzeln abgefragt – wie der Dialog an der Steuerung.
 */
@Composable
fun KantenEditor(
    vorschlag: KantenEingabe?,
    uebernehmenText: String,
    onUebernehmen: (KantenErgebnis) -> Unit,
    modifier: Modifier = Modifier,
) {
    val start = vorschlag ?: BEISPIEL
    val werte = remember(vorschlag) {
        mutableStateListOf(
            zahlText(start.durchmesser), zahlText(start.zEcke), zahlText(start.zPlan),
            zahlText(start.winkel), zahlText(start.radiusZylinder), zahlText(start.radiusPlan),
        )
    }
    var aktiv by remember { mutableIntStateOf(-1) }
    var schrittModus by remember { mutableStateOf(vorschlag == null) }
    var schritt by remember { mutableIntStateOf(0) }
    val clipboard = LocalClipboardManager.current

    val zahlen = werte.map { lies(it) }
    val eingabe = if (zahlen.all { it != null }) {
        val z = zahlen.map { it!! }
        KantenEingabe(z[0], z[1], z[2], z[3], z[4], z[5])
    } else null
    var fehler: String? = null
    val ergebnis: KantenErgebnis? = eingabe?.let {
        try { Kante.berechne(it) } catch (e: GeometrieFehler) { fehler = e.message; null }
    }
    if (eingabe == null) fehler = "Bitte alle Werte als Zahl eingeben."

    val hervorIndex = if (schrittModus && schritt < FELDER.size) schritt else aktiv

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = schrittModus, onClick = { schrittModus = true; schritt = 0 }, label = { Text("Schritt für Schritt") })
            FilterChip(selected = !schrittModus, onClick = { schrittModus = false }, label = { Text("Alle Felder") })
        }

        KonturGrafik(
            bahn = ergebnis?.let { bahnVon(it) } ?: emptyList(),
            zeichnung = zeichnungVon(eingabe ?: start),
            punkte = ergebnis?.let {
                listOf(
                    GPunkt(it.start, "Z${Fmt.zahl(it.start.z)}"),
                    GPunkt(it.ende1, "X${Fmt.zahl(it.ende1.x)} Z${Fmt.zahl(it.ende1.z)}"),
                    GPunkt(it.start2, "X${Fmt.zahl(it.start2.x)} Z${Fmt.zahl(it.start2.z)}"),
                    GPunkt(it.ende2, "X${Fmt.zahl(it.ende2.x)}"),
                )
            } ?: emptyList(),
            mitten = ergebnis?.let { listOf(it.mitte1, it.mitte2) } ?: emptyList(),
            hervor = hervorhebung(hervorIndex, eingabe ?: start, ergebnis),
        )

        if (schrittModus && schritt < FELDER.size) {
            val f = FELDER[schritt]
            Text("Schritt ${schritt + 1} von ${FELDER.size}", style = MaterialTheme.typography.labelMedium)
            Text("${f.titel} (${f.r})", style = MaterialTheme.typography.titleLarge)
            Text(f.hilfe, style = MaterialTheme.typography.bodyMedium)
            ZahlFeld(werte[schritt], { werte[schritt] = it }, "${f.r} =", Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { if (schritt > 0) schritt-- }, enabled = schritt > 0) { Text("Zurück") }
                Button(onClick = { schritt++ }, enabled = zahlen[schritt] != null) {
                    Text(if (schritt == FELDER.size - 1) "Berechnen" else "Weiter")
                }
            }
        } else {
            FELDER.forEachIndexed { i, f ->
                ZahlFeld(
                    werte[i], { werte[i] = it }, "${f.titel} (${f.r})",
                    Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) aktiv = i },
                )
            }
            if (schrittModus) TextButton(onClick = { schritt = 0 }) { Text("Nochmal Schritt für Schritt") }
        }

        if (!schrittModus || schritt >= FELDER.size) {
            if (fehler != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(fehler!!, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            ergebnis?.let { erg ->
                Ergebnis(erg)
                val block = NcProgramm.ncBlock(erg)
                val aufruf = NcProgramm.l170Aufruf(erg.eingabe)
                CodeKarte("NC-Sätze mit festen Werten", block) { clipboard.setText(AnnotatedString(block)) }
                CodeKarte("Oder an der Maschine mit L170", aufruf) { clipboard.setText(AnnotatedString(aufruf)) }
                Button(onClick = { onUebernehmen(erg) }, modifier = Modifier.fillMaxWidth()) { Text(uebernehmenText) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ZahlFeld(wert: String, onWert: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = wert,
        onValueChange = onWert,
        label = { Text(label) },
        singleLine = true,
        isError = lies(wert) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        modifier = modifier,
    )
}

@Composable
private fun Ergebnis(e: KantenErgebnis) {
    val (d1, d2) = Kante.radiusAbweichungGerundet(e)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Ergebnis (wie L170 in R55…R65)", style = MaterialTheme.typography.titleSmall)
            e.rParameter().chunked(2).forEach { paar ->
                Row {
                    paar.forEach { (n, v) ->
                        Text("$n = ${Fmt.zahl(v)}", fontFamily = FontFamily.Monospace, modifier = Modifier.width(150.dp))
                    }
                }
            }
            val ok = d1 <= NcProgramm.TOLERANZ && d2 <= NcProgramm.TOLERANZ
            Text(
                "Kreisprüfung (3 Nachkommastellen): ΔR1 ${"%.4f".format(d1)} · ΔR2 ${"%.4f".format(d2)} " +
                    if (ok) "✓" else "✗",
                color = if (ok) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                fontWeight = FontWeight.Bold,
            )
            Text("Kreisrichtung: ${e.gKreis}  ·  Seite R36 = ${e.eingabe.seite}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun CodeKarte(titel: String, code: String, onKopieren: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(titel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(top = 12.dp))
                TextButton(onClick = onKopieren) { Text("Kopieren") }
            }
            Text(code, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

fun bahnVon(e: KantenErgebnis): List<GSeg> {
    val cw = e.eingabe.seite > 0
    val s = e.eingabe.seite
    val anfahrt = P(e.start.z - s * 2 * (e.ende1.z - e.start.z).let { kotlin.math.abs(it) + 0.5 }, e.start.r)
    val unten = P(e.ende2.z, e.ende2.r - 1.0)
    return listOf(
        GSeg(anfahrt, e.start),
        GSeg(e.start, e.ende1, e.mitte1, cw),
        GSeg(e.ende1, e.start2),
        GSeg(e.start2, e.ende2, e.mitte2, cw),
        GSeg(e.ende2, unten),
    )
}

/** Scharfe Kontur aus der Zeichnung: Zylinder – Ecke – Schräge – Ecke – Planfläche. */
fun zeichnungVon(e: KantenEingabe): List<P> {
    val r0 = e.durchmesser / 2
    val s = e.seite
    val ta = tan(Math.toRadians(e.winkel.coerceIn(0.1, 89.9)))
    val rB = r0 - kotlin.math.abs(e.zPlan - e.zEcke) * ta
    val laenge = kotlin.math.abs(e.zPlan - e.zEcke).coerceAtLeast(1.0)
    return listOf(
        P(e.zEcke - s * laenge * 0.6, r0),
        P(e.zEcke, r0),
        P(e.zPlan, rB),
        P(e.zPlan, rB - laenge * 0.5),
    )
}

private fun hervorhebung(i: Int, e: KantenEingabe, erg: KantenErgebnis?): GHervor? {
    val z = zeichnungVon(e)
    return when (i) {
        0 -> GHervor(linien = listOf(z[0] to z[1]), text = "Ø Zylinder")
        1 -> GHervor(punkt = z[1], text = "Z scharfe Ecke")
        2 -> GHervor(linien = listOf(z[2] to z[3]), text = "Z Planfläche")
        3 -> GHervor(linien = listOf(z[1] to z[2]), text = "Winkel der Schräge")
        4 -> GHervor(bogen = erg?.let { GSeg(it.start, it.ende1, it.mitte1, e.seite > 0) }, punkt = if (erg == null) z[1] else null, text = "Radius Ecke Zylinder")
        5 -> GHervor(bogen = erg?.let { GSeg(it.start2, it.ende2, it.mitte2, e.seite > 0) }, punkt = if (erg == null) z[2] else null, text = "Radius Ecke Planfläche")
        else -> null
    }
}

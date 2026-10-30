package de.memet.nckreis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.memet.nckreis.core.Art
import de.memet.nckreis.core.Fmt
import de.memet.nckreis.core.Muster
import de.memet.nckreis.core.NcProgramm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeScreen(
    code: String,
    onCode: (String) -> Unit,
    leseLaeuft: Boolean,
    meldung: String?,
    onFoto: () -> Unit,
    onBild: () -> Unit,
    onLens: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val programm = remember(code) { NcProgramm(code.lines()) }
    var dialogMuster by remember { mutableStateOf<Muster?>(null) }
    var hinweis by remember { mutableStateOf<String?>(null) }
    var vorher by remember { mutableStateOf<String?>(null) }
    var geaendert by remember { mutableStateOf<List<String>>(emptyList()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onFoto, modifier = Modifier.weight(1f)) { Text("Foto") }
            OutlinedButton(onClick = onBild, modifier = Modifier.weight(1f)) { Text("Bild") }
            OutlinedButton(onClick = onLens, modifier = Modifier.weight(1f)) { Text("Lens") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { clipboard.getText()?.text?.let { onCode(NcProgramm.ocrBereinigen(it)) } },
                modifier = Modifier.weight(1f),
            ) { Text("Einfügen") }
            OutlinedButton(onClick = { onCode(NcProgramm.ocrBereinigen(code)) }, modifier = Modifier.weight(1f)) { Text("Bereinigen") }
            OutlinedButton(onClick = { vorher = code; onCode(""); geaendert = emptyList() }, modifier = Modifier.weight(1f)) { Text("Leeren") }
        }
        if (leseLaeuft) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.height(20.dp)); Text("Texterkennung läuft …")
        }
        meldung?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        OutlinedTextField(
            value = code,
            onValueChange = onCode,
            label = { Text("NC-Code (Lesefehler hier korrigieren)") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
        )

        Button(
            onClick = {
                val m = programm.findeMuster()
                if (m == null) hinweis = "Keine Kante „Kreis – Gerade – Kreis“ mit bekanntem Startpunkt gefunden. " +
                    "Prüfe, ob X/Z vor dem ersten Kreis im Code stehen, oder nutze den Assistenten."
                else { hinweis = null; dialogMuster = m }
            },
            enabled = code.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Berechnen") }
        hinweis?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (geaendert.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0x332E7D32))) {
                Column(Modifier.padding(12.dp)) {
                    Text("Geänderte Sätze", fontWeight = FontWeight.Bold)
                    geaendert.forEach { Text(it, fontFamily = FontFamily.Monospace) }
                    vorher?.let { alt ->
                        TextButton(onClick = { onCode(alt); vorher = null; geaendert = emptyList() }) { Text("Rückgängig") }
                    }
                }
            }
        }

        if (programm.fahrsaetze.isNotEmpty()) {
            Text("Grafik", style = MaterialTheme.typography.titleMedium)
            KonturGrafik(
                bahn = programm.fahrsaetze.mapNotNull { f ->
                    val a = f.start ?: return@mapNotNull null
                    val b = f.ende ?: return@mapNotNull null
                    GSeg(a, b, f.mitte, f.art == Art.G02, f.fehlerhaft, f.art == Art.G00)
                },
                mitten = programm.kreise.mapNotNull { it.mitte },
                punkte = programm.kreise.filter { it.fehlerhaft }.mapNotNull { k -> k.ende?.let { GPunkt(it, "Zeile ${k.zeile + 1}") } },
            )
        }

        if (programm.kreise.isNotEmpty()) {
            Text("Kreisprüfung", style = MaterialTheme.typography.titleMedium)
            programm.kreise.forEach { k ->
                val r = k.radien
                val zeile = programm.zeilen[k.zeile].trim().take(48)
                val text = if (r == null) "Startpunkt unbekannt – nicht prüfbar"
                else "R Start ${"%.4f".format(r.first)} · R Ende ${"%.4f".format(r.second)} · ΔR ${"%.4f".format(k.deltaR)}"
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            r == null -> MaterialTheme.colorScheme.surfaceVariant
                            k.fehlerhaft -> MaterialTheme.colorScheme.errorContainer
                            else -> Color(0x332E7D32)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Zeile ${k.zeile + 1}: $zeile", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        Text(
                            (if (r != null && k.fehlerhaft) "✗ Kreisendpunktfehler – " else if (r != null) "✓ " else "") + text,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        programm.hinweise.distinct().forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(40.dp))
    }

    dialogMuster?.let { m ->
        Dialog(onDismissRequest = { dialogMuster = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                Column {
                    TopAppBar(
                        title = { Text("Kante aus Zeichnung") },
                        actions = { TextButton(onClick = { dialogMuster = null }) { Text("Abbrechen") } },
                    )
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                        Text(
                            "Vorschlag aus deinem Code (Zeilen ${m.kreis1.zeile + 1}–${m.kreis2.zeile + 1}). " +
                                "Bitte mit der Zeichnung vergleichen und bei Bedarf korrigieren.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        KantenEditor(
                            vorschlag = programm.eingabeAus(m),
                            uebernehmenText = "In den Code übernehmen",
                            onUebernehmen = { erg ->
                                val (neu, zeilen) = programm.ersetze(m, erg)
                                vorher = code
                                val neueZeilen = neu.lines()
                                geaendert = zeilen.sorted().map { neueZeilen[it] }
                                onCode(neu)
                                dialogMuster = null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Suppress("unused")
private fun f(v: Double) = Fmt.zahl(v)

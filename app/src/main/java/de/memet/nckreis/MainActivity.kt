package de.memet.nckreis

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import de.memet.nckreis.core.NcProgramm
import de.memet.nckreis.ocr.TextErkennung
import de.memet.nckreis.ui.CodeScreen
import de.memet.nckreis.ui.KantenEditor
import java.io.File

class MainActivity : ComponentActivity() {

    /** Von Google Lens o.ä. geteilter Text oder geteiltes Bild. */
    private var geteilt by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) geteilt = intent
        setContent { Thema { App(geteilt) { geteilt = null } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        geteilt = intent
    }
}

@Composable
private fun Thema(inhalt: @Composable () -> Unit) {
    val dunkel = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val farben = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dunkel) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dunkel -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = farben, content = inhalt)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(geteilt: Intent?, geteiltErledigt: () -> Unit) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var code by rememberSaveable { mutableStateOf("") }
    var leseLaeuft by rememberSaveable { mutableStateOf(false) }
    var meldung by rememberSaveable { mutableStateOf<String?>(null) }

    fun erkenne(uri: Uri) {
        leseLaeuft = true
        meldung = null
        TextErkennung.lies(ctx, uri) { erg ->
            leseLaeuft = false
            erg.onSuccess {
                code = NcProgramm.ocrBereinigen(it)
                tab = 0
                meldung = "Text erkannt. Bitte mit dem Foto vergleichen – besonders Minuszeichen und Punkte."
            }.onFailure {
                meldung = if (it.message?.contains("download", ignoreCase = true) == true)
                    "Die Texterkennung wird gerade von den Google Play-Diensten geladen. Kurz warten (Internet nötig) und nochmal versuchen."
                else "Texterkennung fehlgeschlagen: ${it.message}"
            }
        }
    }

    // Geteilte Inhalte (z.B. Google Lens → Teilen → NC Kreis)
    LaunchedEffect(geteilt) {
        val i = geteilt ?: return@LaunchedEffect
        if (i.action == Intent.ACTION_SEND) {
            val text = i.getStringExtra(Intent.EXTRA_TEXT)
            val bild = IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
            when {
                !text.isNullOrBlank() -> { code = NcProgramm.ocrBereinigen(text); tab = 0; meldung = "Text übernommen." }
                bild != null -> erkenne(bild)
            }
        }
        geteiltErledigt()
    }

    val fotoDatei = remember(ctx) {
        File(ctx.cacheDir, "fotos").apply { mkdirs() }.let { File(it, "nc_foto.jpg") }
    }
    val fotoUri = remember(fotoDatei) { FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", fotoDatei) }
    val foto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) erkenne(fotoUri) }
    val bildWahl = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { erkenne(it) } }

    Scaffold(
        topBar = { TopAppBar(title = { Text(if (tab == 0) "NC Kreis – Code" else "NC Kreis – Assistent L170") }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Edit, null) }, label = { Text("Code") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.Build, null) }, label = { Text("Assistent") })
            }
        },
    ) { innen ->
        val m = Modifier.padding(innen).fillMaxSize()
        if (tab == 0) {
            androidx.compose.foundation.layout.Box(m) {
                CodeScreen(
                    code = code,
                    onCode = { code = it },
                    leseLaeuft = leseLaeuft,
                    meldung = meldung,
                    onFoto = {
                        try { foto.launch(fotoUri) } catch (e: ActivityNotFoundException) { toast(ctx, "Keine Kamera-App gefunden.") }
                    },
                    onBild = { bildWahl.launch("image/*") },
                    onLens = { oeffneLens(ctx) },
                )
            }
        } else {
            Column(m.verticalScroll(rememberScrollState()).padding(16.dp)) {
                KantenEditor(
                    vorschlag = null,
                    uebernehmenText = "Sätze an den Code anhängen",
                    onUebernehmen = { erg ->
                        val block = NcProgramm.ncBlock(erg)
                        code = if (code.isBlank()) block else code.trimEnd() + "\n" + block
                        tab = 0
                    },
                )
            }
        }
    }
}

private fun toast(ctx: android.content.Context, text: String) = Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()

/** Google Lens öffnen. Dort Text markieren → „Teilen“ → NC Kreis, oder „Kopieren“ und hier „Einfügen“. */
private fun oeffneLens(ctx: android.content.Context) {
    val pm = ctx.packageManager
    val lensApp = pm.getLaunchIntentForPackage("com.google.ar.lens")
    val versuche = listOfNotNull(
        lensApp,
        Intent(Intent.ACTION_VIEW, Uri.parse("googleapp://lens")).setPackage("com.google.android.googlequicksearchbox"),
    )
    for (i in versuche) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            toast(ctx, "In Lens: Text markieren → Teilen → NC Kreis (oder Kopieren → hier Einfügen)")
            return
        } catch (_: Exception) { }
    }
    toast(ctx, "Google Lens nicht gefunden. Nutze „Foto“ – das liest den Text direkt.")
}

package de.memet.nckreis.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.abs

/** Texterkennung auf dem Handy (Google ML Kit, offline – dieselbe Technik wie Google Lens). */
object TextErkennung {

    fun lies(context: Context, bild: Uri, fertig: (Result<String>) -> Unit) {
        val eingabe = try {
            InputImage.fromFilePath(context, bild)
        } catch (e: Exception) {
            fertig(Result.failure(e)); return
        }
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(eingabe)
            .addOnSuccessListener { fertig(Result.success(zeilenSortiert(it))) }
            .addOnFailureListener { fertig(Result.failure(it)) }
    }

    /**
     * Am Bildschirm der Steuerung stehen Wörter oft in Spalten. ML Kit liefert sie als getrennte Blöcke.
     * Hier werden alle erkannten Zeilen nach ihrer Höhe im Bild zu echten Programmzeilen zusammengesetzt.
     */
    private fun zeilenSortiert(text: Text): String {
        data class Stueck(val oben: Int, val mitte: Int, val hoehe: Int, val links: Int, val inhalt: String)
        val stuecke = text.textBlocks.flatMap { it.lines }.mapNotNull { z ->
            val b = z.boundingBox ?: return@mapNotNull null
            Stueck(b.top, b.centerY(), b.height().coerceAtLeast(1), b.left, z.text)
        }
        if (stuecke.isEmpty()) return text.text
        val reihen = mutableListOf<MutableList<Stueck>>()
        for (s in stuecke.sortedBy { it.mitte }) {
            val reihe = reihen.lastOrNull()
            if (reihe != null) {
                val mitteReihe = reihe.map { it.mitte }.average()
                val hoehe = reihe.map { it.hoehe }.average()
                if (abs(s.mitte - mitteReihe) < hoehe * 0.55) { reihe.add(s); continue }
            }
            reihen.add(mutableListOf(s))
        }
        return reihen.joinToString("\n") { r -> r.sortedBy { it.links }.joinToString(" ") { it.inhalt } }
    }
}

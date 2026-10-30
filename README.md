# NC Kreis

Android-App für Kreisendpunkte an Kanten (Zylinder – Radius – Schräge – Radius – Planfläche),
passend zum Zyklus **L170** im Repo `studer-l905-zyklus`.

## Herunterladen
Unter **Releases → „neueste“** liegt immer die aktuelle `NC-Kreis.apk`. Jeder Push auf `main` baut sie neu.
Alle Versionen sind mit demselben Schlüssel signiert und lassen sich direkt über die alte installieren.

## Tab „Code“
1. NC-Code holen:
   - **Foto** – Bildschirm der Steuerung fotografieren, Texterkennung über die Google Play-Dienste (wie Lens; beim allerersten Mal wird sie kurz geladen).
   - **Bild** – vorhandenes Foto auswählen.
   - **Google Lens** – in Lens Text markieren → *Teilen* → **NC Kreis**, oder *Kopieren* → hier **Einfügen**.
     Ein geteiltes Bild wird ebenfalls direkt gelesen.
2. Lesefehler im Textfeld korrigieren. Die App prüft jeden G02/G03 sofort:
   Radius Start→Mitte und Ende→Mitte, Warnung ab ΔR > 0,001 (Ursache von Alarm 308).
3. **Berechnen** – sucht die Kante *Kreis – Gerade – Kreis*, schlägt die Zeichnungswerte vor
   (Ø, Z Ecke, Z Planfläche, Winkel, R34, R35). Nach Kontrolle mit der Zeichnung werden
   X/Z/I/K in den Sätzen ersetzt, auch das Z des Anfahrsatzes. „Rückgängig“ ist möglich.

## Tab „Assistent“
Wie der Dialog von L170: Werte Schritt für Schritt eingeben, die Grafik zeigt jeweils, welches
Maß gemeint ist. Ergebnis als R55…R65, als fertige NC-Sätze und als L170-Aufruf.

## Rechnung
- I = Radius (nicht Durchmesser), K relativ zum Kreisanfang, X im Durchmesser.
- Planfläche bei größerem Z → G02 (R36 = 1), gespiegelt → G03 (R36 = -1).
- Testfall (läuft an der Maschine ohne Alarm):
  Ø87.98, Ecke Z-4, Planfläche Z0, 10°, R2/R1 →
  `G02 X87.919 Z-3.828 I-2 K0` / `G01 X86.861 Z-0.826` / `G02 X84.891 Z0 I-0.985 K-0.174`

## Selbst bauen
Android Studio öffnen oder `./gradlew assembleRelease`.

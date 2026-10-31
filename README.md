# NC Kreis

Web-App für Kreisendpunkte an Kanten (Zylinder – Radius – Schräge – Radius – Planfläche).

**➜ https://memet981.github.io/nc-kreis-app/**

Läuft im Browser auf Android und iPhone, ohne Installation. Gerechnet wird nur auf dem Handy,
es wird nichts an einen Server geschickt.

## Auf dem Handy wie eine App
Link öffnen, dann:
- **Android (Chrome):** Menü ⋮ → **Zum Startbildschirm hinzufügen** (oder „App installieren“).
- **iPhone (Safari):** Teilen-Knopf → **Zum Home-Bildschirm**.

Danach startet NC Kreis vom Startbildschirm und geht auch **ohne Internet**.
Neue Versionen lädt die App im Hintergrund – sie sind beim nächsten Öffnen da.

## Tab „Code“
1. NC-Code holen: **Google Lens** öffnen (am iPhone die Kamera), Bildschirm der Steuerung
   fotografieren → Text markieren → **Kopieren** → in NC Kreis **Einfügen**.
   Der Text wird dabei automatisch bereinigt (Komma → Punkt, O → 0, Leerzeichen nach X/Z,
   leere Zeilen, I/Z/G als 1/2/6 gelesen, griechisches Κ statt K …).
2. Lesefehler im Textfeld korrigieren. Die App prüft jeden G02/G03 sofort:
   Radius Start→Mitte und Ende→Mitte, Warnung ab ΔR > 0,001 (Ursache von Alarm 308).
3. **Berechnen** – sucht die Kante *Kreis – Gerade – Kreis*, schlägt die Zeichnungswerte vor
   (Ø, Z Ecke, Z Planfläche, Winkel, beide Radien). Nach Kontrolle mit der Zeichnung werden
   X/Z/I/K in den Sätzen ersetzt, auch das Z des Anfahrsatzes. „Rückgängig“ ist möglich.

## Tab „Zeichnung“
Werte aus der Zeichnung eingeben. Beim Antippen eines Feldes zeigt die Grafik, welches
Maß gemeint ist. Ergebnis als fertige NC-Sätze mit Kreisprüfung.
Die **±**-Taste neben jedem Feld dreht das Vorzeichen (die iPhone-Zahlentastatur hat kein Minus).

## Rechnung
- I = Radius (nicht Durchmesser), K relativ zum Kreisanfang, X im Durchmesser.
- Planfläche bei größerem Z → G02, gespiegelt → G03.
- Testfall (läuft an der Maschine ohne Alarm):
  Ø87.98, Ecke Z-4, Planfläche Z0, 10°, R2/R1 →
  `G02 X87.919 Z-3.828 I-2 K0` / `G01 X86.861 Z-0.826` / `G02 X84.891 Z0 I-0.985 K-0.174`

## Scharfe Ecke
Radius 0 bedeutet: an dieser Ecke kein Radius. Der Kreissatz entfällt, gefahren wird
mit G01 direkt bis zur Ecke. Steht im Code eine scharfe Ecke und die Zeichnung hat einen Radius,
fügt „Berechnen“ den fehlenden Kreissatz ein.

## Aufbau
- `web/index.html` – die ganze App in einer Datei. Die Rechnung steht in `<script id="rechnung">`.
  Die Datei läuft auch allein (z. B. per E-Mail verschickt), dann aber ohne Offline-Speicher.
- `web/sw.js`, `web/manifest.webmanifest`, Icons – für „Zum Startbildschirm“ und offline.
- `test/rechnung.test.mjs` – Tests der Rechnung: `node --test test/`
- Jeder Push auf `main` lässt die Tests laufen und veröffentlicht `web/` auf GitHub Pages.

Zum lokalen Ausprobieren:
`python3 -m http.server 8765 --directory web` → http://localhost:8765

## Frühere Android-App
Die Kotlin-App (mit Foto-Texterkennung) ist im Tag `android-letzte-version` erhalten.

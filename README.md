# NC Kreis

Web-App für Kreisendpunkte an Konturen (Kante, Hohlkehle, Ballig, Stufe …).

**Für Studer-Schleifmaschinen mit Siemens SINUMERIK 3G.** Die App zeigt das beim ersten Start
einmal an. Andere Steuerungen können I/K anders rechnen.

**➜ https://memet981.github.io/nc-kreis-app/**

Läuft im Browser auf Android und iPhone, ohne Installation. Gerechnet wird nur auf dem Handy,
es wird nichts an einen Server geschickt.
Am PC und Tablet quer stehen Grafik, Felder und NC-Sätze nebeneinander. Am Handy steht alles untereinander, auch wenn es quer gehalten wird.

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

## Tab „Konturformen“
Die App öffnet mit diesem Tab. Zuerst die Form antippen. Jede Form gibt es zweimal: **links** fährt in Z−, **rechts** fährt in Z+
(G02/G03 steht auf der Kachel). Die Vorgaben kommen aus echten Studer-Programmen:

| Form | Kontur | Vorgabe aus |
|---|---|---|
| Kante | Zylinder – Radius – Schräge – Radius – Planfläche | Beispiel 87.98, %6357 |
| Hohlkehle | Fase – Schulter – Radius – Zylinder – Fase | %6251, %4214 |
| Hohlkehle mit Flanke | Schulter – Radius – Zylinder (Breite) – Kante – schräge Flanke (Winkel zur X-Achse) bis Ø Ende | Skizze Ø60, R0.5, 0.2×45°, 15° |
| Schräge Schulter | Schräge – Radius – Zylinder – Fase (90° = gerade Schulter) | %4016 |
| Radius am Ende | Zylinder – Radius – Stirnseite | %4202 |
| Flaschenhals | Schräge 1 – Radius 1 – Schräge 2 – Radius 2 – Zylinder – Fase | %6432 |
| Ballig | großer Radius von Z Mitte, dann Radius an der Stirnseite (0 = keiner) | %6305 |
| Stufe | zwei Absätze mit Fase und Radius, dann Zylinder – Fase | %5636 |

Dann die Werte aus der Zeichnung eingeben. Beim Antippen eines Feldes zeigt die Grafik, welches
Maß gemeint ist. Ergebnis als fertige NC-Sätze mit Kreisprüfung.
Die **±**-Taste neben jedem Feld dreht das Vorzeichen (die iPhone-Zahlentastatur hat kein Minus).
- Fasen: Länge in Z, dazu ein Fasenwinkel zur Z-Achse (meist 45°, manchmal 30°).
- Hohlkehle mit Flanke: Breite = Planfläche bis zur scharfen Ecke Zylinder/Flanke. Die Kante beginnt um ihre Länge vor dieser Ecke auf dem Zylinder und endet auf der Flanke.
- Auslauf: Die letzte Linie (Fase oder Zylinder) läuft um diesen Wert über Z Ende hinaus.
- „‹ Alle Formen“ oder nochmal auf „Konturformen“ tippen führt zurück zur Auswahl.

„Berechnen“ im Tab „Code“ erkennt nur die Kante (Kreis – Gerade – Kreis).

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

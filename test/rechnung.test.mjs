// Gleiche Fälle wie KanteTest.kt der früheren Android-App.
// Geprüft wird genau das <script id="rechnung"> aus web/index.html.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const html = readFileSync(new URL("../web/index.html", import.meta.url), "utf8");
const skript = html.match(/<script id="rechnung">([\s\S]*?)<\/script>/)[1];
const { Kante, KantenEingabe, NcProgramm, Kontur, P } = vm.runInNewContext(
  skript + "\n;({ Kante, KantenEingabe, NcProgramm, Kontur, P })", {});

const zeilen = text => text.split("\n");
const nah = (soll, ist, toleranz) => assert.ok(Math.abs(soll - ist) <= toleranz, `${ist} ist nicht ${soll} ± ${toleranz}`);

/** Werte, die am 29.10. an der Maschine ohne Alarm 308 liefen. */
const maschine = [
  "G01 X87.98 Z-4.175",
  "G02 X87.919 Z-3.828 I-2 K0",
  "G01 X86.861 Z-0.826",
  "G02 X84.891 Z0 I-0.985 K-0.174",
].join("\n");

test("Zeichnung ergibt Maschinenwerte", () => {
  const e = Kante.berechne(new KantenEingabe(87.98, -4.0, 0.0, 10.0, 2.0, 1.0));
  assert.equal(NcProgramm.ncBlock(e), maschine);
  const [d1, d2] = Kante.radiusAbweichungGerundet(e);
  assert.ok(d1 < 0.001 && d2 < 0.001);
});

test("fehlerhafter Code wird korrigiert", () => {
  const falsch = [
    "N120 G00 X88.2 Z-4.18",
    "N130 G01 X87.98",
    "N140 G02 G64 X87.92 Z-3.81 I-2 K0",
    "N150 G01 G64 G08 X86.86 Z-0.83 F=2.8",
    "N160 G02 G64 X84.891 Z0 I-0.985 K-0.174",
  ].join("\n");
  const p = new NcProgramm(zeilen(falsch));
  assert.ok(p.kreise[0].fehlerhaft);
  const m = p.findeMuster();
  const [neu] = p.ersetze(m, Kante.berechne(p.eingabeAus(m)));
  const z = zeilen(neu);
  assert.equal(z[0], "N120 G00 X88.2 Z-4.175");
  assert.equal(z[2], "N140 G02 G64 X87.919 Z-3.828 I-2 K0");
  assert.equal(z[3], "N150 G01 G64 G08 X86.861 Z-0.826 F=2.8");
  assert.equal(z[4], "N160 G02 G64 X84.891 Z0 I-0.985 K-0.174");
  assert.ok(new NcProgramm(z).kreise.every(k => !k.fehlerhaft));
});

test("gespiegelte Kante", () => {
  const e = Kante.berechne(new KantenEingabe(87.98, 4.0, 0.0, 10.0, 2.0, 1.0));
  assert.equal(e.gKreis, "G03");
  nah(0.826, e.start2.z, 0.0006);
});

test("scharfe Ecke am Zylinder (Radius 0)", () => {
  const e = Kante.berechne(new KantenEingabe(87.98, -4.0, 0.0, 10.0, 0.0, 1.0));
  assert.equal(NcProgramm.ncBlock(e), "G01 X87.98 Z-4\nG01 X86.861 Z-0.826\nG02 X84.891 Z0 I-0.985 K-0.174");
});

test("Kreis wird zu scharfer Ecke", () => {
  const code = [
    "N120 G00 X88.2 Z-4.175",
    "N130 G01 X87.98",
    "N140 G02 G64 X87.919 Z-3.828 I-2 K0",
    "N150 G01 G64 G08 X86.861 Z-0.826 F=2.8",
    "N160 G02 G64 X84.891 Z0 I-0.985 K-0.174",
  ].join("\n");
  const p = new NcProgramm(zeilen(code));
  const m = p.findeMuster();
  const [neu] = p.ersetze(m, Kante.berechne(p.eingabeAus(m).kopie({ radiusZylinder: 0 })));
  const z = zeilen(neu);
  assert.equal(z[0], "N120 G00 X88.2 Z-4.175");
  assert.equal(z[2], "N140 G01 G64 X87.98 Z-4");
  assert.equal(z[3], "N150 G01 G64 G08 X86.861 Z-0.826 F=2.8");
});

test("scharfe Ecke im Code bekommt Radius", () => {
  const code = [
    "N120 G00 X88.2 Z-6",
    "N130 G01 X87.98",
    "N140 G01 Z-4",
    "N150 X86.861 Z-0.826",
    "N160 G02 X84.891 Z0 I-0.985 K-0.174",
  ].join("\n");
  const p = new NcProgramm(zeilen(code));
  const m = p.findeMuster();
  const ein = p.eingabeAus(m);
  assert.equal(ein.radiusZylinder, 0);
  nah(-4.0, ein.zEcke, 0.0005);
  const [neu, geaendert] = p.ersetze(m, Kante.berechne(ein.kopie({ radiusZylinder: 2.0 })));
  const z = zeilen(neu);
  assert.equal(z[2], "N140 G01 X87.98 Z-4.175");
  assert.equal(z[3], "G02 X87.919 Z-3.828 I-2 K0");
  assert.equal(z[4], "N150 G01 X86.861 Z-0.826");
  assert.ok(geaendert.has(3) && geaendert.has(4));
  assert.ok(new NcProgramm(z).kreise.every(k => !k.fehlerhaft));
});

test("Lesefehler werden bereinigt", () => {
  assert.equal(NcProgramm.ocrBereinigen("n140 g02 x 87,919 z– 3.828 i-2 kO"), "N140 G02 X87.919 Z-3.828 I-2 K0");
  assert.equal(NcProgramm.ocrBereinigen("X87.9L9"), "X87.919");
  assert.equal(NcProgramm.ocrBereinigen("[SER]\n\nL901\n  \nN80 G00 X50\n\n"), "[SER]\nL901\nN80 G00 X50");
});

test("I als 1, l oder | gelesen", () => {
  const ocr = NcProgramm.ocrBereinigen;
  assert.equal(ocr("N180 G03 G64 X36.75 Z-9. 10. K0.7"), "N180 G03 G64 X36.75 Z-9. I0. K0.7");
  assert.equal(ocr("N140 G02 X87.919 Z-3.828 1-2 K0"), "N140 G02 X87.919 Z-3.828 I-2 K0");
  assert.equal(ocr("N160 G02 X84.891 Z0 l-0,985 K-0.174"), "N160 G02 X84.891 Z0 I-0.985 K-0.174");
  assert.equal(ocr("N160 G02 X84.891 Z0 |-0.985 K-0.174"), "N160 G02 X84.891 Z0 I-0.985 K-0.174");
  // Kein Kreissatz, schon ein I da, oder Zahl am Zeilenanfang: nichts ändern
  assert.equal(ocr("N120 G01 X42.8 10."), "N120 G01 X42.8 10.");
  assert.equal(ocr("G03 X36.75 Z-9 I0 K0.7 10"), "G03 X36.75 Z-9 I0 K0.7 10");
  assert.equal(ocr("180 G03 X36.75 Z-9 K0.7"), "180 G03 X36.75 Z-9 K0.7");
  const p = new NcProgramm(zeilen(ocr("G01 X38.15 Z-9.7\nG03 X36.75 Z-9. 1-0.7 K0")));
  assert.equal(p.kreise[0].i, -0.7);
});

test("Lesefehler aus Lens-Foto vom 31.10.", () => {
  const ocr = NcProgramm.ocrBereinigen;
  // K als griechisches Kappa, G als 6
  assert.equal(ocr("N180 G03 664 X36.75 Z-9. I0. Κ0.7"), "N180 G03 G64 X36.75 Z-9. I0. K0.7");
  assert.equal(ocr("N180 G03 664 X36.75 Z-9. I0. К0.7"), "N180 G03 G64 X36.75 Z-9. I0. K0.7"); // kyrillisch
  assert.equal(ocr("N140 G01 G64 608 X41.15 Z-13.2"), "N140 G01 G64 G08 X41.15 Z-13.2");
  // O statt 0 im G-Wort
  assert.equal(ocr("N210 GOO G05 X50."), "N210 G00 G05 X50.");
  assert.equal(ocr("N80 GO1 X5O"), "N80 G01 X50");
  // Z als 2
  assert.equal(ocr("N200 G01 G64 G08 X35.35 20.3"), "N200 G01 G64 G08 X35.35 Z0.3");
  // Nicht anfassen: Kommentare, Werte nach "=" oder "*", GOTO, Satz mit Z
  assert.equal(ocr("N90 (ANWAHL 2 SCHEIBENKANTE)"), "N90 (ANWAHL 2 SCHEIBENKANTE)");
  assert.equal(ocr("N110 G00 F * 1 = 2.5 X43"), "N110 G00 F * 1 = 2.5 X43");
  assert.equal(ocr("GOTOF MARKE"), "GOTOF MARKE");
  assert.equal(ocr("N120 G01 X42.8 Z-5 20"), "N120 G01 X42.8 Z-5 20");

  const code = ocr([
    "N170 G01 G05 X38.15 Z-9.7",
    "N180 G03 664 X36.75 Z-9. I0. Κ0.7",
    "N190 G01 G05 Z-0.4",
    "N200 G01 G64 G08 X35.35 20.3",
    "N210 GOO G05 X50.",
  ].join("\n"));
  const p = new NcProgramm(zeilen(code));
  assert.equal(p.kreise.length, 1);
  assert.ok(!p.kreise[0].fehlerhaft);
  assert.equal(p.fahrsaetze[3].ende.z, 0.3);
  assert.equal(p.fahrsaetze[4].art, "G00");
});

test("R-Parameter im Code", () => {
  const p = new NcProgramm(zeilen("R10=87.98 R11=-4\nG01 X R10 Z R11"));
  assert.equal(p.rWerte.get(10), 87.98);
  assert.equal(p.fahrsaetze[0].ende.x, 87.98);
  assert.equal(p.fahrsaetze[0].ende.z, -4);
});

// ---------- Weitere Formen: Werte aus echten Studer-Programmen ----------

const satzListe = (...s) => s.join("\n");
const kontur = (form, werte, seite) => Kontur.berechne(Kontur[form](werte, seite));

test("Kante links ergibt %6357", () => {
  const e = Kante.berechne(new KantenEingabe(32.19, -27.78, -28.88, 45, 1.05, 0));
  assert.equal(NcProgramm.ncBlock(e), satzListe("G01 X32.19 Z-27.345", "G03 X31.575 Z-28.088 I-1.05 K0", "G01 X29.99 Z-28.88"));
});

test("Hohlkehle rechts ergibt %6251", () => {
  const e = kontur("hohlkehle", { dS: 90.75, zP: -9.7, R: 0.7, dZ: 86.35, zE: 0, fS: 0.5, fE: 0.4, fw: 45, a: 0.2 }, 1);
  assert.equal(Kontur.ncBlock(e), satzListe(
    "G01 X90.75 Z-10.2", "G01 X89.75 Z-9.7", "G01 X87.75 Z-9.7", "G03 X86.35 Z-9 I0 K0.7",
    "G01 X86.35 Z-0.4", "G01 X85.15 Z0.2"));
});

test("Hohlkehle links mit 30°-Fase ergibt %4214", () => {
  const e = kontur("hohlkehle", { dS: 70, zP: -20.9, R: 0.5, dZ: 66.7, zE: -28.7, fS: 0, fE: 0.6, fw: 30, a: 0 }, -1);
  assert.equal(Kontur.ncBlock(e), satzListe(
    "G01 X70 Z-20.9", "G01 X67.7 Z-20.9", "G02 X66.7 Z-21.4 I0 K-0.5", "G01 X66.7 Z-28.1", "G01 X66.007 Z-28.7"));
});

test("Stufe ergibt %5636", () => {
  const e = kontur("stufe", { dS: 67.075, fS: 0.5, zP1: -20.2, R1: 0.1, dM: 64, fM: 0.5, zP2: -3.125, R2: 0.6,
    dZ: 58.775, zE: 0, fE: 0.5, fw: 45, a: 0.3 }, 1);
  assert.equal(Kontur.ncBlock(e), satzListe(
    "G01 X67.075 Z-20.7", "G01 X66.075 Z-20.2", "G01 X64.2 Z-20.2", "G03 X64 Z-20.1 I0 K0.1",
    "G01 X64 Z-3.625", "G01 X63 Z-3.125", "G01 X59.975 Z-3.125", "G03 X58.775 Z-2.525 I0 K0.6",
    "G01 X58.775 Z-0.5", "G01 X57.175 Z0.3"));
});

test("Ballig mit Radius am Ende ergibt %6305 (beide Kreise hintereinander)", () => {
  const werte = { dM: 45, zM: -16.125, R: 398, R2: 1.15 };
  const rechts = kontur("ballig", { ...werte, zE: 0.05 }, 1);
  assert.equal(Kontur.ncBlock(rechts),
    "G01 X45 Z-16.125\nG02 X44.429 Z-1.056 I-398 K0\nG02 X42.131 Z0.05 I-1.149 K-0.044");
  assert.equal(Kontur.ncBlock(kontur("ballig", { ...werte, zE: -32.3 }, -1)),
    "G01 X45 Z-16.125\nG03 X44.429 Z-31.194 I-398 K0\nG03 X42.131 Z-32.3 I-1.149 K0.044");
  assert.ok(Kontur.radiusAbweichungGerundet(rechts).every(d => d <= 0.001));
  // Tangential: Abstand der Mittelpunkte = R − R2
  const [k1, k2] = rechts.kreise;
  nah(398 - 1.15, Math.hypot(k2.mitte.z - k1.mitte.z, k2.mitte.r - k1.mitte.r), 1e-9);
});

test("Ballig ohne Radius am Ende: ein Bogen wie bisher", () => {
  const werte = { dM: 45, zM: -16.125, R: 398, R2: 0 };
  assert.equal(Kontur.ncBlock(kontur("ballig", { ...werte, zE: 3 }, 1)), "G01 X45 Z-16.125\nG02 X44.08 Z3 I-398 K0");
  assert.equal(Kontur.ncBlock(kontur("ballig", { ...werte, zE: -35.25 }, -1)), "G01 X45 Z-16.125\nG03 X44.08 Z-35.25 I-398 K0");
});

test("Radius am Ende ergibt %4202", () => {
  assert.equal(Kontur.ncBlock(kontur("radiusAmEnde", { dZ: 11.97, zS: 0, R: 0.8 }, 1)), "G01 X11.97 Z-0.8\nG02 X10.37 Z0 I-0.8 K0");
  assert.equal(Kontur.ncBlock(kontur("radiusAmEnde", { dZ: 11.97, zS: -20, R: 0.8 }, -1)), "G01 X11.97 Z-19.2\nG03 X10.37 Z-20 I-0.8 K0");
});

test("Schräge Schulter ergibt %4016", () => {
  // Winkel und Ecke aus den Programmsätzen zurückgerechnet
  const e = kontur("schraegeSchulter", { dS: 26.935, wS: 75.1275, zU: -1.9609, R: 0.3, dZ: 17.6, zE: 0, fE: 0.3, fw: 45, a: 0.2 }, 1);
  const [k] = e.kreise;
  assert.equal(k.art, "G03");
  nah(18.045, k.start.x, 0.002); nah(-2.02, k.start.z, 0.002);
  nah(-1.731, k.ende.z, 0.002); nah(17.6, k.ende.x, 0.0005);
  assert.equal(Kontur.ncBlock(e).split("\n").at(-1), "G01 X16.6 Z0.2");
});

test("Flaschenhals ergibt %6432", () => {
  const werte = { dG: 53.92, zO: -103.28, w1: 40.882, R1: 25.4, zM: -91.075, w2: 5.897, R2: 25.4, dZ: 19.19,
    zE: 0, fE: 1.7, fw: 45, a: 0.3 };
  const e = kontur("flaschenhals", werte, 1);
  const [k1, k2] = e.kreise;
  assert.deepEqual([k1.art, k2.art], ["G03", "G03"]);
  nah(31.144, k1.ende.x, 0.01); nah(-83.113, k1.ende.z, 0.01); nah(19.204, k1.i, 0.01); nah(16.624, k1.k, 0.01);
  nah(19.19, k2.ende.x, 0.0005); nah(-23.942, k2.ende.z, 0.01); nah(25.266, k2.i, 0.01); nah(2.61, k2.k, 0.01);
  assert.ok(Kontur.radiusAbweichungGerundet(e).every(d => d < 0.001));
  // gespiegelt: gleiche Kreise, G02
  const l = kontur("flaschenhals", { ...werte, zO: -6.72, zM: -18.925, zE: -110 }, -1);
  assert.equal(l.kreise.map(k => k.art).join(), "G02,G02");
  nah(k1.i, l.kreise[0].i, 1e-9); nah(-k1.k, l.kreise[0].k, 1e-9);
});

test("Kante: Z Ecke und Z Planfläche gleich verschoben – X bleibt", () => {
  const a = Kante.berechne(new KantenEingabe(87.98, -4, 0, 10, 2, 1));
  const b = Kante.berechne(new KantenEingabe(87.98, -3.9, 0.1, 10, 2, 1));
  for (const p of ["start", "ende1", "start2", "ende2"]) {
    nah(a[p].x, b[p].x, 1e-9);
    nah(a[p].z + 0.1, b[p].z, 1e-9);
  }
  // nur Z Planfläche verschoben: X wandert um 2·0.1·tan 10°
  const c = Kante.berechne(new KantenEingabe(87.98, -4, 0.1, 10, 2, 1));
  nah(a.ende2.x - 0.2 * Math.tan(10 * Math.PI / 180), c.ende2.x, 1e-9);
});

test("Flaschenhals: Z Ecke oben und Z Ecke Mitte gleich verschoben – X bleibt", () => {
  const werte = { dG: 53.92, zO: -103.28, w1: 40.882, R1: 25.4, zM: -91.075, w2: 5.897, R2: 25.4, dZ: 19.19,
    zE: 0, fE: 1.7, fw: 45, a: 0.3 };
  const a = kontur("flaschenhals", werte, 1);
  const b = kontur("flaschenhals", { ...werte, zO: -103.18, zM: -90.975 }, 1);
  assert.equal(a.saetze.length, b.saetze.length);
  a.saetze.forEach((s, i) => nah(s.ende.x, b.saetze[i].ende.x, 1e-9));
  nah(a.kreise[1].ende.z + 0.1, b.kreise[1].ende.z, 1e-9);
});

test("verrunde rechnet wie Kante.berechne", () => {
  const k = Kante.berechne(new KantenEingabe(87.98, -4, 0, 10, 2, 1));
  const v = Kontur.verrunde({ ecken: [new P(-10, 43.99), k.ecke1, k.ecke2, new P(0, k.ecke2.r - 5)], radien: [0, 2, 1, 0] });
  const [b1, b2] = v.kreise;
  for (const [ist, soll] of [[b1.start, k.start], [b1.ende, k.ende1], [b2.start, k.start2], [b2.ende, k.ende2]]) {
    nah(soll.z, ist.z, 1e-9); nah(soll.r, ist.r, 1e-9);
  }
  nah(k.i1, b1.i, 1e-9); nah(k.k1, b1.k, 1e-9); nah(k.i2, b2.i, 1e-9); nah(k.k2, b2.k, 1e-9);
  assert.deepEqual([b1.art, b2.art], ["G02", "G02"]);
});

test("Fehler bei falschen Maßen", () => {
  const hk = { dS: 90.75, zP: -9.7, R: 0.7, dZ: 86.35, zE: 0, fS: 0.5, fE: 0.4, fw: 45, a: 0.2 };
  assert.throws(() => kontur("hohlkehle", { ...hk, R: 20 }, 1), /Radius Hohlkehle zu groß/);
  assert.throws(() => kontur("hohlkehle", hk, -1), /Bei „links“ muss Z Ende kleiner als Z Planfläche sein/);
  assert.throws(() => kontur("ballig", { dM: 45, zM: 0, R: 5, zE: 10, R2: 0 }, 1), /Radius ballig ist kleiner/);
  const ballig = { dM: 45, zM: -16.125, R: 398, zE: 0.05 };
  assert.throws(() => kontur("ballig", { ...ballig, R2: 20 }, 1), /Radius am Ende ist zu groß/);
  assert.throws(() => kontur("ballig", { ...ballig, R2: 400 }, 1), /Radius am Ende muss kleiner/);
  assert.throws(() => kontur("ballig", { ...ballig, R2: -1 }, 1), /nicht negativ/);
});

test("Hohlkehle mit Flanke links und rechts (Skizze Ø60, Breite 5, R0.5, Kante 0.2×45°, Flanke 15°, Tiefe 1 im Ø)", () => {
  const w = { dS: 66, R: 0.5, dZ: 60, b: 5, fK: 0.2, wF: 15, tF: 1, a: 0.2, fS: 0, fw: 45 };
  const links = kontur("hohlkehleFlanke", { ...w, zP: 0 }, -1);
  const rechts = kontur("hohlkehleFlanke", { ...w, zP: -5 }, 1);
  assert.equal(Kontur.ncBlock(links), satzListe(
    "G01 X66 Z0", "G01 X61 Z0", "G02 X60 Z-0.5 I0 K-0.5", "G01 X60 Z-4.666", "G01 X59.454 Z-4.939", "G01 X57.507 Z-5.2"));
  assert.equal(Kontur.ncBlock(rechts), satzListe(
    "G01 X66 Z-5", "G01 X61 Z-5", "G03 X60 Z-4.5 I0 K0.5", "G01 X60 Z-0.334", "G01 X59.454 Z-0.061", "G01 X57.507 Z0.2"));
  assert.ok(Kontur.radiusAbweichungGerundet(links).every(d => d <= 0.001));
  // Kante endet genau auf der Flanke: Flanke 15° zur X-Achse, scharfe Ecke bei Z−(5 − 0.5·tan15°) / Ø60
  const kanteEnde = links.saetze[3].ende;
  nah(-5 + 0.5 * Math.tan(15 * Math.PI / 180) - (30 - kanteEnde.r) * Math.tan(15 * Math.PI / 180), kanteEnde.z, 1e-9);
  // Ohne Kante und 0° = gerade Planfläche
  const gerade = kontur("hohlkehleFlanke", { ...w, zP: 0, fK: 0, wF: 0, a: 0 }, -1);
  assert.equal(Kontur.ncBlock(gerade).split("\n").slice(-2).join("|"), "G01 X60 Z-5|G01 X59 Z-5");
  assert.throws(() => kontur("hohlkehleFlanke", { ...w, zP: 0, tF: 0 }, -1), /Tiefe Flanke/);
  assert.throws(() => kontur("hohlkehleFlanke", { ...w, zP: 0, b: 0.1 }, -1), /Breite ist zu klein/);
});

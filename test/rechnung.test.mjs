// Gleiche Fälle wie KanteTest.kt der früheren Android-App.
// Geprüft wird genau das <script id="rechnung"> aus web/index.html.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const html = readFileSync(new URL("../web/index.html", import.meta.url), "utf8");
const skript = html.match(/<script id="rechnung">([\s\S]*?)<\/script>/)[1];
const { Kante, KantenEingabe, NcProgramm } = vm.runInNewContext(
  skript + "\n;({ Kante, KantenEingabe, NcProgramm })", {});

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

test("scharfe Ecke R34 = 0", () => {
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
});

test("R-Parameter und L170-Aufruf", () => {
  const ein = new KantenEingabe(87.98, -4.0, 0.0, 10.0, 2.0, 1.0);
  const aufruf = NcProgramm.l170Aufruf(ein);
  assert.ok(aufruf.startsWith("R30=87.98 R31=-4 R32=0 R33=10 R34=2 R35=1 R36=1 R37=1\nL170\n"));
  const p = new NcProgramm(zeilen(aufruf));
  assert.equal(p.rWerte.get(30), 87.98);
});

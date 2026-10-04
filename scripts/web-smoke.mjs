#!/usr/bin/env node
// Browser-Rauchtest für die Web-Version: lädt web/build/dist in Chromium und prüft
//   • keine Konsolenfehler, Service Worker aktiv, Offline-Neustart funktioniert, Manifest und Icons erreichbar,
//   • Durchlauf per Maus: Menü → Levelauswahl → Level 1 → Turm ziehen → Welle starten (mit Screenshots).
//
// Voraussetzung: einmalig `npm install playwright && npx playwright install chromium`, dann
//   ./gradlew :web:assembleWeb && node scripts/web-smoke.mjs [ausgabeordner]
import { createRequire } from 'node:module';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../web/build/dist');
const out = path.resolve(process.argv[2] || 'web-smoke-out');
fs.mkdirSync(out, { recursive: true });

const types = { '.html': 'text/html', '.js': 'text/javascript', '.png': 'image/png', '.webmanifest': 'application/manifest+json' };
const server = http.createServer((req, res) => {
  let p = path.join(root, decodeURIComponent(req.url.split('?')[0]));
  if (p.endsWith(path.sep)) p += 'index.html';
  fs.readFile(p, (err, data) => {
    if (err) { res.writeHead(404); res.end(); return; }
    res.writeHead(200, { 'Content-Type': types[path.extname(p)] || 'application/octet-stream' });
    res.end(data);
  });
});

const failures = [];
const check = (ok, what) => { console.log((ok ? '  ✓ ' : '  ✗ ') + what); if (!ok) failures.push(what); };

await new Promise((r) => server.listen(0, r));
const base = `http://localhost:${server.address().port}/`;
const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: 1280, height: 720 } });
const page = await ctx.newPage();
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });

console.log('Laden …');
await page.goto(base);
await page.waitForFunction(() => !document.getElementById('boot'), null, { timeout: 15000 });
await page.waitForTimeout(1500);
await page.screenshot({ path: path.join(out, '1-menue.png') });

console.log('Service Worker und Manifest …');
const sw = await page.evaluate(async () => {
  const reg = await navigator.serviceWorker.ready;
  const names = await caches.keys();
  return { active: !!reg.active, caches: names.length };
});
check(sw.active && sw.caches > 0, 'Service Worker aktiv, Zwischenspeicher gefüllt');
for (const f of ['manifest.webmanifest', 'icons/icon-192.png', 'icons/icon-512.png', 'icons/apple-touch-icon.png']) {
  check((await page.request.get(base + f)).status() === 200, f + ' erreichbar');
}
await ctx.setOffline(true);
await page.reload();
check(await page.waitForFunction(() => !document.getElementById('boot'), null, { timeout: 15000 }).then(() => true, () => false),
  'Offline-Neustart startet das Spiel');
await ctx.setOffline(false);
await page.waitForTimeout(500);

console.log('Spielablauf per Maus …');
const m = page.mouse;
const drag = async (x1, y1, x2, y2) => {
  await m.move(x1, y1); await m.down();
  for (let i = 1; i <= 12; i++) { await m.move(x1 + (x2 - x1) * i / 12, y1 + (y2 - y1) * i / 12); await page.waitForTimeout(16); }
  await m.up();
};
await m.click(640, 400);                      // SPIELEN
await page.waitForTimeout(1500);
await m.click(430, 250);                      // Level 1
await page.waitForTimeout(1800);
await drag(1100, 157, 420, 262);              // Puls auf die Karte ziehen
await drag(1100, 337, 330, 430);              // Mörser
await page.waitForTimeout(500);
await m.click(1000, 662);                     // Welle starten
await page.waitForTimeout(8000);
await page.screenshot({ path: path.join(out, '2-spiel.png') });
await m.click(420, 262);                      // Turm wählen → Upgrade-Panel
await page.waitForTimeout(500);
await page.screenshot({ path: path.join(out, '3-upgrade.png') });

check(errors.length === 0, 'keine Konsolenfehler' + (errors.length ? ': ' + errors.join(' | ') : ''));
await browser.close();
server.close();
console.log(failures.length ? `\n${failures.length} Prüfung(en) fehlgeschlagen.` : `\nAlles in Ordnung. Screenshots: ${out}`);
process.exit(failures.length ? 1 : 0);

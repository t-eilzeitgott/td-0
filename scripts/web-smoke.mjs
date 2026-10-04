#!/usr/bin/env node
// Browser-Test der echten Web-Version (TeaVM-Build) in Chromium:
//   1. Desktop:      keine Konsolenfehler, Service Worker, Offline-Neustart, Manifest/Icons.
//   2. iPhone quer:  kompaktes Layout, Turm per Finger ziehen, Welle, XP, automatisches Speichern,
//                    Leiste einklappen (Karte wächst), Neustart der Seite → "Fortsetzen" setzt den Lauf fort.
//   3. iPhone hoch:  gedrehte Karte – ein Turm landet genau dort, wo er losgelassen wird.
//   4. Cloud:        Profil, Token-Eingabe, Abgleich mit einem nachgebauten GitHub (api.github.com wird abgefangen),
//                    zweites Gerät übernimmt den Stand, Export-/Import-Code, Trennen.
//
// Die Seite wird mit ?debug geladen: Dann bietet sie window.__ntd.call(...) für Bildschirmpositionen und Zustände.
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
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

await new Promise((r) => server.listen(0, r));
const base = `http://localhost:${server.address().port}/`;
const browser = await chromium.launch();

// ------------------------------------------------------------------------------------------------ Hilfen

const uOf = (w, h) => Math.min(2, Math.max(0.78, Math.min(w, h) / 560));

async function open(ctx, errors, query = '?debug') {
  const page = await ctx.newPage();
  page.on('pageerror', (e) => errors.push(e.message));
  page.on('console', (m) => { if (m.type() === 'error' && !/status of 401/.test(m.text())) errors.push(m.text()); });
  await page.goto(base + query);
  await page.waitForFunction(() => !document.getElementById('boot'), null, { timeout: 15000 });
  if (query) await page.waitForFunction(() => window.__ntd, null, { timeout: 5000 });
  await page.waitForTimeout(800);
  return page;
}
const call = (page, cmd) => page.evaluate((c) => window.__ntd.call(c), cmd);
const xy = (s) => s.split(',').map(Number);
const anchor = async (page, name) => xy(await call(page, 'anchor ' + name));
const state = async (page) => Object.fromEntries((await call(page, 'state')).split(' ').map((kv) => kv.split('=')));
const profile = async (page) => Object.fromEntries((await call(page, 'profile')).split(' ').map((kv) => kv.split('=')));
const store = (page, key) => page.evaluate((k) => localStorage.getItem(k), key);

/** Finger ziehen/antippen über das Chrome-Protokoll (echte Touch-Ereignisse). */
function touch(page, cdp) {
  const send = (type, x, y) => cdp.send('Input.dispatchTouchEvent', { type, touchPoints: type === 'touchEnd' ? [] : [{ x, y }] });
  return {
    async tap(x, y) { await send('touchStart', x, y); await sleep(60); await send('touchEnd', x, y); await sleep(120); },
    async drag(x1, y1, x2, y2) {
      await send('touchStart', x1, y1);
      for (let i = 1; i <= 14; i++) { await send('touchMove', x1 + (x2 - x1) * i / 14, y1 + (y2 - y1) * i / 14); await sleep(20); }
      await send('touchEnd', x2, y2);
      await sleep(150);
    },
  };
}

/** Nachgebautes GitHub: Anmeldung, Gist-Liste, lesen, anlegen, ändern. */
class FakeGitHub {
  constructor(token) { this.token = token; this.gists = new Map(); this.seq = 1; this.calls = []; }
  async handle(route) {
    const req = route.request();
    const cors = { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*', 'access-control-allow-methods': '*' };
    if (req.method() === 'OPTIONS') { await route.fulfill({ status: 204, headers: cors }); return; }
    const json = (status, body) => route.fulfill({ status, headers: { ...cors, 'content-type': 'application/json' }, body: JSON.stringify(body) });
    this.calls.push(req.method() + ' ' + new URL(req.url()).pathname);
    if (req.headers()['authorization'] !== 'Bearer ' + this.token) { await json(401, { message: 'Bad credentials' }); return; }
    const url = new URL(req.url());
    if (url.pathname === '/user') { await json(200, { login: 'anna' }); return; }
    if (url.pathname === '/gists' && req.method() === 'GET') {
      await json(200, [...this.gists.values()].map((g) => ({ id: g.id, files: Object.fromEntries(Object.keys(g.files).map((f) => [f, {}])) })));
      return;
    }
    if (url.pathname === '/gists' && req.method() === 'POST') {
      const b = JSON.parse(req.postData());
      const g = { id: 'g' + this.seq++, public: b.public, files: {} };
      for (const [n, f] of Object.entries(b.files)) g.files[n] = { content: f.content };
      this.gists.set(g.id, g);
      await json(201, g);
      return;
    }
    const m = url.pathname.match(/^\/gists\/(\w+)$/);
    if (m && this.gists.has(m[1])) {
      const g = this.gists.get(m[1]);
      if (req.method() === 'PATCH') for (const [n, f] of Object.entries(JSON.parse(req.postData()).files)) g.files[n] = { content: f.content };
      await json(200, g);
      return;
    }
    await json(404, { message: 'Not Found' });
  }
}

// ------------------------------------------------------------------------------------------ 1. Desktop

console.log('1. Desktop: Laden, Service Worker, Offline …');
{
  const errors = [];
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 720 } });
  const page = await open(ctx, errors, '');
  await page.screenshot({ path: path.join(out, '1-menue.png') });
  const sw = await page.evaluate(async () => {
    const reg = await navigator.serviceWorker.ready;
    return { active: !!reg.active, caches: (await caches.keys()).length };
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
  check(errors.length === 0, 'keine Konsolenfehler' + (errors.length ? ': ' + errors.join(' | ') : ''));
  await ctx.close();
}

// -------------------------------------------------------------------------------------- 2. iPhone quer

console.log('2. iPhone quer: kompaktes Layout, Spielen, Speichern, Fortsetzen …');
{
  const errors = [];
  const W = 852, H = 393, u = uOf(W, H);
  const ctx = await browser.newContext({ viewport: { width: W, height: H }, deviceScaleFactor: 2, hasTouch: true, isMobile: true });
  let page = await open(ctx, errors);
  let t = touch(page, await ctx.newCDPSession(page));

  await call(page, 'go select');
  await page.waitForTimeout(1800);
  await t.tap(320, 130);                               // Level 1 (ohne Lauf und ohne Sieg: Direktstart)
  await page.waitForTimeout(1800);
  let s = await state(page);
  check(s.mode === 'COMPACT_LAND' && s.rotated === 'false', 'Handy quer nutzt das kompakte Layout (' + s.mode + ')');
  check(parseFloat(s.mapScale) >= 0.44, 'Karte groß genug: Maßstab ' + s.mapScale + ' (vorher 0,41)');
  await page.screenshot({ path: path.join(out, '2a-handy-quer.png') });

  // Turm per Finger auf die Karte ziehen (Vorschau schwebt 56·u über dem Finger)
  for (const [wx, wy] of [[300, 250], [520, 250], [760, 250]]) {
    const [sx, sy] = xy(await call(page, `spot ${wx},${wy}`));
    const [px, py] = xy(await call(page, `world ${sx},${sy}`));
    const [fx, fy] = await anchor(page, 'tile0');
    await t.drag(fx, fy, px, py + 56 * u);
  }
  s = await state(page);
  check(s.towers === '3', 'drei Türme per Finger gebaut');

  // Gesperrte Türme lassen sich nicht bauen
  const [lx, ly] = await anchor(page, 'tile1');
  const [spx, spy] = xy(await call(page, `world ${(await call(page, 'spot 900,300'))}`));
  await t.drag(lx, ly, spx, spy + 56 * u);
  check((await state(page)).towers === '3', 'gesperrter SNIPER lässt sich auf Level 1 nicht bauen');

  const [stx, sty] = await anchor(page, 'start');
  await t.tap(stx, sty);
  const [spdx, spdy] = await anchor(page, 'speed');
  await t.tap(spdx, spdy); await t.tap(spdx, spdy);    // 3x
  await page.waitForFunction(() => /cleared=[1-9]/.test(window.__ntd.call('state')), null, { timeout: 120000 }).catch(() => {});
  s = await state(page);
  const prof = await profile(page);
  check(Number(s.cleared) >= 1, 'erste Welle besiegt (Welle ' + s.wave + ', ' + s.kills + ' Abschüsse)');
  check(Number(prof.xp) > 0 && Number(prof.waves) >= 1, 'XP verbucht: ' + prof.xp);
  check((await store(page, 'neontd.profile'))?.includes('"xp"'), 'Profil im Speicher');
  await page.waitForTimeout(4000);
  const run = await store(page, 'neontd.run.serpentine');
  check(run && run.includes('"towers"') && !run.includes('"over"'), 'Lauf wurde automatisch gespeichert');

  // Leiste einklappen → Karte wächst; Zustand bleibt gemerkt
  const before = parseFloat((await state(page)).mapScale);
  const [tgx, tgy] = await anchor(page, 'toggle');
  await t.tap(tgx, tgy);
  await page.waitForTimeout(1000);
  s = await state(page);
  check(parseFloat(s.rail) < 0.05 && parseFloat(s.mapScale) >= before, `Leiste eingeklappt, Karte ${before} → ${s.mapScale} (auf breiten Handys bereits höhenbegrenzt)`);
  check((await store(page, 'neontd.ui.rail')) === '0', 'Einklappen wird gemerkt');
  await page.screenshot({ path: path.join(out, '2b-eingeklappt.png') });
  const [tg2x, tg2y] = await anchor(page, 'toggle');
  await t.tap(tg2x, tg2y);
  await page.waitForTimeout(800);

  // Seite neu laden (wie nach dem Schließen der App): "Fortsetzen"
  await page.reload();
  await page.waitForFunction(() => window.__ntd, null, { timeout: 15000 });
  await page.waitForTimeout(1000);
  t = touch(page, await ctx.newCDPSession(page));
  await call(page, 'go select');
  await page.waitForTimeout(1800);
  await t.tap(320, 130);
  await page.waitForTimeout(1200);
  check((await state(page)).scene === 'LevelSelectScene', 'Startdialog erscheint (Lauf gespeichert)');
  await page.screenshot({ path: path.join(out, '2c-startdialog.png') });
  await t.tap(W / 2, H / 2 - 44 * u + 26 * u);        // erste Schaltfläche: FORTSETZEN
  await page.waitForTimeout(1800);
  s = await state(page);
  check(s.towers === '3' && s.overlay === 'PAUSE', 'Lauf fortgesetzt: 3 Türme, startet in der Pause (Welle ' + s.wave + ')');
  const [ox, oy] = await anchor(page, 'ov0');
  await t.tap(ox, oy);
  await page.waitForTimeout(3500);
  s = await state(page);
  check(s.overlay === 'NONE' && Number(s.wave) >= 1, 'läuft nach "Weiter" weiter');
  check(errors.length === 0, 'keine Konsolenfehler' + (errors.length ? ': ' + errors.join(' | ') : ''));
  await ctx.close();
}

// -------------------------------------------------------------------------------------- 3. iPhone hoch

console.log('3. iPhone hoch: gedrehte Karte …');
{
  const errors = [];
  const W = 393, H = 852, u = uOf(W, H);
  const ctx = await browser.newContext({ viewport: { width: W, height: H }, deviceScaleFactor: 2, hasTouch: true, isMobile: true });
  const page = await open(ctx, errors);
  const t = touch(page, await ctx.newCDPSession(page));
  // Notch und Home-Indikator nachstellen (die Seite liest env(safe-area-inset-*) über das Element #safe)
  await page.evaluate(() => { const e = document.getElementById('safe'); e.style.paddingTop = '59px'; e.style.paddingBottom = '34px'; });
  await page.waitForTimeout(1200);
  await call(page, 'level 8');                          // alle Türme frei
  await call(page, 'go game');
  await page.waitForTimeout(1800);
  let s = await state(page);
  check(s.mode === 'COMPACT_PORT' && s.rotated === 'true', 'Handy hoch: gedrehte Karte (' + s.mode + ')');
  check(parseFloat(s.mapScale) >= 0.46, 'Karte groß genug: Maßstab ' + s.mapScale + ' (vorher 0,30)');
  let ok = true;
  for (const [wx, wy, tile] of [[400, 250, 0], [900, 330, 3], [250, 500, 2]]) {
    const [sx, sy] = xy(await call(page, `spot ${wx},${wy}`));
    const [px, py] = xy(await call(page, `world ${sx},${sy}`));
    const [fx, fy] = await anchor(page, 'tile' + tile);
    await t.drag(fx, fy, px, py + 56 * u);
    const [tx, ty] = xy(await call(page, 'tower'));
    ok = ok && Math.abs(tx - sx) < 1.5 && Math.abs(ty - sy) < 1.5;
  }
  check(ok && (await state(page)).towers === '3', 'Türme landen auf der gedrehten Karte genau dort, wo sie losgelassen werden');
  await page.screenshot({ path: path.join(out, '3a-handy-hoch.png') });
  // Turm wählen → Panel in der Leiste, Upgrade kaufen
  const [ax, ay] = xy(await call(page, 'world 400,250'));
  const [tx0, ty0] = xy(await call(page, 'tower'));
  await t.tap(...xy(await call(page, `world ${(await call(page, 'tower'))}`)));
  s = await state(page);
  check(s.selected === 'true', 'Turm antippen wählt ihn');
  await call(page, 'money 5000');
  const money = Number((await state(page)).money);
  await t.tap(...(await anchor(page, 'track1')));
  check(Number((await state(page)).money) < money, 'Upgrade gekauft (Panel auf dem Handy bedienbar)');
  await page.screenshot({ path: path.join(out, '3b-panel.png') });
  // Leiste einklappen → in der Hochformat-Ansicht wächst die Karte deutlich
  await page.keyboard.press('Escape');                 // Auswahl aufheben
  await page.waitForTimeout(400);
  const open0 = parseFloat((await state(page)).mapScale);
  await t.tap(...(await anchor(page, 'toggle')));
  await page.waitForTimeout(1000);
  const closed0 = parseFloat((await state(page)).mapScale);
  check(closed0 > open0 * 1.05, `Leiste einklappen vergrößert die Karte: ${open0.toFixed(3)} → ${closed0.toFixed(3)}`);
  check(errors.length === 0, 'keine Konsolenfehler' + (errors.length ? ': ' + errors.join(' | ') : ''));
  await ctx.close();
}

// ------------------------------------------------------------------------------------------ 4. Cloud

console.log('4. Cloud: Profil, Token, Abgleich zwischen zwei Geräten, Code …');
{
  const errors = [];
  const TOKEN = 'ghp_' + 'x'.repeat(36);
  const gh = new FakeGitHub(TOKEN);
  const W = 393, H = 852;
  const device = async (answers) => {
    const ctx = await browser.newContext({ viewport: { width: W, height: H }, deviceScaleFactor: 2, hasTouch: true, isMobile: true });
    await ctx.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: base.replace(/\/$/, '') }).catch(() => {});
    await ctx.route('https://api.github.com/**', (r) => gh.handle(r));
    const page = await open(ctx, errors);
    page.on('dialog', (d) => d.accept(answers.next ?? ''));
    return { ctx, page, t: touch(page, await ctx.newCDPSession(page)), answers };
  };
  const press = async (dev, name) => {
    await dev.page.mouse.wheel(0, 3000);
    await dev.page.waitForTimeout(500);
    await dev.t.tap(...(await anchor(dev.page, name)));
    await dev.page.waitForTimeout(400);
  };
  const waitSync = (dev) => dev.page.waitForFunction(() => /sync=(OK|ERROR)/.test(window.__ntd.call('profile')), null, { timeout: 15000 });

  const A = await device({});
  await call(A.page, 'level 3');
  await call(A.page, 'go profile');
  await A.page.waitForTimeout(1200);
  A.answers.next = 'Tester';
  await A.page.evaluate(() => window.scrollTo(0, 0));
  await A.t.tap(...(await anchor(A.page, 'name')));
  await A.page.waitForTimeout(400);
  check((await profile(A.page)).name === 'Tester', 'Name über das Eingabefeld geändert');
  check((await store(A.page, 'neontd.profile')).includes('"Tester"'), 'Name gespeichert');

  A.answers.next = TOKEN;
  await press(A, 'connect');
  await waitSync(A);
  let pa = await profile(A.page);
  check(pa.sync === 'OK' && pa.cloud === 'true', 'Gerät A verbunden und abgeglichen (' + pa.msg + ')');
  check(gh.gists.size === 1 && [...gh.gists.values()][0].public === false, 'ein privater Gist wurde angelegt');
  const content = [...gh.gists.values()][0].files['neon-td-save.json'].content;
  check(content.includes('"xp"') && content.includes('Tester') && !content.includes(TOKEN), 'Inhalt enthält den Spielstand, aber nie das Token');
  check((await store(A.page, 'neontd.cloud.token')) === TOKEN, 'Token nur im lokalen Speicher von A');
  await A.page.screenshot({ path: path.join(out, '4a-cloud.png') });

  // Gerät B (eigener Speicher, wie die Home-Bildschirm-App am iPhone) holt den Stand
  const B = await device({});
  await call(B.page, 'go profile');
  await B.page.waitForTimeout(1200);
  B.answers.next = TOKEN;
  await press(B, 'connect');
  await waitSync(B);
  const pb = await profile(B.page);
  check(pb.sync === 'OK' && pb.name === 'Tester' && Number(pb.level) >= 3, `Gerät B hat den Stand übernommen (Level ${pb.level}, Name ${pb.name})`);
  check(gh.gists.size === 1, 'dasselbe Gist wird weiterverwendet');

  // Falsches Token
  const Cbad = await device({});
  await call(Cbad.page, 'go profile');
  await Cbad.page.waitForTimeout(1200);
  Cbad.answers.next = 'ghp_' + 'y'.repeat(36);
  await press(Cbad, 'connect');
  await waitSync(Cbad);
  const pc = await profile(Cbad.page);
  check(pc.sync === 'ERROR' && pc.cloud === 'false', 'falsches Token wird abgelehnt (' + pc.msg + ')');
  await Cbad.ctx.close();

  // Export-/Import-Code
  await call(A.page, 'go profile');
  await A.page.waitForTimeout(1200);
  await press(A, 'copy');
  const code = await A.page.evaluate(() => navigator.clipboard.readText()).catch(() => '');
  check(code.startsWith('NTD1:') && code.length > 40, 'Export-Code in der Zwischenablage (' + code.length + ' Zeichen)');
  const D = await device({});
  await call(D.page, 'go profile');
  await D.page.waitForTimeout(1200);
  D.answers.next = code;
  await press(D, 'paste');
  await D.page.waitForTimeout(500);
  const pd = await profile(D.page);
  check(pd.name === 'Tester' && Number(pd.level) >= 3, `Import-Code übernimmt Profil (Level ${pd.level})`);
  D.answers.next = 'unsinn';
  await press(D, 'paste');
  check((await profile(D.page)).name === 'Tester', 'ungültiger Code ändert nichts');

  // Trennen
  await press(A, 'disconnect');
  await A.page.waitForTimeout(400);
  check((await store(A.page, 'neontd.cloud.token')) === null && (await profile(A.page)).cloud === 'false', 'Trennen entfernt das Token');
  check((await profile(A.page)).name === 'Tester', 'Spielstand bleibt nach dem Trennen auf dem Gerät');
  check(errors.length === 0, 'keine Konsolenfehler' + (errors.length ? ': ' + errors.join(' | ') : ''));
  await A.ctx.close(); await B.ctx.close(); await D.ctx.close();
}

await browser.close();
server.close();
console.log(failures.length ? `\n${failures.length} Prüfung(en) fehlgeschlagen.` : `\nAlles in Ordnung. Screenshots: ${out}`);
process.exit(failures.length ? 1 : 0);

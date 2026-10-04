# Entwickler-Dokumentation

Diese Seite erklärt, wie Neon TD aufgebaut ist, worauf die Physik beruht und wie man das Spiel erweitert.

## Überblick

```
┌────────────────────────────── core (reines Java, keine Abhängigkeiten) ──────────────────────────────┐
│  math ─ physics ─ sim ─ level ─ progress ─ save   gfx ─ fx ─ render      ui ─ scene ─ app ─ platform  │
│  └─────── Simulation und Daten (kennen ──────────┘ └─ Darstellung ──────┘ └──── Bedienung ───────────┘ │
│           weder Bildschirm noch Eingabe)                                                              │
└───────────────────────────────────────────────────────────────────────────────────────────────────────┘
         ▲                                              ▲
   desktop (Java2D, Swing)                       web (TeaVM → JavaScript, Canvas2D)
```

Der Kern ist bewusst **plattformunabhängig**: Er kennt nur zwei kleine Schnittstellen zur Außenwelt –

- `neontd.gfx.Gfx` – eine winzige Vektor-Zeichen-API (Pfade, Füllen, Linien, Text, Transformation, Alpha, additives
  Mischen). Umgesetzt als `CanvasGfx` (Browser) und `Java2DGfx` (Desktop).
- `neontd.platform.Platform` – dauerhafter Speicher (`KeyValueStore`), Uhrzeit, Netzwerk (`save.Http`, optional),
  Eingabefeld (`prompt`), Zwischenablage, „Adresse öffnen“ und ein paar Hinweise („Touch-Gerät?“). Alles außer dem
  Speicher hat eine harmlose Standardimplementierung; eine neue Plattform kann also klein anfangen.

Eine neue Plattform (z. B. JavaFX, ein Handy-Backend) braucht nur diese beiden Schnittstellen plus eine Schleife, die
`App.resize`, `App.update`, `App.render` und die Eingabemethoden (`pointerDown/Move/Up`, `key`, `wheel`) aufruft.
Genau das tun `desktop/DesktopMain` und `web/WebMain`.

## Das Physik-Fundament

Alles Spielgeschehen liegt in `neontd.sim.World`; die Bausteine stehen in `neontd.physics`.

### Fester Zeitschritt und Interpolation
`World.step()` rechnet immer exakt `World.STEP = 1/60 s`. `FixedTimestep` sammelt die echte Zeit und sagt, wie viele
Schritte fällig sind (höchstens 8 pro Frame – genug für Tempo ×5, aber kein „Aufholspirale“-Effekt nach Tab-Wechseln). Das macht die Simulation
unabhängig von der Bildrate und **deterministisch**. Tempo ×2/×3 bedeutet *mehr Schritte pro Frame*, nie größere Schritte.
Beim Zeichnen werden Positionen mit `alpha()` zwischen dem vorletzten und letzten Zustand (`prevX/prevY` → `x/y`)
interpoliert; dadurch bleibt alles auch bei 120-Hz-Displays ruhig.

### Pfade nach Bogenlänge (`Path`)
Ein Pfad wird aus Kontrollpunkten über einen **zentripetalen Catmull-Rom-Spline** zu einer dichten Polylinie gesampelt
(`Path.fromControlPoints`): Die Kurve läuft durch alle Punkte, bildet keine Schleifen oder Spitzen. Gegner speichern nur
`dist` – ihre Bogenlänge auf dem Pfad. Daraus folgt:

- Die Geschwindigkeit ist **exakt** und unabhängig von der Kurvenform (`dist += speed * dt`).
- Die Position in der Zukunft ist **exakt berechenbar**: `positionAt(dist + v·t)`.
- Verlangsamung, Pfadlänge, „Erster/Letzter“-Zielwahl sind triviale Vergleiche auf `dist`.

### Zielvorhersage
`World.leadTime` löst „Wo ist der Gegner, wenn mein Geschoss dort ankommt?“ mit einer kleinen Fixpunkt-Iteration über
`positionAt` (konvergiert, solange das Geschoss schneller ist als der Gegner). Puls-Kugeln und Mörser-Granaten
zielen damit auf die vorhergesagte Stelle. `SimTest.leadAimingHitsMostBullets` sichert eine Trefferquote > 90 % zu.

### Broad-Phase und kontinuierliche Kollision
- `SpatialHash` ist ein gleichmäßiges Raster ohne Allokation im Betrieb. Jeder Gegner steht genau in der Zelle seines
  Mittelpunkts; wer sucht, erweitert den Radius um den größten Gegnerradius (`World.MAX_ENEMY_RADIUS`) und prüft danach
  genau. Es wird einmal pro Schritt neu gefüllt.
- `Collision.sweptCircles` rechnet die Kollision zweier *bewegter* Kreise in Relativbewegung und liefert den
  Zeitpunkt des ersten Kontakts im Schritt. **Schnelle Geschosse können dadurch nicht durch kleine Gegner
  „tunneln“** (`CollisionTest.fastProjectileDoesNotTunnelThroughSmallTarget`). Bei mehreren Kandidaten gewinnt der
  früheste Kontakt, bei Gleichstand die kleinere Gegner-ID – damit bleibt das Ergebnis deterministisch.
- Flächenschaden (`World.explode`) und Blitzketten (`fireArc`) nutzen dieselbe Hash-Abfrage.

### Reihenfolge eines Schritts
`step()` → Wellen erzeugen → Gegner bewegen (Lecks erkennen) → Hash neu füllen → Türme (Ziel wählen, feuern) →
Geschosse bewegen und treffen → Tode verarbeiten (Belohnung, Splitter-Kinder) → Aufräumen → Wellen/Sieg prüfen.
Tode werden erst *nach* allen Treffern verarbeitet, damit sich Listen nicht mitten in einer Schleife ändern.

### Determinismus
`Rng` (mulberry32) nutzt nur 32-Bit-Integer-Arithmetik und liefert im Browser dieselbe Folge wie auf der JVM. Die
Simulation verändert sich nur durch die Befehle `placeTower`, `upgrade`, `sell`, `startNextWave` (und `autoStart`).
`SimTest.simulationIsDeterministic` lässt einen Bot zweimal 150 s spielen und vergleicht den Zustands-Hash.
Das ist die Grundlage für Wiederholungen (Replays) aus einem Befehlsprotokoll und für automatische Balance-Tests.

### Trennung von Simulation und Darstellung
`World` meldet alles Sichtbare über `SimListener` (Treffer, Abschuss, Explosion, Strahl, Blitz, Welle …). `GameFx`
übersetzt diese Ereignisse in Effekte (`fx.Effects`: Feuerwerk, Ringe, Strahlen, Blitze, Bildschirmwackeln) – die
Simulation weiß nichts davon. Dieselbe Schnittstelle ist der richtige Ort für Ton oder Statistiken.

### Partikel
`fx.Particles` simuliert Funken mit Geschwindigkeit, Schwerkraft und Luftwiderstand als *Struct-of-Arrays* (keine
Objekte pro Partikel). Das ist wichtig für Handys: Auch viele gleichzeitige Feuerwerke erzeugen kaum Müll für die
Speicherbereinigung des Browsers. Ein Partikel vom Typ `POP` zerplatzt am Lebensende in kleine Funken („Knistern“).

## Darstellung im Neon-Stil

- **Leuchten ohne Weichzeichner:** `gfx.Neon.stroke` zeichnet den aktuellen Pfad mehrfach mit wachsender Breite und
  sinkender Deckkraft (additiv) und zuletzt mit hellem Kern. Das sieht in Canvas2D und Java2D gleich aus und ist
  deutlich billiger als `shadowBlur`. `Neon.quality` (2/1/0) steuert den Aufwand; `App.governQuality` senkt ihn
  automatisch, wenn das Gerät unter ~38 fps fällt, und hebt ihn bei Luft wieder an.
- **Strichschrift:** `gfx.NeonText` zeichnet Überschriften aus eckigen „Neonröhren“-Buchstaben (A–Z, Ä Ö Ü, Ziffern)
  – scharf in jeder Größe, mit optionalem Flackern.
- **Icons:** `gfx.Icons` – alle Symbole sind Vektorpfade in einer Einheitsbox.
- **Farben:** `gfx.Theme`. `Theme.enemyColor(hp)` bildet Lebenspunkte logarithmisch auf den Farbverlauf ab.
- **Layout:** `ui.Viewport` liefert Fenstergröße, Sicherheitsränder (Notch, Home-Indikator) und die Einheit `u`, mit der
  Bedienelemente auf Handy, Laptop und 4K ähnlich groß wirken. Die Geometrie der Spielszene steckt – ohne zu zeichnen
  und damit testbar – in `scene.GameLayout` (siehe unten).

### Layouts der Spielszene (`GameLayout`)

| Modus | Wann | Aufbau |
|---|---|---|
| `DOCKED` | Laptop/Tablet quer | Karte links, feste Seitenleiste rechts (Kopf mit Level-Balken, Shop, Panel, Steuerung) |
| `STACKED` | Tablet hoch | Karte oben, darunter Shop und Panel |
| `COMPACT_LAND` | kleinste Fensterkante < 480 (Handy quer) | schmale Leiste rechts (Türme │ Steuerung), Kopfzeile schwebt über der Karte, Upgrade-Panel als Popover neben dem Turm (wechselt die Seite, wenn es ihn verdecken würde) |
| `COMPACT_PORT` | Handy hoch | **Karte um 90° gedreht** (Pfad von oben nach unten), Leiste unten; ein gewählter Turm ersetzt die Turmleiste durch das Panel |

In den kompakten Modi klappt der Pfeil-Knopf die Leiste ein (animiert über `railT`, gemerkt unter
`neontd.ui.rail`): quer bleiben nur START und der Knopf als Überlagerung, hoch nur die Steuerzeile. Das Ergebnis in
Zahlen steht in `GameLayoutTest` (iPhone 15 quer: Kartenmaßstab 0,41 → 0,49/0,50; hoch: 0,30 → 0,48/0,53).

Die **gedrehte Karte** wird beim Zeichnen mit `rotate(π/2)` abgebildet; `GameLayout.worldX/worldY/screenX/screenY`
rechnen Bildschirm und Welt ineinander um (auch für Ziehen, Antippen, Panel-Platzierung). Damit Zahlen waagerecht
bleiben, reicht die Szene für Gegner und Effekte eine `gfx.UprightGfx` durch, die Text um seinen Ankerpunkt
zurückdreht; der Blitz-Turm bleibt über `TowerArt.uprightAngle` aufrecht. Der Editor nutzt dieselbe Idee über
`scene.MapView`.

`Gfx`-Vereinbarungen: Koordinaten in logischen Pixeln, y nach unten; Farben `0xAARRGGBB`, wobei Alpha-Byte 0 als „voll
deckend“ gilt; `alpha(a)` *multipliziert* und gilt bis zum nächsten `restore()`; Text ist vertikal an y zentriert;
`arc` läuft im Uhrzeigersinn.

## Erweitern

**Neuer Turm**
1. Eintrag in `sim.TowerType` (Name, Beschreibung, Farbe, Preis, Reichweite, Schaden, Intervall).
2. Verhalten in `World.fire` (neue `fireXyz`-Methode; Treffer immer über `damage(...)`, Effekte über den Listener).
3. Symbol in `render.TowerArt.symbol`, Effekte in `render.GameFx` (optional).
4. Der Shop, die Hotkeys (`1`–`9`) und das Upgrade-Panel passen sich der Anzahl der `TowerType`-Einträge an.

**Neuer Gegner:** Eintrag in `sim.EnemyType` (Form aus `Shape`, Radius, Tempo, HP-Faktor, Belohnung, Lecks, Splitter),
bei neuer Form die Zeichnung in `render.EnemyArt`; Auftritt in `WaveFactory` bzw. `Levels`.

**Neues Level:** In `level.Levels` Kontrollpunkte (und optional handgebaute Wellen) ergänzen – oder einfach im Editor
zeichnen. Level werden als lesbarer Text gespeichert (`level.LevelCodec`, Format `NTD1`).

**Balance:** Zahlen in `TowerType`, `EnemyType`, `UpgradeTrack` und `WaveFactory` ändern und
`./gradlew :core:test --tests '*BalanceTest*' -i` ausführen: `AutoPlayer` spielt Level 1 mit einer soliden und einer
„faulen“ Strategie und gibt aus, wie weit er kommt. Der Test verlangt: solide Strategie gewinnt, faule verliert.

**Neuer Freischalt-Turm / neue Belohnung:** `TowerType.unlockLevel` bestimmt, ab welchem Spielerlevel ein Turm
baubar ist; Startgeld und Start-Leben stehen in `Progress.startMoneyBonus/startLivesBonus`. Die Oberfläche (Schloss in
der Kachel, Profilseite) liest nur diese Methoden.

**Neue Plattform:** `Gfx` und `Platform` implementieren (siehe oben).

## Endlosmodus

`World.enableEndless()` schaltet um (auch mitten im Spiel nach dem Sieg). Danach liefert `waveAt(i)` Wellen bei Bedarf
aus `WaveFactory.wave(i + 1)`; die Zähler je Welle wachsen mit (`ensureWaveCapacity`), `firstOpen` verhindert, dass
`checkWaves` jeden Schritt alle Wellen durchläuft. Wellen mit nur noch wenigen Nachzüglern (`EARLY_START_LEFT`) halten den
automatischen Start nicht mehr auf (`readyForNextWave`).

Entwurf für Welle 1000 (`WaveFactory`, `UpgradeTrack`; gemessen mit `EndlessProbe`):

- **Zahlen bleiben im `int`:** HP wachsen quadratisch, ab Welle 100 mit +0,5 % je Welle (Welle 1000: ≈ 16 Mio. je Block,
  ≈ 470 Mio. je Titan, Obergrenze `MAX_HP` = 10⁹). Geld (`MAX_MONEY` = 2·10⁹), Schaden (`Tower.MAX_DAMAGE`), Preise und
  Investitionen sind begrenzt (`World.addMoney`, saturierend). Die Rechnung nutzt nur `double` und Wurzeln
  (`powSixteenths`), kein `Math.pow` und kein `long` – auf JVM und im Browser bitgleich.
- **Anzahl der Gegner ist begrenzt** (BLOCK ≤ 36, DART ≤ 30, SPLITTER/KOLOSS ≤ 12, TITAN ≤ 5, dazu Schwärme): höchstens
  ~240 je Welle. Intervalle schrumpfen etwas (Faktor 0,8), damit eine Welle ~25 s zum Erscheinen braucht.
- **Belohnungen** wachsen mit (HP/HP₂₀)^0,625 (`Enemy.reward`, `WaveDef.Group.reward`); die Türme halten über **Meisterstufen**
  Schritt (`UpgradeTrack.multAt`, Preis `cost` ×1,36 je Stufe). Das Verhältnis ist so gewählt, dass der einfache
  `AutoPlayer` (Endlos-Strategie: relativer Zugewinn pro Preis) Welle 1000 gerade noch mit 20 Leben schafft. Wer an den
  Zahlen dreht, lässt `EndlessProbe` laufen (`java -cp core/build/classes/java/main:core/build/classes/java/test
  neontd.sim.EndlessProbe 1000 100`, ~30 s) und prüft, dass der Bot weder trivial durchkommt noch früh scheitert.

## Fortschritt, Speichern und Cloud

```
progress.Progress        Profil: XP, Level, Titel, Freischaltungen, Statistik, Rekorde
save.ProfileJson         Profil ↔ JSON, Zusammenführen (max der Zähler, Vereinigung der gewonnenen Level)
sim.WorldSnapshot        Abbild des Spielstands (Türme, Gegner im Anflug, Geld, Leben, Welle)
save.RunSave             Lauf = Zeitstempel + WorldSnapshot (oder Ende-Marker "over")
save.SaveStore           lokaler Speicher: Profil, Läufe, Cloud-Einstellungen (Token, Gist-Kennung)
level.LevelStore         eigene Level mit Zeitstempel und Löschmarkern
save.SaveBundle          alles zusammen = Dateiformat des Gists und des Export-Codes ("NTD1:" + Base64)
save.GistSync            Abgleich: lesen → zusammenführen → lokal übernehmen → bei Abweichung schreiben
save.Http / WebHttp / JavaHttp   Netzwerkschnittstelle; Browser: fetch (mit keepalive), Desktop: java.net.http
```

**Fortsetzen:** `World.snapshot()` liefert einen Stand nur, wenn kein `Emitter` mehr läuft (`canSnapshot`): Dann ist
der Zustand vollständig durch Türme, Geld/Leben/Welle und die lebenden Gegner beschrieben. `GameScene` speichert alle
3 s, nach jeder besiegten Welle (nach dem Simulationsschritt, nicht mittendrin), beim Pausieren/Verlassen und in
`onSuspend`. `World.restore()` überspringt ungültige Einträge (kaputte Speicherstände stürzen nie ab, siehe
`SaveTest.brokenRunSavesAreRejectedOrSanitized`).

**Cloud-Abgleich:** Jeder Eintrag trägt einen Zeitstempel; gelöschte Level und beendete Läufe bleiben als kleine
Marker bestehen, damit der Abgleich sie nicht wiederbelebt. Zusammenführen ist vertauschbar und idempotent
(`SaveTest.profileMergeNeverLosesProgress`); `SyncTest` spielt zwei „Geräte“ gegen einen nachgebauten Gist-Server.
Fehlercodes werden in verständliche Meldungen übersetzt (`GistSync.describe`). Das Token liegt nur im lokalen Speicher
(`neontd.cloud.token`) und geht nur im `Authorization`-Header an `api.github.com`; `SyncTest` prüft, dass es nie im Gist
landet. Der Service Worker fasst fremde Adressen nicht an (er behandelt nur gleiche Herkunft).

**Aufrufe von außen:** `App.commitProgress()` (Profil sichern, Cloud vormerken), `App.suspend()` (Seite wird
unsichtbar: Lauf sichern, Cloud sofort abgleichen – im Browser über `visibilitychange`/`pagehide`).

## TeaVM – worauf man im `core` achten muss

Der Browser-Build übersetzt den **Bytecode** des Kerns mit TeaVM nach JavaScript. Deshalb im `core`: keine Reflection,
keine Threads, kein `java.time`/`java.text`, kein `String.format`. Lambdas, Enums, Collections, `StringBuilder` und
`Math` funktionieren.

**Vorsicht mit `long`:** TeaVM setzt `long` im Browser über JavaScript-`BigInt` um – das ist deutlich langsamer als
normale Zahlen und braucht iOS 14 oder neuer. In Code, der pro Frame oder pro Schuss läuft, deshalb nur `int` und
`double` verwenden. Besonders tückisch: `Math.round(double)` liefert ein `long`. Statt dessen gibt es
`Mathx.roundToInt` und `Mathx.round1`. (Das erzeugte JavaScript ist reines ES2015 und braucht sonst keine
neueren Browser-Funktionen.)

Browser-Funktionen, die TeaVMs typisierte APIs nicht bequem bieten, stehen in `web/Js.java` als `@JSBody`-Einzeiler.
Vorsicht beim Schreiben: Escape-Folgen in diesen Skripten brauchen in Java doppelte Rückstriche (`'\\n'`), sonst steckt ein
echter Zeilenumbruch im JavaScript und die Seite startet nicht – der Browser-Test (`scripts/web-smoke.mjs`) fängt das ab.

## Tests und Werkzeuge

| | |
|---|---|
| `PathTest`, `CollisionTest`, `SpatialHashTest`, `MiscPhysicsTest` | Physik: Bogenlänge, Tunneling, Raster, Zeitschritt, RNG, Vereinfachung |
| `SimTest` | Platzieren, Upgrades, Verkauf, jeder Turmtyp, Splitter, Sieg/Niederlage, Zielvorhersage, Determinismus |
| `BalanceTest` | Bot gewinnt Level 1, faule Strategie verliert, Wellenkurve wächst stetig |
| `EndlessTest` | Sieg → Endlos weiter, Wellen bis 1200 im Zahlenrahmen, Meisterstufen und Preise, Geld-/Schaden-Überlauf, früher Wellenstart, Bot in Welle 150 |
| `ProgressTest` | XP-Kurve, Freischaltungen, Startbonus, Titel, Rekorde, Überlauf |
| `SaveTest` | JSON, Base64, Profil-Speicher und -Merge, Lauf speichern/wiederherstellen (auch beschädigt), Level-Zeitstempel |
| `SyncTest` | Cloud-Abgleich zweier Geräte gegen einen nachgebauten Gist-Server, Fehlerfälle, Trennen, Export-Code |
| `GameLayoutTest` | Kartengröße auf iPhone-Maßen, keine Überlappungen, Koordinaten-Umrechnung inkl. Drehung |
| `GameFlowTest` | Gesperrte Türme, XP und Speicherung, Fortsetzen nach „Neustart“, Endlos nach Sieg, Leiste ein-/ausklappen, gedrehte Karte, Editor kompakt |
| `LevelStoreTest` | Speichern/Laden, kaputte Daten werden übersprungen |
| `SceneSmokeTest` | Alle Szenen in sechs Fenstergrößen mit Tippen, Ziehen und Tasten; prüft u. a. ausgeglichenes `save/restore` und keine `NaN`-Werte |
| `desktop/ScreenshotTool` | Startet das Spiel ohne Fenster, führt ein Skript aus (Klicks, Ziehen, Tasten) und speichert PNGs – siehe `scripts/screenshots.sh` |
| `desktop/IconGenerator` | Erzeugt die App-Icons (`web/src/main/webapp/icons`) |
| `sim/EndlessProbe` | Messwerkzeug: Bot spielt den Endlosmodus und gibt Kennzahlen je 100 Wellen aus |
| `scripts/web-smoke.mjs` | Browser-Test der echten Web-Version in Chromium (siehe unten) |

**Browser-Test:** `./gradlew :web:assembleWeb && node scripts/web-smoke.mjs` startet einen kleinen Webserver, lädt die Seite
mit `?debug` und prüft in Chromium: Service Worker und Offline-Start; iPhone quer (kompaktes Layout, Turm per
Touch ziehen, gesperrter Turm, XP, automatisches Speichern, Leiste einklappen, Seite neu laden und *Fortsetzen*); iPhone
hoch (gedrehte Karte: Turm landet genau dort, wo er losgelassen wird, Upgrade im Panel); Cloud (Namen setzen, Token
eingeben, Abgleich gegen ein per `page.route` nachgebautes `api.github.com`, zweites Gerät, falsches Token,
Export-/Import-Code, Trennen). Mit `?debug` bietet die Seite `window.__ntd.call("anchor tile0")` u. ä. (`web/DebugApi`),
damit der Test nicht von Pixelmaßen abhängt; ohne `?debug` gibt es diese Schnittstelle nicht.

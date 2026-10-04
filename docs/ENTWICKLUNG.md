# Entwickler-Dokumentation

Diese Seite erklärt, wie Neon TD aufgebaut ist, worauf die Physik beruht und wie man das Spiel erweitert.

## Überblick

```
┌────────────────────────────── core (reines Java, keine Abhängigkeiten) ──────────────────────────────┐
│  math ─ physics ─ sim ─ level            gfx ─ fx ─ render              ui ─ scene ─ app ─ platform  │
│  └─────── Simulation (kennt keinen ──────┘ └──── Darstellung ───────────┘ └──── Bedienung ────────────┘ │
│           Bildschirm, keine Eingabe)                                                                  │
└───────────────────────────────────────────────────────────────────────────────────────────────────────┘
         ▲                                              ▲
   desktop (Java2D, Swing)                       web (TeaVM → JavaScript, Canvas2D)
```

Der Kern ist bewusst **plattformunabhängig**: Er kennt nur zwei kleine Schnittstellen zur Außenwelt –

- `neontd.gfx.Gfx` – eine winzige Vektor-Zeichen-API (Pfade, Füllen, Linien, Text, Transformation, Alpha, additives
  Mischen). Umgesetzt als `CanvasGfx` (Browser) und `Java2DGfx` (Desktop).
- `neontd.platform.Platform` – dauerhafter Speicher (`KeyValueStore`) und ein paar Hinweise („Touch-Gerät?“).

Eine neue Plattform (z. B. JavaFX, ein Handy-Backend) braucht nur diese beiden Schnittstellen plus eine Schleife, die
`App.resize`, `App.update`, `App.render` und die Eingabemethoden (`pointerDown/Move/Up`, `key`, `wheel`) aufruft.
Genau das tun `desktop/DesktopMain` und `web/WebMain`.

## Das Physik-Fundament

Alles Spielgeschehen liegt in `neontd.sim.World`; die Bausteine stehen in `neontd.physics`.

### Fester Zeitschritt und Interpolation
`World.step()` rechnet immer exakt `World.STEP = 1/60 s`. `FixedTimestep` sammelt die echte Zeit und sagt, wie viele
Schritte fällig sind (höchstens 6 pro Frame – kein „Aufholspirale“-Effekt nach Tab-Wechseln). Das macht die Simulation
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
  Bedienelemente auf Handy, Laptop und 4K ähnlich groß wirken. `GameScene.layout()` und `EditorScene.layout()` wählen
  je nach Seitenverhältnis Quer- (Seitenleiste) oder Hochformat (Leiste unter der Karte).

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

**Fortschritt (Progression):** Einhängepunkte sind vorhanden – `platform.KeyValueStore` zum Speichern, `SimListener.
onGameEnded` für das Ergebnis, `Icons.Icon.LOCK`/`STAR` für Anzeigen und `LevelSelectScene` für gesperrte Level. Eine
`Progress`-Klasse (Sterne je Level, Freischaltungen) würde über den `LevelStore`-Mechanismus gespeichert.

**Neue Plattform:** `Gfx` und `Platform` implementieren (siehe oben).

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

## Tests und Werkzeuge

| | |
|---|---|
| `PathTest`, `CollisionTest`, `SpatialHashTest`, `MiscPhysicsTest` | Physik: Bogenlänge, Tunneling, Raster, Zeitschritt, RNG, Vereinfachung |
| `SimTest` | Platzieren, Upgrades, Verkauf, jeder Turmtyp, Splitter, Sieg/Niederlage, Zielvorhersage, Determinismus |
| `BalanceTest` | Bot gewinnt Level 1, faule Strategie verliert, Wellenkurve wächst stetig |
| `LevelStoreTest` | Speichern/Laden, kaputte Daten werden übersprungen |
| `SceneSmokeTest` | Alle Szenen in sechs Fenstergrößen mit Tippen, Ziehen und Tasten; prüft u. a. ausgeglichenes `save/restore` und keine `NaN`-Werte |
| `desktop/ScreenshotTool` | Startet das Spiel ohne Fenster, führt ein Skript aus (Klicks, Ziehen, Tasten) und speichert PNGs – siehe `scripts/screenshots.sh` |
| `desktop/IconGenerator` | Erzeugt die App-Icons (`web/src/main/webapp/icons`) |

Die Web-Version lässt sich mit Playwright/Chromium automatisiert prüfen (`web/build/dist` per HTTP ausliefern, Seite
laden, Mausklicks oder Touch-Ereignisse senden, Konsolenfehler und Screenshots auswerten).

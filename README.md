# NEON TD

Ein Tower-Defense-Spiel im Neon-Look: tiefschwarzer Hintergrund, leuchtende Farben, klare Symbole – inspiriert von
BBTAN, mit dem Ablauf von Bloons TD. Die gesamte Spiellogik ist in **Java** geschrieben und läuft

- **im Browser am PC**,
- **auf dem iPhone** (als App auf dem Home-Bildschirm, auch offline) und
- als **Desktop-Anwendung** (Windows, macOS, Linux).

<p align="center">
  <img src="docs/screenshots/game.png" alt="Gefecht: fünf Turmklassen, Gegner mit HP-Zahl, Feuerwerk" width="860">
</p>

## Was drin ist

- **5 Turmklassen**, jede mit eigener Rolle und eigenem Neon-Symbol (siehe unten).
- **3 Upgrade-Kategorien pro Turm** – *Reichweite*, *Schaden* und *Tempo* – mit je 5 Stufen.
- **Wellen wie bei Bloons TD**: Welle manuell starten (auch früher!), Tempo 1×/2×/3×, automatischer Wellenstart, Pause.
- **Gegner tragen ihre Lebenspunkte als Zahl auf sich**. Die Farbe folgt den aktuellen HP (gelb → orange → rot → pink
  → violett → indigo), die Form zeigt die Art.
- **Feuerwerk**, wenn ein Gegner zerstört wird – mit Funken-Schweifen, Ringen und knisternden Nachzündern.
- **Hauptmenü** mit lebendigem Hintergrund (ein Bot verteidigt dort im Dauerlauf Level 1) und **Levelauswahl**.
- **Level 1 „Serpentine“** mit 20 Wellen und Titan-Bossen in Welle 10 und 20.
- **Level-Editor**: eigene Pfade per Punkten *oder freihändig* zeichnen, Pfade glätten sich zu Kurven, Rückgängig,
  Probespielen, Speichern. Eigene Level erscheinen in der Levelauswahl.
- **Physik-Fundament** für die Weiterentwicklung: fester Zeitschritt, Pfade nach Bogenlänge, Broad-Phase,
  kontinuierliche Kollision, Zielvorhersage, deterministische Simulation (siehe [docs/ENTWICKLUNG.md](docs/ENTWICKLUNG.md)).

| Hauptmenü | Levelauswahl | Level-Editor |
|:--:|:--:|:--:|
| <img src="docs/screenshots/menu.png" width="290"> | <img src="docs/screenshots/levels.png" width="290"> | <img src="docs/screenshots/editor.png" width="290"> |

## Spielen

### Im Browser und auf dem iPhone (empfohlen)

Die Web-Version ist eine statische Seite. Das Repository enthält einen GitHub-Workflow, der sie baut und auf
**GitHub Pages** veröffentlicht:

1. Änderungen in den Hauptzweig (`main`) bringen.
2. Im Repository **Settings → Pages → Source: „GitHub Actions“** einmalig auswählen
   (bei privaten Repositories hängt die Verfügbarkeit von deinem GitHub-Tarif ab).
3. Nach dem ersten Durchlauf des Workflows *Build und Veröffentlichung* liegt das Spiel unter
   `https://<dein-name>.github.io/<repository>/`.

**iPhone:** Adresse in **Safari** öffnen → **Teilen** → **„Zum Home-Bildschirm“**. Danach startet Neon TD im
Vollbild wie eine App und funktioniert nach dem ersten Laden auch **ohne Internet**. Das Querformat bietet die
größte Karte; das Hochformat funktioniert ebenfalls.

Ohne GitHub Pages geht es genauso mit jedem anderen statischen Hosting: den Ordner `web/build/dist` (siehe unten)
hochladen.

### Lokal bauen und im Browser öffnen

Benötigt wird nur ein **JDK 17 oder neuer** – Gradle lädt der mitgelieferte Wrapper selbst.

```bash
./gradlew :web:assembleWeb
python3 -m http.server 8080 -d web/build/dist     # oder ein beliebiger anderer Webserver
```

Dann `http://localhost:8080` öffnen. Zum Testen auf dem iPhone im selben WLAN `http://<IP-des-PCs>:8080` aufrufen
(der Offline-Modus braucht HTTPS bzw. `localhost`, das Spiel läuft aber auch ohne ihn).

### Desktop-Anwendung

```bash
./gradlew :desktop:run                # direkt starten
./gradlew :desktop:distZip            # Paket bauen: desktop/build/distributions/neon-td-*.zip
```

Das ZIP enthält `bin/neon-td` (bzw. `neon-td.bat` unter Windows); eine Java-Laufzeit ab Version 17 muss installiert sein.
`F11` schaltet Vollbild um. Eigene Level liegen in `~/.neon-td/save.properties`.

## Bedienung

| | Maus / PC | Finger / iPhone |
|---|---|---|
| **Turm bauen** | Karte im Shop anklicken, dann auf die Karte klicken – oder vom Shop auf die Karte **ziehen** | Karte antippen und Ziel antippen – oder vom Shop **ziehen** (die Vorschau schwebt über dem Finger) |
| **Turm verbessern** | Turm anklicken → Panel mit *Reichweite / Schaden / Tempo* | Turm antippen |
| **Verkaufen** | Im Panel „Verkaufen“ (zweimal bestätigen), 70 % Erstattung | dito |
| **Zielwahl** | Im Panel: Erster · Letzter · Stärkster · Nächster | dito |
| **Wellen** | `START` / `Leertaste`; `1×/2×/3×` = `F`; Schleife = Auto-Start; Pause = `P` / `Esc` | Schaltflächen unten rechts |

Weitere Tasten: `1`–`5` Turm wählen · `Q` `W` `E` Upgrade Reichweite/Schaden/Tempo · `Tab` Zielwahl · `X`/`Entf`
verkaufen · Rechtsklick bricht ab.

## Die fünf Türme

| Turm | Preis | Reichweite | Schaden | Tempo | Rolle |
|---|---:|---:|---:|---:|---|
| **PULS** (cyan) | 100 | 150 | 4 | 2,0/s | Schneller Allrounder. Kugeln zielen auf die *vorhergesagte* Position |
| **SNIPER** (magenta) | 280 | 330 | 34 | 0,5/s | Sehr weit, sehr stark; der Strahl trifft sofort |
| **MÖRSER** (orange) | 240 | 235 | 14 | 0,6/s | Granate mit Flächenschaden (zum Rand hin schwächer) |
| **FROST** (blau) | 160 | 108 | 2 | 0,7/s | Nova verlangsamt alle Gegner in Reichweite (stärker mit Schadensstufen) |
| **BLITZ** (grün) | 220 | 142 | 5 | 1,1/s | Springt auf bis zu 4 Ziele, jeder Sprung 25 % schwächer |

Jede der drei Kategorien hat 5 Stufen: *Reichweite* bis ×1,6 · *Schaden* bis ×7 · *Tempo* bis ×3,1. Die drei farbigen
Bögen um den Turm zeigen den Ausbau (blau = Reichweite, rot = Schaden, gelb = Tempo).

## Die Gegner

| Gegner | Form | Besonderheit |
|---|---|---|
| Block | Quadrat | Standard |
| Dart | Dreieck | schnell, wenig HP |
| Splitter | gestrichelte Raute | zerfällt in 3 Minis |
| Mini | Kreis | klein und flink |
| Koloss | Sechseck | langsam, sehr zäh, kostet 3 Leben |
| Titan | Achteck | Boss, kostet 10 Leben |

Du startest mit 500 ◆ und 20 Leben. Abschüsse und besiegte Wellen bringen Geld; ein Gegner, der das Ziel erreicht,
kostet Leben.

## Level-Editor

Hauptmenü → **Level-Editor** (oder in der Levelauswahl **Neues Level**).

- **Punkte** – tippen setzt einen Punkt (nahe am Pfad wird er eingefügt, sonst hinten angehängt); Punkte lassen sich
  ziehen. Der Pfad ist ein glatter Spline durch alle Punkte und zeigt die echte Spurbreite.
- **Zeichnen** – mit Finger oder Maus freihändig einen Pfad zeichnen; er wird automatisch zu wenigen Punkten vereinfacht.
- **Löschen** – Punkte antippen, um sie zu entfernen.
- **1 · 2 · 3 · +** – bis zu drei Pfade; Gegner wechseln sich zwischen ihnen ab.
- **Rückgängig / Wiederholen**, **✕** leert den aktuellen Pfad.
- **Testen** spielt das Level sofort probe, **Speichern** legt es in der Levelauswahl ab.

Ein spielbares Level braucht mindestens einen Pfad mit mindestens 700 Einheiten Länge, alle Punkte im Spielfeld.
Die Wellen für eigene Level werden automatisch erzeugt (25 Wellen mit steigender Schwierigkeit). Gespeichert wird im
Browser (`localStorage`) bzw. auf dem Desktop in einer Datei.

## Für Entwickler

```
core/      reines Java ohne Abhängigkeiten – Simulation, Physik, Darstellung, UI, Szenen (läuft überall)
desktop/   Java2D-Fenster, Screenshot-Werkzeug, Icon-Generator
web/       TeaVM-Brücke: Java → JavaScript, Canvas2D, Zeiger-Ereignisse, PWA (Manifest, Service Worker, Icons)
```

```bash
./gradlew test                  # 47 Tests: Physik, Simulation, Balance (Bot), Speicherung, alle Szenen mit Eingaben
./gradlew build                 # alles bauen: Tests, Web (build/dist), Desktop-Paket
./gradlew :web:assembleWeb -PdebugJs      # Web-Version unminifiziert, zum Debuggen
./scripts/screenshots.sh        # Screenshots der Doku neu erzeugen (läuft ohne Fenster)
```

Wie die Physik, die Simulation und die Darstellung zusammenhängen und wie man neue Türme, Gegner, Level oder eine
Fortschritts-Schicht ergänzt, steht in **[docs/ENTWICKLUNG.md](docs/ENTWICKLUNG.md)**.

## Ausblick: Progression

Das Gerüst ist darauf vorbereitet: Der Speicher (`KeyValueStore`), die Level-Verwaltung, das Schloss-Symbol, das
Ereignis `onGameEnded` und die deterministische Simulation sind vorhanden. Naheliegende nächste Schritte:
Sterne pro Level, Freischaltung weiterer Level und Türme, Währung zwischen den Läufen, Ton, weitere Gegner- und
Turmarten, Bestenlisten und Wiederholungen aus aufgezeichneten Befehlen.

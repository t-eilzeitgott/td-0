#!/usr/bin/env bash
# Erzeugt die Screenshots für die Dokumentation – ohne Fenster, direkt aus dem Spiel (siehe ScreenshotTool).
# Aufruf aus dem Projektordner:  ./scripts/screenshots.sh
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="docs/screenshots"
rm -f "$OUT"/*.png
./gradlew --no-daemon -q :core:compileJava :desktop:compileJava 2>&1 | grep -v '^Picked up' || true
CP="core/build/classes/java/main:desktop/build/classes/java/main"
shot() { java -Djava.awt.headless=true -cp "$CP" neontd.desktop.ScreenshotTool "$OUT" "$1" 2>&1 | grep -v '^Picked up'; }

# iPhone hoch ist die Hauptausrichtung: 393×852 mit Statusleiste (59) und Home-Indikator (34).
PHONE="size 393 852 touch; insets 0 59 0 34"
# Ein Spieler mittendrin: Welt 1–3 mit Sternen, etwas Fortschritt, Kristalle.
MID="name Neo; level 16; crystals 420; starworld 1 3; starworld 2 2; stars magma-1 2; stars magma-2 1; metric kills 900; metric waves 60"
# Bauplätze in Weltkoordinaten (1280×720) neben der Bahn des Referenzlevels „Serpentine“.
PLACE="dragto tile0 300 250 0.3; dragto tile3 520 250 0.3; dragto tile2 760 250 0.3; dragto tile4 420 460 0.3; dragto tile1 900 300 0.3; dragto tile0 960 160 0.3"

# Erster Start: Intro, Spielername, Hauptmenü
shot "$PHONE; wait 1.2; shot intro; wait 1.6; shot name-phone; name Neo; wait 0.4; shot name-phone-filled"
shot "$PHONE; $MID; wait 4.2; shot menu-phone"
shot "size 1280 720; $MID; wait 4.5; shot menu"

# Welten und Level
shot "$PHONE; $MID; open select; wait 2; shot worlds-phone"
shot "$PHONE; $MID; open world:2; wait 2; shot levels-phone"
shot "size 1280 720; $MID; open select; wait 2; shot worlds"
shot "size 1280 720; $MID; open world:2; wait 2; shot levels"

# Gefecht hoch: Welt 2 (Tiefsee) mit Pads, Welle läuft; dann Upgrade-Panel und Ausbau-Fenster
shot "$PHONE; $MID; open levelrich:tiefsee-2; wait 3; tap start; wait 9; shot game-phone"
PLACE_P="dragto tile0 300 250 0.3; dragto tile3 520 250 0.3; dragto tile2 760 250 0.3; dragto tile4 420 460 0.3; dragto tile1 900 300 0.3"
shot "$PHONE; level 16; open gamerich; wait 3; $PLACE_P; tap start; wait 8; shot phone-portrait; wclick 300 250; wait 0.5; tap track1; wait 0.6; shot phone-portrait-panel; tap track1; tap track1; wait 0.3; tap special; wait 1; shot ausbau-phone"

# Gefecht quer (PC) und Endlosmodus
shot "size 1280 720; level 16; open gamerich; wait 3.5; $PLACE; tap start; wait 5; key Space; wait 6; key Space; wait 5; key Space; wait 8.8; shot game"
shot "size 1280 720; level 16; open gamerich; wait 3.5; $PLACE; wait 0.5; wclick 300 250; wait 0.4; tap track1; wait 0.3; tap track2; wait 4; shot upgrade"
shot "size 1280 720; level 16; open endless; wait 3.5; $PLACE; tap start; wait 4; tap speed; tap speed; tap speed; tap auto; wait 30; shot endless"

# Arena (Wochen-Wettkampf) mit Beispieldaten: Rangliste, Verlauf, Ruhmeshalle, Lauf und Ergebnis
shot "$PHONE; $MID; open arena; wait 1.5; shot arena-phone; click 196 137; wait 1.5; shot arena-feed-phone; click 330 137; wait 1.5; shot arena-hall-phone"
shot "size 1280 720; $MID; open arena; wait 1.5; shot arena"
ARENA_PLACE="dragto tile0 520 360 0.3; dragto tile3 520 200 0.3; dragto tile3 920 200 0.3"
shot "$PHONE; $MID; crystals 0; open arenagame; wait 3; $ARENA_PLACE; wait 0.3; tap start; tap speed; tap speed; wait 5.5; shot arena-run-phone; wait 4; tap pause; wait 0.6; tap ov2; wait 3; shot arena-result-phone"

# Sieg mit Sternen
shot "$PHONE; $MID; open levelrich:stadt-3; wait 3; tap start; wait 3; win; wait 2.5; shot win-phone"

# Tages-Challenge, Bestenliste, Profil
shot "$PHONE; $MID; open daily; wait 2; shot daily-phone"
shot "$PHONE; $MID; open board; wait 2; shot board-phone"
shot "$PHONE; $MID; open profile; wait 1.5; shot profile"

# Shop, Missionen, Season, Errungenschaften, Einstellungen, Editor
shot "$PHONE; level 30; crystals 800; metric kills 300; open shop; wait 2.5; shot shop-phone"
shot "size 1280 720; level 24; crystals 1500; open shop; wait 2.5; shot shop-towers"
shot "size 1280 720; level 24; crystals 1500; open shop2; wait 2.5; shot shop-specials"
shot "size 1280 720; level 24; crystals 1500; open shop3; wait 2.5; shot shop-research"
shot "size 1280 720; metric kills 300; metric waves 11; open missions; wait 2.5; shot missions"
shot "size 1280 720; metric kills 300; open season; wait 2.5; shot season"
shot "size 1280 720; $MID; metric kills 600; open achievements; wait 2.5; shot achievements"
shot "size 1280 720; open settings; wait 2.5; shot settings"
EDIT="click 60 200; click 300 200; click 430 330; click 300 450; click 150 520; click 420 575; click 700 500; click 860 330; wait 0.4"
shot "size 1280 720; open editor; wait 1.2; $EDIT; shot editor"

# Elite-Türme und Spezial-Auswahl
ELITE="tap pager; wait 0.5; dragto tile0 300 250 0.3; dragto tile1 520 250 0.3; dragto tile2 760 250 0.3; dragto tile3 420 460 0.3; dragto tile4 900 300 0.3; dragto tile6 960 160 0.3; wait 0.3; tap pager; wait 0.3; dragto tile0 640 520 0.3"
shot "size 1280 720; level 60; ownall; open gamerich; wait 3; $ELITE; wait 0.5; tap start; wait 6; shot elite-towers"
shot "size 1280 720; level 60; ownall; open gamerich; wait 3; $ELITE; wait 0.5; wclick 300 250; wait 0.5; tap special; wait 1; shot special-choice"

# Übersicht aller 48 Strecken
java -Djava.awt.headless=true -cp "$CP" neontd.desktop.LevelSheet "$OUT" 2>&1 | grep -v '^Picked up'

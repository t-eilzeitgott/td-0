#!/usr/bin/env bash
# Erzeugt die Screenshots für die Dokumentation – ohne Fenster, direkt aus dem Spiel (siehe ScreenshotTool).
# Aufruf aus dem Projektordner:  ./scripts/screenshots.sh
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="docs/screenshots"
./gradlew --no-daemon -q :core:compileJava :desktop:compileJava 2>&1 | grep -v '^Picked up' || true
CP="core/build/classes/java/main:desktop/build/classes/java/main"
shot() { java -Djava.awt.headless=true -cp "$CP" neontd.desktop.ScreenshotTool "$OUT" "$1" 2>&1 | grep -v '^Picked up'; }

# Bauplätze in Weltkoordinaten (1280×720) neben der Bahn; die Kacheln stehen für PULS, SNIPER, MÖRSER, FROST, BLITZ.
# "dragto KACHEL x y SEK" zieht von der Kachel auf den Weltpunkt (auf dem Handy mit Fingerversatz).
PLACE="dragto tile0 300 250 0.3; dragto tile3 520 250 0.3; dragto tile2 760 250 0.3; dragto tile4 420 460 0.3; dragto tile1 900 300 0.3; dragto tile0 960 160 0.3"

# Hauptmenü mit laufender Demo und Profilkarte
shot "size 1280 720; level 7; wait 0.5; wait 9; shot menu"

# Spiel: Gefecht und Upgrade-Panel (Level 16 = alle Türme frei)
shot "size 1280 720; level 16; open gamerich; wait 3.5; $PLACE; tap start; wait 5; key Space; wait 6; key Space; wait 5; key Space; wait 8.8; shot game"
shot "size 1280 720; level 16; open gamerich; wait 3.5; $PLACE; wait 0.5; wclick 300 250; wait 0.4; tap track1; wait 0.3; tap track2; wait 0.8; shot upgrade"

# Level-Editor und Levelauswahl mit einem gespeicherten eigenen Level
EDIT="click 60 200; click 300 200; click 430 330; click 300 450; click 150 520; click 420 575; click 700 500; click 860 330; wait 0.4"
shot "size 1280 720; open editor; wait 1.2; $EDIT; shot editor"
shot "size 1280 720; best serpentine 120; best zickzack 60; best spirale 22; open select; wait 2.5; shot levels"
shot "size 393 852 touch; insets 0 59 0 34; best serpentine 260; best zickzack 60; best spirale 22; open select; wait 2.5; shot levels-phone"

# Profil
shot "size 393 852 touch; insets 0 59 0 34; level 7; open profile; wait 1.5; shot profile"

# iPhone quer (mit Notch-Rand): Leiste ausgeklappt, eingeklappt und mit Upgrade-Panel
shot "size 852 393 touch; insets 59 0 59 21; level 16; open gamerich; wait 3; $PLACE; tap start; wait 8; shot phone-landscape; tap toggle; wait 1.2; shot phone-landscape-collapsed; tap toggle; wait 1; wclick 300 250; wait 0.5; tap track1; wait 0.6; shot phone-landscape-panel"

# iPhone hoch (mit Statusleiste und Home-Indikator): gedrehte Karte, Leiste unten
shot "size 393 852 touch; insets 0 59 0 34; level 16; open gamerich; wait 3; $PLACE; tap start; wait 8; shot phone-portrait; wclick 300 250; wait 0.5; tap track1; wait 0.6; shot phone-portrait-panel"

# Endlosmodus (Meisterstufen, Tempo 5x)
shot "size 1280 720; level 16; open endless; wait 3.5; $PLACE; tap start; wait 4; tap speed; tap speed; tap speed; tap auto; wait 30; shot endless"

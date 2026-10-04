#!/usr/bin/env bash
# Erzeugt die Screenshots für die Dokumentation – ohne Fenster, direkt aus dem Spiel (siehe ScreenshotTool).
# Aufruf aus dem Projektordner:  ./scripts/screenshots.sh
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="docs/screenshots"
./gradlew --no-daemon -q :core:compileJava :desktop:compileJava 2>&1 | grep -v '^Picked up' || true
CP="core/build/classes/java/main:desktop/build/classes/java/main"
shot() { java -Djava.awt.headless=true -cp "$CP" neontd.desktop.ScreenshotTool "$OUT" "$1" 2>&1 | grep -v '^Picked up'; }

# Positionen: Karten im Shop (Desktop 1280x720), Bauplätze neben der Bahn
PLACE="drag 1100 157 222 275 0.3; drag 1100 337 475 275 0.3; drag 1100 427 603 268 0.3; drag 1100 517 405 141 0.3; drag 1100 157 700 215 0.3; drag 1100 247 349 289 0.3"

# Hauptmenü mit laufender Demo
shot "size 1280 720; wait 0.5; wait 9; shot menu"

# Spiel: Gefecht und Upgrade-Panel
shot "size 1280 720; open gamerich; wait 3.5; $PLACE; click 1000 662; wait 5; key Space; wait 6; key Space; wait 5; key Space; wait 8.8; shot game"
shot "size 1280 720; open gamerich; wait 3.5; $PLACE; wait 0.5; click 222 275; wait 0.4; click 1190 270; wait 0.3; click 1190 190; wait 0.8; shot upgrade"

# Level-Editor und Levelauswahl mit einem gespeicherten eigenen Level
EDIT="click 60 200; click 300 200; click 430 330; click 300 450; click 150 520; click 420 575; click 700 500; click 860 330; wait 0.4"
shot "size 1280 720; open editor; wait 1.2; $EDIT; shot editor; key KeyS ctrl; wait 0.4; open select; wait 1.8; shot levels"

# iPhone quer (mit Notch-Rand) und hoch (mit Statusleiste und Home-Indikator)
shot "size 852 393 touch; insets 47 0 47 21; open gamerich; wait 3.5; drag 700 93 180 183 0.4; drag 700 190 256 187 0.4; drag 700 240 332 179 0.4; wait 0.3; click 666 340; wait 8; shot phone-landscape"
shot "size 393 852 touch; insets 0 47 0 34; open gamerich; wait 3.5; drag 40 350 95 208 0.4; drag 196 350 150 215 0.4; drag 274 350 190 200 0.4; wait 0.3; click 78 791; wait 8; shot phone-portrait"

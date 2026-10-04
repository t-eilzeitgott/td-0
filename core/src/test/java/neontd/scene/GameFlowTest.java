package neontd.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neontd.level.LevelDef;
import neontd.level.Levels;
import neontd.platform.MemoryStore;
import neontd.progress.Progress;
import neontd.save.RunSave;
import neontd.scene.SceneSmokeTest.Harness;
import neontd.sim.Tower;
import neontd.sim.TowerType;
import neontd.sim.UpgradeTrack;
import neontd.sim.World;
import org.junit.jupiter.api.Test;

/** Spielfluss in der Szene: Sperren, XP, Speichern/Fortsetzen, Endlos, Handy-Leiste, gedrehte Karte. */
class GameFlowTest {
    /** Ein baubarer Platz nahe {@code (x, y)}. */
    static double[] validSpot(World w, double x, double y) {
        double best = Double.MAX_VALUE;
        double[] spot = null;
        for (double gy = 40; gy < w.height - 30; gy += 6) {
            for (double gx = 40; gx < w.width - 30; gx += 6) {
                if (w.checkPlacement(gx, gy) == World.PlaceCheck.OK) {
                    double d = Math.hypot(gx - x, gy - y);
                    if (d < best) {
                        best = d;
                        spot = new double[] {gx, gy};
                    }
                }
            }
        }
        assertNotNull(spot);
        return spot;
    }

    private static GameScene open(Harness h) {
        h.app.goTo(new GameScene(h.app, Levels.serpentine()));
        h.run(1.5);
        return (GameScene) h.app.scene();
    }

    private static void setLevel(Harness h, int level) {
        h.app.progress.xp = Progress.xpAtLevel(level) + 5;
    }

    private static void dragTile(Harness h, GameScene gs, int tile, double wx, double wy) {
        double[] from = gs.anchor("tile" + tile);
        double[] to = gs.anchor("world:" + wx + "," + wy);
        h.drag(from[0], from[1], to[0], to[1] + (h.touch ? 56 * h.app.vp.u : 0));
        confirmIfFloating(h, gs);
    }

    /** Auf dem Finger-Gerät schwebt der Turm nach dem Loslassen und wird mit dem Haken fest gebaut. */
    static void confirmIfFloating(Harness h, GameScene gs) {
        double[] ok = gs.anchor("confirm");
        if (h.touch) {
            assertNotNull(ok, "Turm schwebt nach dem Loslassen");
            h.tap(ok);
        }
    }

    @Test
    void lockedTowersCannotBeBuiltUntilTheLevelIsReached() {
        Harness h = new Harness(1280, 720, false);
        GameScene gs = open(h);
        World w = gs.testWorld();
        double[] spot = validSpot(w, 400, 250);
        // Level 1: nur PULS
        for (int tile : new int[] {1, 2, 3, 4}) {
            dragTile(h, gs, tile, spot[0], spot[1]);
            assertEquals(0, w.towers.size(), "Turm " + TowerType.values()[tile] + " ist gesperrt");
        }
        h.app.key("Digit4", false); // FROST per Taste
        h.tap(gs.anchor("world:" + spot[0] + "," + spot[1]));
        assertEquals(0, w.towers.size());
        dragTile(h, gs, 0, spot[0], spot[1]);
        assertEquals(1, w.towers.size());
        assertEquals(TowerType.PULSE, w.towers.get(0).type);

        // Level 2 schaltet FROST frei (Kachel 3), die anderen bleiben gesperrt
        setLevel(h, 2);
        double[] spot2 = validSpot(w, 700, 250);
        dragTile(h, gs, 3, spot2[0], spot2[1]);
        assertEquals(2, w.towers.size());
        assertEquals(TowerType.FROST, w.towers.get(1).type);
        double[] spot3 = validSpot(w, 300, 300);
        dragTile(h, gs, 4, spot3[0], spot3[1]);
        assertEquals(2, w.towers.size(), "BLITZ ist erst ab Level 6 frei");
    }

    @Test
    void touchPlacementFloatsUntilConfirmed() {
        Harness h = new Harness(393, 852, true, 0, 47, 0, 34);
        GameScene gs = open(h);
        World w = gs.testWorld();
        double[] spot = validSpot(w, 400, 250);
        double[] from = gs.anchor("tile0");
        double[] to = gs.anchor("world:" + spot[0] + "," + spot[1]);
        h.drag(from[0], from[1], to[0], to[1] + 56 * h.app.vp.u);
        assertEquals(0, w.towers.size(), "nach dem Loslassen noch nicht gebaut");
        // Verschieben: erneut auf die Karte ziehen
        double[] spot2 = validSpot(w, 800, 400);
        double[] to2 = gs.anchor("world:" + spot2[0] + "," + spot2[1]);
        // Den schwebenden Turm direkt anfassen und ziehen: kein Sprung, er folgt dem Finger genau.
        h.drag(to[0], to[1] + 56 * h.app.vp.u - 56 * h.app.vp.u, to2[0], to2[1]);
        assertEquals(0, w.towers.size());
        h.tap(gs.anchor("confirm"));
        assertEquals(1, w.towers.size());
        assertEquals(spot2[0], w.towers.get(0).x, 1.5);
        // Antippen der Karte setzt ebenfalls nur die Vorschau; Abbrechen baut nichts
        h.tap(gs.anchor("tile0"));
        double[] p3 = gs.anchor("world:" + validSpot(w, 300, 250)[0] + "," + validSpot(w, 300, 250)[1]);
        h.tap(p3[0], p3[1] + 56 * h.app.vp.u);
        assertEquals(1, w.towers.size());
        h.tap(gs.anchor("cancel"));
        assertEquals(1, w.towers.size());
        assertNull(gs.anchor("confirm"));
    }

    @Test
    void levelBonusRaisesStartMoneyAndLives() {
        Harness h = new Harness(1280, 720, false);
        GameScene g0 = open(h);
        assertEquals(500, g0.testWorld().money);
        assertEquals(20, g0.testWorld().lives);
        setLevel(h, 12);
        GameScene g1 = open(h);
        assertEquals(500 + 110, g1.testWorld().money);
        assertEquals(22, g1.testWorld().lives);
    }

    @Test
    void clearedWavesGiveXpAndAreStored() {
        Harness h = new Harness(1280, 720, false);
        GameScene gs = open(h);
        World w = gs.testWorld();
        int[][] spots = {{300, 250}, {520, 250}, {760, 250}, {420, 450}};
        for (int[] s : spots) {
            double[] p = validSpot(w, s[0], s[1]);
            assertNotNull(w.placeTower(TowerType.PULSE, p[0], p[1]));
        }
        assertEquals(0, h.app.progress.xp);
        h.app.key("Space", false);
        h.run(50);
        assertTrue(h.app.progress.wavesCleared >= 1, "Wellen: " + h.app.progress.wavesCleared);
        assertEquals(Progress.waveXp(1, false), h.app.progress.xp);
        assertTrue(h.app.progress.kills > 0);
        assertNotNull(h.store.get("neontd.profile"), "Profil wird gespeichert");
        Progress stored = h.app.saves.loadProgress();
        assertEquals(h.app.progress.xp, stored.xp);
        assertTrue(stored.updated > 0);
    }

    @Test
    void autosaveCanBeResumedAfterARestart() {
        Harness h = new Harness(1280, 720, false);
        GameScene gs = open(h);
        World w = gs.testWorld();
        for (int[] s : new int[][] {{300, 250}, {520, 250}, {760, 250}}) {
            double[] p = validSpot(w, s[0], s[1]);
            assertNotNull(w.placeTower(TowerType.PULSE, p[0], p[1]));
        }
        h.app.key("Space", false);
        h.run(40);
        RunSave run = h.app.saves.resumable("serpentine");
        assertNotNull(run, "Lauf wird zwischengespeichert");
        assertEquals(3, run.snapshot.towers.size());
        assertTrue(run.t > 0);

        // "Neustart": neue App auf demselben Speicher
        Harness h2 = new Harness(h.store, 1280, 720, false);
        RunSave loaded = h2.app.saves.resumable("serpentine");
        assertNotNull(loaded);
        h2.app.goTo(GameScene.resume(h2.app, Levels.serpentine(), loaded));
        h2.run(1.5);
        GameScene g2 = (GameScene) h2.app.scene();
        assertEquals(3, g2.testWorld().towers.size());
        assertEquals(loaded.snapshot.waveIndex, g2.testWorld().waveIndex);
        assertEquals(loaded.snapshot.money, g2.testWorld().money);
        // Startet pausiert; "Weiter" setzt fort
        int wave = g2.testWorld().waveIndex;
        double t0 = g2.testWorld().time;
        h2.run(2);
        assertEquals(t0, g2.testWorld().time, 1e-9, "Pause: Zeit steht");
        double[] cont = g2.anchor("ov0");
        h2.tap(cont[0], cont[1]);
        h2.run(5);
        assertTrue(g2.testWorld().time > t0);
        assertTrue(g2.testWorld().waveIndex >= wave);
    }

    @Test
    void winningOffersEndlessAndUnlocksIt() {
        Harness h = new Harness(1280, 720, false);
        LevelDef def = Levels.serpentine().copy();
        def.builtin = false;
        def.id = "kurz";
        def.waveCount = 2;
        def.startMoney = 100000;
        h.app.goTo(new GameScene(h.app, def));
        h.run(1.5);
        GameScene gs = (GameScene) h.app.scene();
        World w = gs.testWorld();
        for (int[] s : new int[][] {{300, 250}, {520, 250}, {760, 250}, {420, 450}, {900, 300}}) {
            double[] p = validSpot(w, s[0], s[1]);
            Tower t = w.placeTower(TowerType.PULSE, p[0], p[1]);
            assertNotNull(t);
            for (UpgradeTrack tr : UpgradeTrack.values()) {
                w.upgrade(t, tr);
                w.upgrade(t, tr);
            }
        }
        assertFalse(h.app.progress.endlessUnlocked("kurz"));
        w.autoStart = true;
        h.app.key("Space", false);
        h.run(120);
        assertEquals(World.State.WON, w.state);
        assertTrue(h.app.progress.endlessUnlocked("kurz"));
        assertEquals(1, h.app.progress.gamesWon);
        assertNull(h.app.saves.resumable("kurz"), "beendeter Lauf bleibt nicht fortsetzbar");
        int xpBefore = h.app.progress.xp;
        assertTrue(xpBefore >= Progress.WIN_XP + Progress.FIRST_WIN_XP);

        // Einblendung: ENDLOS WEITER ist die erste Schaltfläche
        double[] cont = gs.anchor("ov0");
        assertNotNull(cont);
        h.tap(cont[0], cont[1]);
        h.run(1);
        assertTrue(w.endless);
        assertEquals(World.State.RUNNING, w.state);
        assertTrue(w.canStartWave());
        // Tempo 5x gibt es jetzt
        double[] sp = gs.anchor("speed");
        for (int i = 0; i < 3; i++) {
            h.tap(sp[0], sp[1]);
        }
        h.run(6);
        assertTrue(w.waveIndex >= 3, "läuft im Endlosmodus weiter: Welle " + w.waveIndex);
        // Meisterstufen sind jetzt kaufbar
        Tower any = w.towers.get(0);
        w.money = 1_000_000;
        assertTrue(w.upgradeCost(any, UpgradeTrack.DAMAGE) > 0);
    }

    @Test
    void levelSelectOffersContinueAndEndlessWhenAvailable() {
        Harness h = new Harness(852, 393, true);
        // Ohne Lauf und ohne Sieg: direkt ins Spiel
        h.app.goTo(new LevelSelectScene(h.app));
        h.run(1.5);
        h.tap(320, 130);
        h.run(1.5);
        assertEquals("GameScene", h.scene());

        // Mit freigeschaltetem Endlos: Dialog statt Direktstart
        h.app.progress.won.add("serpentine");
        h.app.goTo(new LevelSelectScene(h.app));
        h.run(1.5);
        h.tap(320, 130);
        h.run(1.0);
        assertEquals("LevelSelectScene", h.scene(), "Dialog erscheint");
        h.app.back();
        h.run(0.5);
        assertEquals("LevelSelectScene", h.scene());
    }

    @Test
    void phoneRailCollapsesAndExpands() {
        Harness h = new Harness(852, 393, true, 59, 0, 59, 21);
        GameScene gs = open(h);
        GameLayout lay = gs.layoutInfo();
        assertEquals(GameLayout.Mode.COMPACT_LAND, lay.mode);
        double openScale = lay.mapScale;
        assertTrue(lay.open > 0.99);
        double[] t = gs.anchor("toggle");
        h.tap(t[0], t[1]);
        h.run(1.0);
        assertTrue(lay.open < 0.02, "eingeklappt");
        assertTrue(lay.mapScale > openScale, "Karte wächst: " + openScale + " → " + lay.mapScale);
        // Eingeklappt gibt es keine Turmkacheln zum Ziehen
        World w = gs.testWorld();
        double[] spot = validSpot(w, 400, 250);
        double[] tile = gs.anchor("tile0");
        double[] to = gs.anchor("world:" + spot[0] + "," + spot[1]);
        h.drag(tile[0], tile[1], to[0], to[1] + 56 * h.app.vp.u);
        assertEquals(0, w.towers.size());
        // Zustand bleibt gemerkt
        assertEquals("0", h.store.get("neontd.ui.rail"));
        h.tap(t[0], t[1]);
        h.run(1.0);
        assertTrue(lay.open > 0.99);
        h.drag(tile[0], tile[1], to[0], to[1] + 56 * h.app.vp.u);
        confirmIfFloating(h, gs);
        assertEquals(1, w.towers.size());
        // Neue Szene übernimmt die Wahl
        h.tap(t[0], t[1]);
        h.run(1.0);
        GameScene again = open(h);
        assertTrue(again.layoutInfo().open < 0.02);
    }

    @Test
    void rotatedPortraitMapPlacesTowersWhereTheyAreDropped() {
        Harness h = new Harness(393, 852, true, 0, 47, 0, 34);
        GameScene gs = open(h);
        GameLayout lay = gs.layoutInfo();
        assertEquals(GameLayout.Mode.COMPACT_PORT, lay.mode);
        assertTrue(lay.rotated);
        World w = gs.testWorld();
        for (double[] want : new double[][] {{400, 250}, {900, 330}, {250, 500}}) {
            double[] spot = validSpot(w, want[0], want[1]);
            int before = w.towers.size();
            dragTile(h, gs, 0, spot[0], spot[1]);
            assertEquals(before + 1, w.towers.size(), "Turm gesetzt bei " + spot[0] + "," + spot[1]);
            Tower t = w.towers.get(w.towers.size() - 1);
            assertEquals(spot[0], t.x, 1.0);
            assertEquals(spot[1], t.y, 1.0);
        }
        // Antippen wählt den Turm (Panel ersetzt die Turmleiste), erneutes Antippen der Karte hebt die Wahl auf
        Tower t0 = w.towers.get(0);
        double[] at = gs.anchor("world:" + t0.x + "," + t0.y);
        h.tap(at[0], at[1]);
        assertTrue(lay.panelReplacesTiles);
        double money = w.money;
        double[] up = gs.anchor("track1");
        h.tap(up[0], up[1]);
        assertTrue(w.money < money, "Upgrade gekauft");
        h.run(1);
        h.app.back();
        h.run(0.5);
        assertFalse(lay.panelReplacesTiles);
        // Verkaufen (zweimal bestätigen)
        h.tap(at[0], at[1]);
        double[] sell = gs.anchor("sell");
        int n = w.towers.size();
        h.tap(sell[0], sell[1]);
        h.tap(sell[0], sell[1]);
        assertEquals(n - 1, w.towers.size());
    }

    @Test
    void landscapePopoverPanelWorksAndAvoidsTheTower() {
        Harness h = new Harness(852, 393, true, 59, 0, 59, 21);
        GameScene gs = open(h);
        World w = gs.testWorld();
        double[] spot = validSpot(w, 1100, 300);
        assertNotNull(w.placeTower(TowerType.PULSE, spot[0], spot[1]));
        double[] at = gs.anchor("world:" + spot[0] + "," + spot[1]);
        h.tap(at[0], at[1]);
        h.run(0.3);
        GameLayout lay = gs.layoutInfo();
        assertFalse(lay.panel.intersectsCircle(at[0], at[1], 20), "Panel verdeckt den Turm nicht");
        double money = w.money;
        double[] tr = gs.anchor("track0");
        h.tap(tr[0], tr[1]);
        assertTrue(w.money < money);
        h.tap(tr[0], tr[1]);
        h.run(0.5);
        assertEquals(2, w.towers.get(0).level[UpgradeTrack.RANGE.ordinal()]);
    }

    @Test
    void profileSceneEditsTheName() {
        Harness h = new Harness(393, 852, true, 0, 47, 0, 34);
        h.app.goTo(new ProfileScene(h.app));
        h.run(1.5);
        h.promptAnswer = title -> "  Anna  <b>  ";
        // Name liegt oben in der Kopfkarte
        h.tap(250, 130);
        h.run(0.5);
        assertEquals("Anna  b", h.app.progress.name);
        assertEquals("Anna  b", h.app.saves.loadProgress().name);
        // Scrollen bis ans Ende rendert ohne Fehler
        for (int i = 0; i < 20; i++) {
            h.app.wheel(120);
            h.run(0.05);
        }
        // Verbinden ohne Netzwerk-Unterstützung meldet das, ohne abzustürzen
        h.promptAnswer = title -> "ghp_x";
        h.run(0.2);
        assertFalse(h.app.cloud.connected());
    }

    @Test
    void compactEditorDrawsPathsOnTheRotatedMapAndSaves() {
        for (double[] size : new double[][] {{393, 852, 0, 47, 0, 34}, {852, 393, 47, 0, 47, 21}}) {
            Harness h = new Harness(size[0], size[1], true, size[2], size[3], size[4], size[5]);
            h.app.goTo(new EditorScene(h.app, null));
            h.run(1.5);
            EditorScene ed = (EditorScene) h.app.scene();
            double[][] pts = {{100, 150}, {400, 150}, {700, 300}, {400, 500}, {150, 600}, {600, 650}};
            for (double[] p : pts) {
                double[] sc = ed.worldToScreen(p[0], p[1]);
                h.tap(sc[0], sc[1]);
            }
            h.run(0.3);
            // gespeichert über Strg+S: der Pfad liegt dort, wo getippt wurde (Umrechnung stimmt in beiden Ausrichtungen)
            assertTrue(h.app.key("KeyS", true));
            h.run(0.3);
            assertEquals(1, h.app.levels.loadAll().size());
            LevelDef saved = h.app.levels.loadAll().get(0);
            assertNull(saved.validate());
            double[] first = saved.paths.get(0);
            assertEquals(pts.length * 2, first.length);
            for (int i = 0; i < pts.length; i++) {
                assertEquals(Math.floor(pts[i][0] / 10 + 0.5) * 10, first[i * 2], 1.0, "x" + i + " " + size[0]);
                assertEquals(Math.floor(pts[i][1] / 10 + 0.5) * 10, first[i * 2 + 1], 1.0, "y" + i + " " + size[0]);
            }
        }
    }
}

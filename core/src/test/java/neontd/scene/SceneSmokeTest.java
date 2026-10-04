package neontd.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neontd.app.App;
import neontd.gfx.Gfx;
import neontd.level.LevelDef;
import neontd.level.Levels;
import neontd.platform.KeyValueStore;
import neontd.platform.MemoryStore;
import neontd.platform.Platform;
import org.junit.jupiter.api.Test;

/**
 * Spielt die Szenen ohne Bildschirm durch (Leer-Zeichner), in Hoch-/Quer-/Desktop-Größe, mit Tippen, Ziehen und
 * Tasten. Fängt Abstürze und Layout-Fehler früh ab, die sonst erst auf dem Gerät auffallen würden.
 */
class SceneSmokeTest {
    /** Zeichner, der nichts malt, aber Aufrufe zählt und grobe Fehler (NaN, Stapelungleichgewicht) erkennt. */
    static final class NullGfx implements Gfx {
        long calls;
        int depth;
        double w = 1280;
        double h = 720;

        private void check(double... v) {
            calls++;
            for (double d : v) {
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    throw new AssertionError("Ungültiger Zeichenwert: " + d);
                }
            }
        }

        @Override public double width() { return w; }
        @Override public double height() { return h; }
        @Override public void save() { depth++; calls++; }
        @Override public void restore() { depth--; calls++; assertTrue(depth >= 0, "restore ohne save"); }
        @Override public void translate(double x, double y) { check(x, y); }
        @Override public void rotate(double radians) { check(radians); }
        @Override public void scale(double sx, double sy) { check(sx, sy); }
        @Override public void alpha(double a) { check(a); }
        @Override public void additive(boolean on) { calls++; }
        @Override public void clipRect(double x, double y, double w, double h) { check(x, y, w, h); }
        @Override public void beginPath() { calls++; }
        @Override public void moveTo(double x, double y) { check(x, y); }
        @Override public void lineTo(double x, double y) { check(x, y); }
        @Override public void arc(double cx, double cy, double r, double a0, double a1) { check(cx, cy, r, a0, a1); }
        @Override public void closePath() { calls++; }
        @Override public void fill(int color) { calls++; }
        @Override public void stroke(double width, int color) { check(width); }
        @Override public void strokeDashed(double width, int color, double dash, double gap, double phase) {
            check(width, dash, gap, phase);
        }
        @Override public void radialGlow(double cx, double cy, double r, int color) { check(cx, cy, r); }
        @Override public void text(String s, double x, double y, double size, int color, int align, boolean bold) {
            assertNotNull(s);
            check(x, y, size);
        }
        @Override public double textWidth(String s, double size, boolean bold) { return s.length() * size * 0.55; }
    }

    static final class Harness {
        final MemoryStore store;
        final NullGfx gfx = new NullGfx();
        final App app;
        double now;
        double clock = 1_700_000_000_000.0;
        final boolean touch;
        /** Antwort auf Eingabeaufforderungen (Name, Token, Code); {@code null} = abgebrochen. */
        java.util.function.Function<String, String> promptAnswer = title -> null;

        Harness(double w, double h, boolean touch, double... insets) {
            this(new MemoryStore(), w, h, touch, insets);
        }

        Harness(MemoryStore store, double w, double h, boolean touch, double... insets) {
            this.store = store;
            this.touch = touch;
            app = new App(new Platform() {
                @Override public KeyValueStore store() { return store; }
                @Override public boolean touchPrimary() { return touch; }
                @Override public double nowMillis() { return clock; }
                @Override public void prompt(String title, String initial, java.util.function.Consumer<String> result) {
                    result.accept(promptAnswer.apply(title));
                }
            });
            double[] in = insets.length == 4 ? insets : new double[4];
            resize(w, h, in);
        }

        void resize(double w, double h, double[] in) {
            gfx.w = w;
            gfx.h = h;
            app.resize(w, h, in[0], in[1], in[2], in[3]);
        }

        void run(double seconds) {
            int n = (int) Math.round(seconds * 60);
            for (int i = 0; i < n; i++) {
                now += 1.0 / 60;
                app.update(now);
                app.render(gfx);
                assertEquals(0, gfx.depth, "save/restore nicht ausgeglichen in " + app.scene().getClass().getSimpleName());
            }
        }

        void tap(double x, double y) {
            app.pointerMove(x, y, false, touch);
            app.pointerDown(x, y, touch);
            run(0.06);
            app.pointerUp(x, y, touch);
            run(0.06);
        }

        void tap(double[] p) {
            tap(p[0], p[1]);
        }

        void drag(double x1, double y1, double x2, double y2) {
            app.pointerDown(x1, y1, touch);
            for (int i = 1; i <= 12; i++) {
                double t = i / 12.0;
                app.pointerMove(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, true, touch);
                run(0.02);
            }
            app.pointerUp(x2, y2, touch);
            run(0.06);
        }

        String scene() {
            return app.scene().getClass().getSimpleName();
        }
    }

    private static final double[][] SIZES = {
        {1280, 720}, {1920, 1080}, {852, 393}, {393, 852}, {568, 320}, {1024, 1366}
    };

    @Test
    void everySceneRendersAtEverySize() {
        for (double[] s : SIZES) {
            Harness h = new Harness(s[0], s[1], s[0] < 900 || s[1] > s[0], 0, 0, 0, 0);
            h.run(1.0);
            assertEquals("MenuScene", h.scene());
            h.app.goTo(new LevelSelectScene(h.app));
            h.run(1.5);
            assertEquals("LevelSelectScene", h.scene());
            h.app.goTo(new GameScene(h.app, Levels.serpentine()));
            h.run(1.5);
            assertEquals("GameScene", h.scene());
            h.app.goTo(new ProfileScene(h.app));
            h.run(1.5);
            assertEquals("ProfileScene", h.scene());
            h.app.goTo(new EditorScene(h.app, null));
            h.run(1.5);
            assertEquals("EditorScene", h.scene());
        }
    }

    @Test
    void safeAreaInsetsDoNotBreakLayout() {
        Harness h = new Harness(852, 393, true, 47, 0, 47, 21);
        h.app.goTo(new GameScene(h.app, Levels.serpentine()));
        h.run(1.5);
        h.resize(393, 852, new double[] {0, 47, 0, 34});
        h.run(0.5);
        h.resize(852, 393, new double[] {47, 0, 47, 21});
        h.run(0.5);
    }

    @Test
    void menuToGameFlowWithTouch() {
        Harness h = new Harness(852, 393, true);
        h.run(1.0);
        h.tap(426, 220); // SPIELEN
        h.run(1.2);
        assertEquals("LevelSelectScene", h.scene());
        h.tap(320, 130); // Level 1
        h.run(1.5);
        assertEquals("GameScene", h.scene());
        // Turm ziehen, Welle starten, laufen lassen
        GameScene gs = (GameScene) h.app.scene();
        double[] spot = GameFlowTest.validSpot(gs.testWorld(), 400, 250);
        double[] from = gs.anchor("tile0");
        double[] to = gs.anchor("world:" + spot[0] + "," + spot[1]);
        h.drag(from[0], from[1], to[0], to[1] + 56 * h.app.vp.u);
        assertEquals(1, gs.testWorld().towers.size());
        double[] start = gs.anchor("start");
        h.tap(start[0], start[1]);
        assertEquals(1, gs.testWorld().waveIndex);
        h.run(20);
        h.app.back(); // Pause
        h.run(0.6);
        h.app.back(); // weiter
        h.run(0.3);
        assertEquals("GameScene", h.scene());
    }

    @Test
    void gameKeyboardSteering() {
        Harness h = new Harness(1280, 720, false);
        h.app.goTo(new GameScene(h.app, Levels.serpentine()));
        h.run(1.5);
        assertTrue(h.app.key("Digit1", false));
        h.app.pointerMove(420, 262, false, false);
        h.run(0.1);
        h.tap(420, 262);
        assertTrue(h.app.key("Space", false)); // Welle starten
        h.run(0.5);
        assertTrue(h.app.key("KeyF", false)); // Tempo
        h.run(10);
        assertFalse(h.app.key("KeyJ", false)); // unbekannte Taste wird nicht verschluckt
    }

    @Test
    void editorBuildSaveAndReload() {
        Harness h = new Harness(1280, 720, false);
        h.app.goTo(new EditorScene(h.app, null));
        h.run(1.5);
        double[][] pts = {{60, 200}, {300, 200}, {430, 330}, {300, 450}, {150, 520}, {420, 575}, {700, 500}, {860, 330}};
        for (double[] p : pts) {
            h.tap(p[0], p[1]);
        }
        h.run(0.3);
        assertTrue(h.app.key("KeyZ", true)); // Rückgängig
        h.tap(860, 330); // wieder setzen
        assertTrue(h.app.key("KeyS", true)); // Speichern
        h.run(0.5);
        assertEquals(1, h.app.levels.loadAll().size());
        LevelDef saved = h.app.levels.loadAll().get(0);
        assertNull(saved.validate());
        assertEquals(1, saved.paths.size());
        // Freihand zeichnen
        assertTrue(h.app.key("KeyD", false));
        h.drag(60, 560, 880, 480);
        h.run(0.3);
        // zurück: ungespeicherte Änderung fragt nach, Esc bricht ab, nochmal Esc verlässt den Dialog
        h.app.back();
        h.run(0.8);
        h.app.back();
        h.run(0.3);
        assertEquals("EditorScene", h.scene());
    }

    private static void assertNull(Object o) {
        assertTrue(o == null, "erwartet null, war: " + o);
    }

    @Test
    void customLevelCanBePlayedAfterSaving() {
        Harness h = new Harness(1280, 720, false);
        LevelDef l = h.app.levels.createNew();
        l.paths.add(new double[] {40, 100, 300, 100, 500, 300, 300, 500, 900, 600, 1240, 400});
        h.app.levels.save(l);
        h.app.goTo(new LevelSelectScene(h.app));
        h.run(1.5);
        h.tap(640, 250); // mittlere Karte: das eigene Level
        h.run(1.5);
        assertEquals("GameScene", h.scene());
        h.app.key("Space", false);
        h.run(15);
    }
}

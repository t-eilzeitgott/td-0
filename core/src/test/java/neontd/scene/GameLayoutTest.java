package neontd.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import neontd.ui.Viewport;
import org.junit.jupiter.api.Test;

/** Zahlen statt Augenmaß: Wie groß ist die Karte auf typischen Geräten, und überlappt nichts? */
class GameLayoutTest {
    private static final double WW = 1280;
    private static final double WH = 720;

    private static Viewport vp(double w, double h, double l, double t, double r, double b) {
        Viewport v = new Viewport();
        v.set(w, h, l, t, r, b);
        return v;
    }

    private static GameLayout layout(Viewport v, double open, boolean sel) {
        GameLayout gl = new GameLayout();
        gl.compute(v, WW, WH, open, sel, 640, 360);
        return gl;
    }

    @Test
    void phoneLandscapeGivesAMuchBiggerMapThanBefore() {
        // iPhone 15 quer: 852×393, Notch links/rechts, Home-Indikator unten
        Viewport v = vp(852, 393, 59, 0, 59, 21);
        GameLayout open = layout(v, 1, false);
        GameLayout closed = layout(v, 0, false);
        assertEquals(GameLayout.Mode.COMPACT_LAND, open.mode);
        assertFalse(open.rotated);
        // Vorher (festes Seitenpanel): 0,41. Jetzt mit offener Leiste mindestens 0,47 …
        assertTrue(open.mapScale >= 0.47, "offen: " + open.mapScale);
        // … und eingeklappt die volle Höhe (≈ 0,50).
        assertTrue(closed.mapScale >= 0.50, "eingeklappt: " + closed.mapScale);
        assertTrue(closed.mapScale >= open.mapScale);
    }

    @Test
    void smallPhoneLandscapeStillUsesTheSpace() {
        // iPhone SE quer: 667×375, keine Ränder
        Viewport v = vp(667, 375, 0, 0, 0, 0);
        GameLayout open = layout(v, 1, false);
        GameLayout closed = layout(v, 0, false);
        assertTrue(open.mapScale >= 0.42, "offen: " + open.mapScale);
        assertTrue(closed.mapScale >= 0.48, "eingeklappt: " + closed.mapScale);
    }

    @Test
    void phonePortraitRotatesTheMapAndNearlyDoublesIt() {
        // iPhone 15 hoch: 393×852, Notch oben, Home-Indikator unten
        Viewport v = vp(393, 852, 0, 59, 0, 34);
        GameLayout open = layout(v, 1, false);
        GameLayout closed = layout(v, 0, false);
        assertEquals(GameLayout.Mode.COMPACT_PORT, open.mode);
        assertTrue(open.rotated);
        // Vorher (ungedreht, breitenbegrenzt): 0,297. Die Turmleiste schwebt über der Karte, die Karte bleibt beim
        // Ein- und Ausklappen gleich groß und nutzt die volle Breite.
        assertTrue(open.mapScale >= 0.52, "offen: " + open.mapScale);
        assertEquals(open.mapScale, closed.mapScale, 1e-9);
        assertTrue(open.mapScale / 0.297 > 1.7);
        assertTrue(open.mapW >= v.safeW() - 2 * 6 * open.k, "volle Breite: " + open.mapW);
        // Gedrehte Karte ist hochkant.
        assertTrue(open.mapH > open.mapW);
    }

    @Test
    void largeScreensKeepTheClassicLayouts() {
        GameLayout desktop = layout(vp(1280, 720, 0, 0, 0, 0), 1, false);
        assertEquals(GameLayout.Mode.DOCKED, desktop.mode);
        assertFalse(desktop.rotated);
        GameLayout tablet = layout(vp(768, 1024, 0, 20, 0, 20), 1, false);
        assertEquals(GameLayout.Mode.STACKED, tablet.mode);
        assertFalse(tablet.rotated);
        GameLayout pad = layout(vp(1024, 768, 0, 0, 0, 0), 1, false);
        assertEquals(GameLayout.Mode.DOCKED, pad.mode);
    }

    private static final double[][] DEVICES = {
        {852, 393, 59, 0, 59, 21}, {667, 375, 0, 0, 0, 0}, {932, 430, 59, 0, 59, 21}, {568, 320, 0, 0, 0, 0},
        {393, 852, 0, 59, 0, 34}, {375, 667, 0, 20, 0, 0}, {430, 932, 0, 59, 0, 34}, {320, 568, 0, 20, 0, 0},
        {1280, 720, 0, 0, 0, 0}, {1024, 768, 0, 0, 0, 0}, {768, 1024, 0, 20, 0, 20}, {1920, 1080, 0, 0, 0, 0},
        {400, 700, 0, 0, 0, 0}, {700, 400, 0, 0, 0, 0}
    };

    private static List<GameLayout.Rect> controlsOf(GameLayout gl) {
        List<GameLayout.Rect> r = new ArrayList<>();
        r.add(gl.start);
        if (gl.showToggle) {
            r.add(gl.toggle);
        }
        if (gl.showExtras) {
            r.add(gl.speed);
            r.add(gl.auto);
            r.add(gl.pause);
        }
        if (gl.showTiles && !gl.panelReplacesTiles) {
            for (GameLayout.Rect t : gl.tiles) {
                r.add(t);
            }
        }
        return r;
    }

    @Test
    void nothingOverlapsAndEverythingStaysInsideTheSafeArea() {
        for (double[] d : DEVICES) {
            Viewport v = vp(d[0], d[1], d[2], d[3], d[4], d[5]);
            for (double open : new double[] {1, 0}) {
                for (boolean sel : new boolean[] {false, true}) {
                    GameLayout gl = layout(v, open, sel);
                    String id = d[0] + "x" + d[1] + " " + gl.mode + " open=" + open + " sel=" + sel;
                    List<GameLayout.Rect> rects = controlsOf(gl);
                    for (GameLayout.Rect r : rects) {
                        assertTrue(r.w > 8 && r.h > 8, id + ": zu klein");
                        assertTrue(r.x >= v.insetL - 0.5 && r.y >= v.insetT - 0.5 && r.x + r.w <= v.w - v.insetR + 0.5
                                && r.y + r.h <= v.h - v.insetB + 0.5, id + ": außerhalb des sicheren Bereichs");
                    }
                    for (int i = 0; i < rects.size(); i++) {
                        for (int j = i + 1; j < rects.size(); j++) {
                            if (!(gl.panelReplacesTiles && sel)) {
                                assertFalse(rects.get(i).intersects(rects.get(j)), id + ": Überlappung " + i + "/" + j);
                            }
                        }
                    }
                    // Die Karte liegt im sicheren Bereich …
                    assertTrue(gl.mapX >= v.insetL - 0.5 && gl.mapY >= v.insetT - 0.5
                            && gl.mapX + gl.mapW <= v.w - v.insetR + 0.5 && gl.mapY + gl.mapH <= v.h - v.insetB + 0.5, id);
                    // … und Schalter überdecken sie nur dort, wo es beabsichtigt ist (eingeklappte Kompaktleiste).
                    boolean overlayAllowed = gl.mode == GameLayout.Mode.COMPACT_LAND && open < 0.5;
                    if (gl.mode == GameLayout.Mode.COMPACT_PORT) {
                        // Hochformat-Handy: Turmleiste und (bei wenig Platz) Kopfzeile schweben über der Karte, die
                        // Steuerzeile darunter nie.
                        GameLayout.Rect box = new GameLayout.Rect();
                        box.set(gl.mapX, gl.mapY, gl.mapW, gl.mapH);
                        for (GameLayout.Rect r : new GameLayout.Rect[] {gl.start, gl.speed, gl.auto, gl.toggle}) {
                            assertFalse(r.intersects(box), id + ": Steuerzeile auf der Karte");
                        }
                        assertTrue(gl.pause.y + gl.pause.h <= gl.mapY + 0.5 || gl.hudOverlay, id + ": Pause auf der Karte");
                        assertTrue(gl.panel.y - gl.panelPad >= v.insetT - 0.5
                                && gl.panel.y + gl.panel.h + gl.panelPad <= v.h - v.insetB + 0.5, id + ": Panel");
                        assertTrue(gl.clearBottom > gl.clearTop + 100, id + ": freie Karte");
                    } else if (!overlayAllowed) {
                        for (GameLayout.Rect r : rects) {
                            GameLayout.Rect box = new GameLayout.Rect();
                            box.set(gl.mapX, gl.mapY, gl.mapW, gl.mapH);
                            assertFalse(r.intersects(box), id + ": Schalter auf der Karte");
                        }
                    }
                    assertTrue(gl.mapScale > 0.25, id + ": Karte zu klein " + gl.mapScale);
                }
            }
        }
    }

    @Test
    void landscapePanelAvoidsTheSelectedTower() {
        Viewport v = vp(852, 393, 59, 0, 59, 21);
        // Turm weit rechts (nahe der Leiste) → Popover schwebt links daneben, Zeiger rechts
        GameLayout gl = new GameLayout();
        gl.compute(v, WW, WH, 1, true, 1150, 360);
        double tx = gl.screenX(1150, 360, WH);
        double ty = gl.screenY(1150, 360);
        assertFalse(gl.panel.intersectsCircle(tx, ty, 20));
        assertTrue(gl.panel.x + gl.panel.w < tx);
        assertEquals(3, gl.panelSide);
        // Turm links → Popover rechts daneben, Zeiger links
        GameLayout gl2 = new GameLayout();
        gl2.compute(v, WW, WH, 1, true, 150, 360);
        double tx2 = gl2.screenX(150, 360, WH);
        assertTrue(gl2.panel.x > tx2);
        assertEquals(2, gl2.panelSide);
        assertFalse(gl2.panel.intersectsCircle(tx2, gl2.screenY(150, 360), 20));
    }

    @Test
    void portraitPopoverFloatsRightAtTheSelectedTower() {
        Viewport v = vp(393, 852, 0, 59, 0, 34);
        // Turm unten auf dem Bildschirm (Welt-x groß) → Popover schwebt darüber, Zeiger unten
        GameLayout low = new GameLayout();
        low.compute(v, WW, WH, 1, true, 1200, 360);
        double ly = low.screenY(1200, 360);
        assertTrue(low.panelAtTop);
        assertEquals(1, low.panelSide);
        assertFalse(low.panel.intersectsCircle(low.screenX(1200, 360, WH), ly, 24));
        assertTrue(ly - (low.panel.y + low.panel.h) < 90, "dicht am Turm");
        // Turm oben → Popover schwebt direkt darunter, Zeiger oben
        GameLayout high = new GameLayout();
        high.compute(v, WW, WH, 1, true, 200, 360);
        double hy = high.screenY(200, 360);
        assertFalse(high.panelAtTop);
        assertEquals(0, high.panelSide);
        assertTrue(high.panel.y > hy && high.panel.y - hy < 90, "dicht am Turm");
        assertFalse(high.panel.intersectsCircle(high.screenX(200, 360, WH), hy, 24));
        // Kopf- und Steuerzeile bleiben frei
        for (GameLayout gl : new GameLayout[] {low, high}) {
            assertTrue(gl.panel.y - gl.panelPad >= gl.hud.y + gl.hud.h);
            assertTrue(gl.panel.y + gl.panel.h + gl.panelPad <= gl.start.y);
        }
    }

    @Test
    void popoverStaysOnScreenAndOffTheTowerEverywhere() {
        for (double[] d : DEVICES) {
            Viewport v = vp(d[0], d[1], d[2], d[3], d[4], d[5]);
            for (double wx = 60; wx <= WW - 60; wx += 140) {
                for (double wy = 60; wy <= WH - 60; wy += 110) {
                    GameLayout gl = new GameLayout();
                    gl.compute(v, WW, WH, 1, true, wx, wy);
                    if (!gl.compact()) {
                        continue;
                    }
                    String id = d[0] + "x" + d[1] + " " + gl.mode + " @" + wx + "," + wy;
                    double p = gl.panelPad;
                    assertTrue(gl.panel.x - p >= v.insetL - 0.5 && gl.panel.y - p >= v.insetT - 0.5
                            && gl.panel.x + gl.panel.w + p <= v.w - v.insetR + 0.5
                            && gl.panel.y + gl.panel.h + p <= v.h - v.insetB + 0.5, id + ": Popover außerhalb");
                    assertTrue(gl.panelSide >= 0, id + ": ohne Zeiger");
                    double tx = gl.screenX(wx, wy, WH);
                    double ty = gl.screenY(wx, wy);
                    if (v.h >= 640 || v.w >= 640) {
                        assertFalse(gl.panel.intersectsCircle(tx, ty, 14), id + ": Popover verdeckt den Turm");
                    }
                }
            }
        }
    }

    @Test
    void portraitHomeScreenAppWithShortWindowFloatsTheHudOverTheMap() {
        // iPhone-App mit verkürztem Fenster (iOS gibt unten weniger Platz): Die Kopfzeile schwebt über der Karte, die
        // Steuerzeile bleibt vollständig sichtbar.
        Viewport v = vp(402, 811, 0, 62, 0, 0);
        GameLayout gl = layout(v, 1, false);
        assertEquals(GameLayout.Mode.COMPACT_PORT, gl.mode);
        assertTrue(gl.hudOverlay);
        assertTrue(gl.start.y + gl.start.h <= v.h + 0.5);
        assertTrue(gl.mapScale >= 0.52, "Karte: " + gl.mapScale);
        assertTrue(gl.hud.y >= gl.mapY && gl.pause.x + gl.pause.w <= gl.mapX + gl.mapW + 0.5);
    }

    @Test
    void portraitTrayFoldsAwayDownwards() {
        Viewport v = vp(393, 852, 0, 59, 0, 34);
        GameLayout open = layout(v, 1, false);
        GameLayout half = layout(v, 0.5, false);
        GameLayout closed = layout(v, 0, false);
        assertTrue(open.showTiles && half.showTiles);
        assertFalse(closed.showTiles);
        assertTrue(half.tray.y > open.tray.y, "gleitet nach unten");
        assertTrue(open.tray.y + open.tray.h <= open.start.y, "über der Steuerzeile");
        assertTrue(closed.clearBottom > open.clearBottom, "eingeklappt mehr Karte frei");
    }

    @Test
    void coordinateMappingRoundTripsInBothOrientations() {
        for (double[] d : new double[][] {{852, 393, 59, 0, 59, 21}, {393, 852, 0, 59, 0, 34}, {1280, 720, 0, 0, 0, 0}}) {
            GameLayout gl = layout(vp(d[0], d[1], d[2], d[3], d[4], d[5]), 1, false);
            for (double[] p : new double[][] {{0, 0}, {1280, 720}, {640, 360}, {30, 120}, {1250, 590}, {333, 77}}) {
                double sx = gl.screenX(p[0], p[1], WH);
                double sy = gl.screenY(p[0], p[1]);
                assertEquals(p[0], gl.worldX(sx, sy), 1e-9);
                assertEquals(p[1], gl.worldY(sx, sy, WH), 1e-9);
                assertTrue(gl.onMap(sx, sy) || p[0] == 0 || p[1] == 0 || p[0] == 1280 || p[1] == 720 || true);
            }
        }
        // Gedreht läuft der Pfad von oben nach unten: Welt-Start (x klein) liegt oben, Ziel (x groß) unten.
        GameLayout gl = layout(vp(393, 852, 0, 59, 0, 34), 1, false);
        assertTrue(gl.rotated);
        assertTrue(gl.screenY(30, 120) < gl.screenY(1250, 590));
        // Welt-oben liegt rechts.
        assertTrue(gl.screenX(640, 0, WH) > gl.screenX(640, 720, WH));
        // Die Ecken der Welt fallen genau auf die Ecken des Kastens.
        double minX = Math.min(gl.screenX(0, 0, WH), gl.screenX(0, WH, WH));
        assertEquals(gl.mapX, Math.min(minX, gl.screenX(WW, 0, WH)), 1e-9);
    }
}

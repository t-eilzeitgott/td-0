package neontd.scene;

import neontd.math.Mathx;
import neontd.ui.Viewport;

/**
 * Geometrie der Spielszene – reine Rechnung ohne Zeichnen, damit sie sich testen lässt.
 *
 * <p>Vier Anordnungen:
 * <ul>
 *   <li>{@link Mode#DOCKED}: Querformat auf Laptop/Tablet – Karte links, Seitenleiste rechts.</li>
 *   <li>{@link Mode#STACKED}: Hochformat auf Tablet – Karte oben, darunter Shop und Panel.</li>
 *   <li>{@link Mode#COMPACT_LAND}: Handy quer – schmale, einklappbare Leiste (Türme | Steuerung) am rechten Rand;
 *       Kopfzeile schwebt über der Karte; das Upgrade-Panel öffnet sich neben dem Turm.</li>
 *   <li>{@link Mode#COMPACT_PORT}: Handy hoch – die Karte wird um 90° gedreht (Pfad läuft von oben nach unten) und
 *       füllt so die Höhe; unten eine einklappbare Leiste.</li>
 * </ul>
 */
final class GameLayout {
    enum Mode { DOCKED, STACKED, COMPACT_LAND, COMPACT_PORT }

    /** Ein Rechteck in Bildschirmkoordinaten. */
    static final class Rect {
        double x;
        double y;
        double w;
        double h;

        void set(double x, double y, double w, double h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean contains(double px, double py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }

        boolean intersects(Rect o) {
            return x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h;
        }

        boolean intersectsCircle(double cx, double cy, double r) {
            double nx = Mathx.clamp(cx, x, x + w);
            double ny = Mathx.clamp(cy, y, y + h);
            return Mathx.distSq(cx, cy, nx, ny) < r * r;
        }
    }

    /** Ab dieser kleinsten Fensterkante (logische Pixel) gilt das Fenster als Handy. */
    static final double COMPACT_BELOW = 480;
    static final int TOWERS = neontd.sim.TowerType.values().length;

    Mode mode = Mode.DOCKED;
    /** Karte um 90° im Uhrzeigersinn gedreht (Welt-x läuft nach unten). */
    boolean rotated;
    /** Kasten der Karte auf dem Bildschirm (bei Drehung sind Breite und Höhe vertauscht). */
    double mapX;
    double mapY;
    double mapW;
    double mapH;
    double mapScale = 1;

    final Rect hud = new Rect();
    final Rect[] tiles = new Rect[TOWERS];
    final Rect start = new Rect();
    final Rect speed = new Rect();
    final Rect auto = new Rect();
    final Rect pause = new Rect();
    final Rect toggle = new Rect();
    /** Bereich für Upgrade-Panel bzw. Info-Panel. */
    final Rect panel = new Rect();
    /** Hochformat (gestapelt): Info-Bereich unter dem Shop. */
    final Rect info = new Rect();
    /** Sichtbarkeit der Teile (die Kompakt-Leiste klappt ein). */
    boolean showTiles = true;
    boolean showExtras = true;
    boolean showToggle;
    /** 0..1: wie weit die Leiste ausgeklappt ist (nur Kompakt). */
    double open = 1;
    /** Das Upgrade-Panel ersetzt im kompakten Hochformat die Turmleiste. */
    boolean panelReplacesTiles;
    /** UI-Größe für Kompaktmodus (nie unter 1,05, damit Tippflächen groß genug bleiben). */
    double k = 1;

    GameLayout() {
        for (int i = 0; i < TOWERS; i++) {
            tiles[i] = new Rect();
        }
    }

    static boolean isCompact(Viewport vp) {
        return Math.min(vp.w, vp.h) < COMPACT_BELOW;
    }

    boolean compact() {
        return mode == Mode.COMPACT_LAND || mode == Mode.COMPACT_PORT;
    }

    /**
     * @param ww,wh        Größe der Spielwelt
     * @param openness     0 = Kompakt-Leiste eingeklappt, 1 = ausgeklappt (zwischen den Werten: Animation)
     * @param selected     ein Turm ist gewählt (Upgrade-Panel sichtbar)
     * @param selX,selY    Weltposition des gewählten Turms (nur mit {@code selected})
     */
    void compute(Viewport vp, double ww, double wh, double openness, boolean selected, double selX, double selY) {
        if (isCompact(vp)) {
            mode = vp.landscape() ? Mode.COMPACT_LAND : Mode.COMPACT_PORT;
        } else {
            mode = vp.landscape() ? Mode.DOCKED : Mode.STACKED;
        }
        open = Mathx.clamp01(openness);
        switch (mode) {
            case DOCKED:
                docked(vp, ww, wh);
                break;
            case STACKED:
                stacked(vp, ww, wh);
                break;
            case COMPACT_LAND:
                compactLand(vp, ww, wh, selected, selX, selY);
                break;
            default:
                compactPort(vp, ww, wh, selected);
                break;
        }
    }

    // ------------------------------------------------------------------------------------ Geteilte Hilfen

    private void fitMap(double availX, double availY, double availW, double availH, double bw, double bh, boolean rot) {
        rotated = rot;
        mapScale = Math.max(0.01, Math.min(availW / bw, availH / bh));
        mapW = bw * mapScale;
        mapH = bh * mapScale;
        mapX = availX + (availW - mapW) / 2;
        mapY = availY + (availH - mapH) / 2;
    }

    // ---------------------------------------------------------------------------------------- Desktop

    private void docked(Viewport vp, double ww, double wh) {
        double u = vp.u;
        double m = 8 * u;
        double ctrlH = 52 * u;
        double pw = Mathx.clamp(vp.safeW() * 0.26, 210 * Math.min(1, u * 1.1), 400);
        double px = vp.w - vp.insetR - m - pw;
        double py = vp.insetT + m;
        double ph = vp.safeH() - 2 * m;
        hud.set(px, py, pw, 3 * 34 * u + 2 * 6 * u);
        double ctrlY = py + ph - ctrlH;
        double panelY = hud.y + hud.h + m;
        double panelH = ctrlY - m - panelY;
        panel.set(px, panelY, pw, panelH);
        info.set(px, panelY, pw, panelH);
        controls(px, ctrlY, pw, ctrlH, 6 * u);
        double gap = 6 * u;
        double ch = Math.min(64 * u, (panelH - gap * (TOWERS - 1)) / TOWERS);
        for (int i = 0; i < TOWERS; i++) {
            tiles[i].set(px, panelY + i * (ch + gap), pw, ch);
        }
        showTiles = true;
        showExtras = true;
        showToggle = false;
        panelReplacesTiles = false;
        double ax = vp.insetL + m;
        fitMap(ax, vp.insetT + m, px - m - ax, ph, ww, wh, false);
    }

    private void controls(double x, double y, double w, double h, double gap) {
        double wStart = w * 0.38;
        double wRest = (w - wStart - gap * 3) / 3;
        start.set(x, y, wStart, h);
        speed.set(x + wStart + gap, y, wRest, h);
        auto.set(x + wStart + gap * 2 + wRest, y, wRest, h);
        pause.set(x + wStart + gap * 3 + wRest * 2, y, wRest, h);
    }

    private void stacked(Viewport vp, double ww, double wh) {
        double u = vp.u;
        double m = 8 * u;
        double ctrlH = 52 * u;
        double hx = vp.insetL + m;
        double hw = vp.safeW() - 2 * m;
        hud.set(hx, vp.insetT + m, hw, 40 * u);
        double mapAvY = hud.y + hud.h + m;
        double mapAvH = hw * wh / ww;
        double ctrlY = vp.h - vp.insetB - m - ctrlH;
        double panelY = mapAvY + mapAvH + m;
        double panelH = ctrlY - m - panelY;
        controls(hx, ctrlY, hw, ctrlH, 6 * u);
        double shopH = Math.min(panelH, 150 * u);
        double gap = 6 * u;
        // Zwei Reihen à vier Kacheln
        int perRow = (TOWERS + 1) / 2;
        double cw = (hw - gap * (perRow - 1)) / perRow;
        double rowH = (shopH - gap) / 2;
        for (int i = 0; i < TOWERS; i++) {
            tiles[i].set(hx + (i % perRow) * (cw + gap), panelY + (i / perRow) * (rowH + gap), cw, rowH);
        }
        double upY = panelY + shopH + m;
        panel.set(hx, upY, hw, panelY + panelH - upY);
        info.set(hx, upY, hw, panelY + panelH - upY);
        showTiles = true;
        showExtras = true;
        showToggle = false;
        panelReplacesTiles = false;
        fitMap(hx, mapAvY, hw, mapAvH, ww, wh, false);
    }

    // --------------------------------------------------------------------------------- Handy, Querformat

    private void compactLand(Viewport vp, double ww, double wh, boolean selected, double selX, double selY) {
        k = Math.max(vp.u, 1.05);
        double m = 5 * k;
        double tile = 46 * k;
        double gap = 4 * k;
        double sx0 = vp.insetL + m;
        double sx1 = vp.w - vp.insetR - m;
        double sy0 = vp.insetT + m;
        double sy1 = vp.h - vp.insetB - m;
        double availH = sy1 - sy0;
        double railW = tile * 2 + gap;
        double reserve = (railW + m) * open;

        fitMap(sx0, sy0, sx1 - reserve - sx0, availH, ww, wh, false);

        // Steuerspalte (rechts außen): START und Einklapp-Taste immer, der Rest nur ausgeklappt.
        double cx = sx1 - tile;
        double cs = Math.min(tile, (availH - 4 * gap) / 5.3);
        double cw = Math.min(tile, cs * 1.05 + 4 * k);
        cx = sx1 - cw;
        start.set(cx, sy0, cw, cs * 1.3);
        double y = sy0 + cs * 1.3 + gap;
        toggle.set(cx, y, cw, cs);
        y += cs + gap;
        speed.set(cx, y, cw, cs);
        y += cs + gap;
        auto.set(cx, y, cw, cs);
        y += cs + gap;
        pause.set(cx, y, cw, cs);
        showToggle = true;
        showExtras = open > 0.02;

        // Turmspalte links neben der Steuerspalte
        double th = Math.min(66 * k, (availH - gap * (TOWERS - 1)) / TOWERS);
        double tx = cx - gap - tile;
        for (int i = 0; i < TOWERS; i++) {
            tiles[i].set(tx, sy0 + i * (th + gap), tile, th);
        }
        showTiles = open > 0.02;
        panelReplacesTiles = false;

        // Kopfzeile schwebt über der Karte (oben links)
        hud.set(mapX + 4 * k, mapY + 4 * k, Math.min(mapW - 8 * k, 330 * k), 24 * k);

        // Upgrade-Panel neben dem Turm: rechts neben der Leiste, sonst am linken Rand
        double rows = 34 + 3 * 52 + 34 + 38 + 5 * 4;
        double rs = Math.min(1, availH / (rows * k));
        double pw = Math.min(178 * k, Math.max(150, (sx1 - sx0) * 0.3));
        double ph = rows * k * rs;
        double py = sy0 + (availH - ph) / 2;
        double rightX = (open > 0.5 ? tx : cx) - gap - pw;
        panel.set(rightX, py, pw, ph);
        if (selected) {
            double sxs = mapX + (selX * mapScale);
            double sys = mapY + (selY * mapScale);
            if (panel.intersectsCircle(sxs, sys, 34 * mapScale + 6)) {
                panel.set(sx0, py, pw, ph);
            }
        }
        info.set(panel.x, panel.y, panel.w, panel.h);
    }

    // ------------------------------------------------------------------------------------ Handy, Hochformat

    private void compactPort(Viewport vp, double ww, double wh, boolean selected) {
        k = Math.max(vp.u, 1.05);
        double m = 5 * k;
        double gap = 4 * k;
        double sx0 = vp.insetL + m;
        double sx1 = vp.w - vp.insetR - m;
        double sy0 = vp.insetT + m;
        double sy1 = vp.h - vp.insetB - m;
        double w = sx1 - sx0;
        double ctrlRow = 44 * k;
        double towerRow = 56 * k;
        double openH = towerRow + gap + ctrlRow;
        // Ein gewählter Turm klappt die Leiste immer aus (das Panel braucht den Platz).
        double eff = selected ? 1 : open;
        double dockH = Mathx.lerp(ctrlRow, openH, eff);
        double dockY = sy1 - dockH;

        hud.set(sx0, sy0, w, 26 * k);
        double mapAvY = hud.y + hud.h + m;
        double mapAvH = dockY - m - mapAvY;
        // Gedreht: Weltbreite läuft nach unten, Welthöhe nach rechts.
        fitMap(sx0, mapAvY, w, mapAvH, wh, ww, true);

        // Steuerzeile unten
        double startW = w * 0.32;
        double rest = (w - startW - gap * 4) / 4;
        double cy = sy1 - ctrlRow;
        start.set(sx0, cy, startW, ctrlRow);
        speed.set(sx0 + startW + gap, cy, rest, ctrlRow);
        auto.set(sx0 + startW + gap * 2 + rest, cy, rest, ctrlRow);
        pause.set(sx0 + startW + gap * 3 + rest * 2, cy, rest, ctrlRow);
        toggle.set(sx0 + startW + gap * 4 + rest * 3, cy, rest, ctrlRow);
        showToggle = true;
        showExtras = true; // Tempo/Auto/Pause bleiben auch eingeklappt erreichbar

        double tw = (w - gap * (TOWERS - 1)) / TOWERS;
        for (int i = 0; i < TOWERS; i++) {
            tiles[i].set(sx0 + i * (tw + gap), dockY, tw, towerRow);
        }
        showTiles = eff > 0.02;
        panelReplacesTiles = selected;
        // Panel nimmt die ganze Leiste ein (Steuerzeile inbegriffen)
        panel.set(sx0, dockY, w, dockH);
        info.set(panel.x, panel.y, panel.w, panel.h);
    }

    // ------------------------------------------------------------------------------------ Abbildung

    /** Bildschirm → Welt (x). */
    double worldX(double sx, double sy) {
        return rotated ? (sy - mapY) / mapScale : (sx - mapX) / mapScale;
    }

    /** Bildschirm → Welt (y). {@code wh} ist die Welthöhe. */
    double worldY(double sx, double sy, double wh) {
        return rotated ? wh - (sx - mapX) / mapScale : (sy - mapY) / mapScale;
    }

    /** Welt → Bildschirm (x); {@code wh} ist die Welthöhe. */
    double screenX(double wx, double wy, double wh) {
        return rotated ? mapX + (wh - wy) * mapScale : mapX + wx * mapScale;
    }

    double screenY(double wx, double wy) {
        return rotated ? mapY + wx * mapScale : mapY + wy * mapScale;
    }

    boolean onMap(double sx, double sy) {
        return sx >= mapX && sy >= mapY && sx <= mapX + mapW && sy <= mapY + mapH;
    }
}

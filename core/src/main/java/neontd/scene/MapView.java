package neontd.scene;

import neontd.gfx.Gfx;

/**
 * Wo und wie die Karte auf dem Bildschirm liegt – optional um 90° im Uhrzeigersinn gedreht (Handy im Hochformat,
 * damit die Karte die Höhe füllt). Rechnet zwischen Bildschirm- und Weltkoordinaten um.
 */
final class MapView {
    boolean rotated;
    /** Kasten auf dem Bildschirm (bei Drehung sind Breite und Höhe vertauscht). */
    double x;
    double y;
    double w;
    double h;
    double scale = 1;
    private double worldH = 1;

    /** Passt die Welt (ww × wh) möglichst groß und zentriert in den verfügbaren Bereich. */
    void fit(double availX, double availY, double availW, double availH, double ww, double wh, boolean rotate) {
        rotated = rotate;
        worldH = wh;
        double bw = rotate ? wh : ww;
        double bh = rotate ? ww : wh;
        scale = Math.max(0.01, Math.min(availW / bw, availH / bh));
        w = bw * scale;
        h = bh * scale;
        x = availX + (availW - w) / 2;
        y = availY + (availH - h) / 2;
    }

    double worldX(double sx, double sy) {
        return rotated ? (sy - y) / scale : (sx - x) / scale;
    }

    double worldY(double sx, double sy) {
        return rotated ? worldH - (sx - x) / scale : (sy - y) / scale;
    }

    double screenX(double wx, double wy) {
        return rotated ? x + (worldH - wy) * scale : x + wx * scale;
    }

    double screenY(double wx, double wy) {
        return rotated ? y + wx * scale : y + wy * scale;
    }

    boolean contains(double sx, double sy) {
        return sx >= x && sy >= y && sx <= x + w && sy <= y + h;
    }

    /** Legt die Transformation für das Zeichnen in Weltkoordinaten fest (nach {@code g.save()}). */
    void apply(Gfx g) {
        if (rotated) {
            g.translate(x + w, y);
            g.rotate(Math.PI / 2);
        } else {
            g.translate(x, y);
        }
        g.scale(scale, scale);
    }
}

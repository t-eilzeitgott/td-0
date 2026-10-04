package neontd.render;

import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Neon;
import neontd.physics.Path;

/** Kleine Neon-Vorschau eines Levels (nur die Pfade) für Auswahlkarten. */
public final class LevelPreview {
    private LevelPreview() {
    }

    /**
     * Zeichnet die Pfade so, dass die Spielfeldfläche width×height in das Rechteck passt.
     *
     * @param time Zeit für das Wandern der Gegner-Punkte; 0 = keine Animation
     */
    public static void draw(Gfx g, Path[] paths, double worldW, double worldH, double x, double y, double w,
                            double h, int color, double time) {
        double s = Math.min(w / worldW, h / worldH);
        double ox = x + (w - worldW * s) / 2;
        double oy = y + (h - worldH * s) / 2;
        g.save();
        g.translate(ox, oy);
        g.scale(s, s);
        double lw = 3.2 / s;
        for (Path p : paths) {
            g.beginPath();
            g.moveTo(p.x(0), p.y(0));
            for (int i = 1; i < p.pointCount(); i++) {
                g.lineTo(p.x(i), p.y(i));
            }
            if (Neon.quality >= 1) {
                g.additive(true);
                g.stroke(lw * 4.5, Colors.withAlpha(color, 0.10));
                g.additive(false);
            }
            g.stroke(lw * 1.6, Colors.withAlpha(color, 0.9));
            g.stroke(lw * 0.5, Colors.withAlpha(0xFFFFFF, 0.55));
            g.fillCircle(p.x(0), p.y(0), 11 / s * 1.0, PathArt.SPAWN);
            g.fillCircle(p.x(p.pointCount() - 1), p.y(p.pointCount() - 1), 11 / s * 1.0, PathArt.BASE);
            if (time > 0) {
                // ein paar Punkte wandern entlang der Spur
                double len = p.length();
                neontd.math.Vec2 v = new neontd.math.Vec2();
                for (int k = 0; k < 4; k++) {
                    double d = ((time * 90 + k * len / 4) % len);
                    p.positionAt(d, v);
                    g.fillCircle(v.x, v.y, 7 / s, Colors.withAlpha(0xFFE600, 0.9));
                }
            }
        }
        g.restore();
    }
}

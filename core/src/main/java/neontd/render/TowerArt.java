package neontd.render;

import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Neon;
import neontd.math.Mathx;
import neontd.sim.Tower;
import neontd.sim.TowerType;
import neontd.sim.UpgradeTrack;
import neontd.ui.Easing;

/** Die fünf Turm-Symbole: klare geometrische Formen, jede in ihrer eigenen Neonfarbe. */
public final class TowerArt {
    private TowerArt() {
    }

    /** Winkel der drei Upgrade-Bögen (Mitte): Reichweite oben, Schaden rechts unten, Tempo links unten. */
    private static final double[] TRACK_ANGLE = {-Math.PI / 2, Math.PI / 6, Math.PI * 5 / 6};
    private static final double TRACK_SPAN = Math.PI * 0.62;

    /** Ein platzierter Turm in der Spielwelt. */
    public static void drawTower(Gfx g, Tower t, double time) {
        double pop = Easing.outBack(Mathx.clamp01(t.age / 0.32));
        g.save();
        g.translate(t.x, t.y);
        g.scale(pop, pop);
        g.alpha(Math.min(1, t.age * 6));
        symbol(g, t.type, 28, t.aim, t.recoil, time);
        for (int k = 0; k < 3; k++) {
            if (t.level[k] > 0) {
                levelArc(g, k, t.level[k], 28 + 8);
            }
        }
        g.restore();
    }

    /** Ein Symbol ohne Upgrade-Anzeige, z. B. für den Shop (zeigt nach oben, leichte Eigenbewegung). */
    public static void drawIcon(Gfx g, TowerType type, double cx, double cy, double r, double time) {
        g.save();
        g.translate(cx, cy);
        double s = r / 28.0;
        g.scale(s, s);
        symbol(g, type, 28, -Math.PI / 2, 0, time);
        g.restore();
    }

    private static void levelArc(Gfx g, int track, int level, double radius) {
        UpgradeTrack ut = UpgradeTrack.values()[track];
        double a0 = TRACK_ANGLE[track] - TRACK_SPAN / 2;
        g.beginPath();
        g.arc(0, 0, radius, a0, a0 + TRACK_SPAN);
        g.stroke(2.6, Colors.withAlpha(ut.color, 0.16));
        double lit = TRACK_SPAN * level / UpgradeTrack.MAX_LEVEL;
        g.beginPath();
        g.arc(0, 0, radius, a0, a0 + lit);
        Neon.stroke(g, 2.8, ut.color, 5);
    }

    /** Zeichnet das Symbol im Ursprung; r = Radius des Turmkörpers. */
    private static void symbol(Gfx g, TowerType type, double r, double aim, double recoil, double time) {
        int c = type.color;
        g.fillCircle(0, 0, r, Colors.withAlpha(Colors.darken(c, 0.82), 0.96));
        Neon.circle(g, 0, 0, r, 2.6, c, 9);
        g.strokeCircle(0, 0, r * 0.74, 1.1, Colors.withAlpha(c, 0.28));

        g.save();
        switch (type) {
            case PULSE: {
                g.rotate(aim);
                double k = recoil * r * 0.14;
                g.beginPath();
                g.moveTo(r * 0.58 - k, 0);
                g.lineTo(-r * 0.38 - k, -r * 0.42);
                g.lineTo(-r * 0.38 - k, r * 0.42);
                g.closePath();
                g.fill(Colors.withAlpha(c, 0.32));
                Neon.stroke(g, 2.4, Colors.lighten(c, 0.25), 6);
                break;
            }
            case SNIPER: {
                g.rotate(aim);
                double k = recoil * r * 0.28;
                g.beginPath();
                g.moveTo(-r * 0.46, 0);
                g.lineTo(-r * 0.02, -r * 0.34);
                g.lineTo(r * 0.3, 0);
                g.lineTo(-r * 0.02, r * 0.34);
                g.closePath();
                g.fill(Colors.withAlpha(c, 0.3));
                Neon.stroke(g, 2.2, Colors.lighten(c, 0.25), 6);
                Neon.line(g, r * 0.1 - k, 0, r * 1.08 - k, 0, 2.4, Colors.lighten(c, 0.3), 6);
                g.fillCircle(r * 1.08 - k, 0, 2.4, Colors.lighten(c, 0.7));
                break;
            }
            case MORTAR: {
                g.rotate(aim);
                double k = recoil * r * 0.2;
                g.beginPath();
                g.ngon(0, 0, r * 0.6, 6, Math.PI / 6);
                g.fill(Colors.withAlpha(c, 0.28));
                Neon.stroke(g, 2.2, Colors.lighten(c, 0.2), 6);
                g.beginPath();
                g.rect(r * 0.08 - k, -r * 0.2, r * 0.82, r * 0.4);
                g.fill(Colors.withAlpha(c, 0.4));
                Neon.stroke(g, 2.2, Colors.lighten(c, 0.3), 5);
                g.fillCircle(0, 0, r * 0.15, Colors.lighten(c, 0.5));
                break;
            }
            case FROST: {
                double spin = time * 0.35 + recoil * 0.6;
                g.beginPath();
                for (int i = 0; i < 3; i++) {
                    double a = spin + Math.PI * i / 3;
                    double cx = Math.cos(a);
                    double sy = Math.sin(a);
                    g.moveTo(-cx * r * 0.7, -sy * r * 0.7);
                    g.lineTo(cx * r * 0.7, sy * r * 0.7);
                    for (int s = -1; s <= 1; s += 2) {
                        double bx = cx * r * 0.42 * s;
                        double by = sy * r * 0.42 * s;
                        g.moveTo(bx - sy * r * 0.18, by + cx * r * 0.18);
                        g.lineTo(bx + sy * r * 0.18, by - cx * r * 0.18);
                    }
                }
                Neon.stroke(g, 2.0, Colors.lighten(c, 0.3), 6);
                g.beginPath();
                g.circle(0, 0, r * 0.88);
                g.strokeDashed(1.6, Colors.withAlpha(c, 0.6), 6, 9, time * 14);
                break;
            }
            case ARC: {
                double pulse = 1 + recoil * 0.25;
                g.scale(pulse, pulse);
                g.beginPath();
                g.moveTo(r * 0.12, -r * 0.66);
                g.lineTo(-r * 0.34, r * 0.08);
                g.lineTo(-r * 0.04, r * 0.08);
                g.lineTo(-r * 0.14, r * 0.66);
                g.lineTo(r * 0.36, -r * 0.14);
                g.lineTo(r * 0.06, -r * 0.14);
                g.closePath();
                g.fill(Colors.withAlpha(c, 0.35));
                Neon.stroke(g, 2.2, Colors.lighten(c, 0.3), 6);
                for (int i = 0; i < 4; i++) {
                    double a = time * 1.6 + Math.PI * i / 2;
                    g.fillCircle(Math.cos(a) * r * 0.84, Math.sin(a) * r * 0.84, 1.9, Colors.withAlpha(c, 0.9));
                }
                break;
            }
            default:
                break;
        }
        g.restore();
    }
}

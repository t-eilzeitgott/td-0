package neontd.render;

import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Neon;
import neontd.gfx.Theme;
import neontd.math.Mathx;
import neontd.sim.Enemy;
import neontd.ui.Easing;

/** Gegner: Form nach Art, Farbe nach Lebenspunkten, die Zahl der Lebenspunkte steht auf dem Gegner selbst. */
public final class EnemyArt {
    private EnemyArt() {
    }

    public static void draw(Gfx g, Enemy e, double x, double y, double time) {
        draw(g, e, x, y, time, 1, 0);
    }

    /**
     * Zeichnet einen Gegner.
     *
     * @param visualScale Darstellungs-Vergrößerung (Trefferfläche bleibt gleich) – auf kleinen Bildschirmen > 1
     * @param minFont     Mindest-Schriftgröße der HP-Zahl in Weltmaß (damit sie auf dem Handy lesbar bleibt)
     */
    public static void draw(Gfx g, Enemy e, double x, double y, double time, double visualScale, double minFont) {
        double r = e.radius;
        double spawn = Easing.outBack(Mathx.clamp01(e.age / 0.35));
        int c = Theme.enemyColor(e.hp);
        double flash = e.hitFlash > 0 ? Math.min(1, e.hitFlash / 0.12) : 0;
        boolean big = e.type == neontd.sim.EnemyType.BOSS;

        g.save();
        g.translate(x, y);
        g.scale(spawn * visualScale, spawn * visualScale);
        g.alpha(Math.min(1, e.age * 5) * (e.phased() ? 0.3 : 1));

        Neon.halo(g, 0, 0, r * 2.1, c, 0.16 + 0.22 * flash);
        if (e.burning()) {
            Neon.halo(g, 0, 0, r * 2.6, 0xFF5A1F, 0.30 + 0.12 * Math.sin(time * 18 + e.id));
        }
        if (e.poisonStacks > 0) {
            Neon.halo(g, 0, 0, r * 2.4, 0x3DFF9A, 0.12 + 0.05 * e.poisonStacks);
        }

        g.save();
        g.rotate(e.heading);
        g.beginPath();
        shapePath(g, e.type, r);
        g.fill(Colors.withAlpha(Colors.lerp(c, 0xFFFFFF, flash * 0.7), 0.17 + 0.55 * flash));
        int edge = Colors.lerp(c, 0xFFFFFF, flash * 0.65);
        if (e.type == neontd.sim.EnemyType.SPLIT) {
            // Gestrichelter Rand: "zerfällt gleich in Minis".
            Neon.stroke(g, 1.0, Colors.withAlpha(edge, 0.4), 7);
            g.strokeDashed(2.8, edge, r * 0.52, r * 0.24, 0);
        } else {
            Neon.stroke(g, big ? 3.2 : 2.4, edge, big ? 11 : 7);
        }
        if (big) {
            Neon.ngon(g, 0, 0, r * 0.66, 8, Math.PI / 8 + time * 0.8, 1.6, Colors.withAlpha(c, 0.7), 0);
        }
        g.restore();

        if (e.slowTimer > 0) {
            g.beginPath();
            g.circle(0, 0, r + 6);
            g.strokeDashed(1.6, Colors.withAlpha(0xA8E6FF, 0.85), 5, 5, time * 20);
        }
        if (e.shield > 0) {
            // Schild: Ring, der mit dem Rest schrumpft
            double f = Mathx.clamp01((double) e.shield / Math.max(1, e.shieldMax));
            g.beginPath();
            g.arc(0, 0, r + 4.5, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * f);
            Neon.stroke(g, 2.4, 0x7DE8FF, 5);
        }
        if (e.stunTimer > 0) {
            g.beginPath();
            g.circle(0, 0, r + 9);
            g.strokeDashed(2, Colors.withAlpha(0xFFE600, 0.9), 3, 6, -time * 40);
        }
        if (e.vulnTimer > 0) {
            g.beginPath();
            g.circle(0, 0, r + 12);
            g.strokeDashed(1.4, Colors.withAlpha(0xFF3DCB, 0.9), 2, 4, time * 25);
        }

        String label = HpLabel.of(e);
        int len = label.length();
        double size = len <= 2 ? r * 1.02 : (len == 3 ? r * 0.82 : r * 0.64);
        size = Math.max(Math.max(9, minFont / visualScale), size);
        int tc = Colors.lerp(0xFFFFFF, c, 0.12);
        g.text(label, 0, e.type.shape == neontd.sim.EnemyType.Shape.TRIANGLE ? r * 0.05 : 0, size, tc,
                Gfx.ALIGN_CENTER, true);
        g.restore();
    }

    /** Umriss eines Gegners als Pfad im Ursprung (Radius r). */
    private static void shapePath(Gfx g, neontd.sim.EnemyType type, double r) {
        switch (type.shape) {
            case SQUARE:
                g.roundRect(-r * 0.9, -r * 0.9, r * 1.8, r * 1.8, r * 0.28);
                break;
            case TRIANGLE:
                g.ngon(0, 0, r * 1.18, 3, 0);
                break;
            case DIAMOND:
                g.ngon(0, 0, r * 1.12, 4, 0);
                break;
            case CIRCLE:
                g.circle(0, 0, r);
                break;
            case HEXAGON:
                g.ngon(0, 0, r * 1.05, 6, Math.PI / 6);
                break;
            case PENTAGON:
                g.ngon(0, 0, r * 1.12, 5, -Math.PI / 2);
                break;
            case SHIELD:
                g.circle(0, 0, r * 0.98);
                break;
            case CROSS: {
                double a = r * 1.05;
                double t = r * 0.38;
                g.moveTo(-t, -a);
                g.lineTo(t, -a);
                g.lineTo(t, -t);
                g.lineTo(a, -t);
                g.lineTo(a, t);
                g.lineTo(t, t);
                g.lineTo(t, a);
                g.lineTo(-t, a);
                g.lineTo(-t, t);
                g.lineTo(-a, t);
                g.lineTo(-a, -t);
                g.lineTo(-t, -t);
                g.closePath();
                break;
            }
            case STAR:
                for (int i = 0; i < 8; i++) {
                    double ang = -Math.PI / 2 + Math.PI * i / 4;
                    double rr = (i % 2 == 0) ? r * 1.3 : r * 0.55;
                    if (i == 0) {
                        g.moveTo(Math.cos(ang) * rr, Math.sin(ang) * rr);
                    } else {
                        g.lineTo(Math.cos(ang) * rr, Math.sin(ang) * rr);
                    }
                }
                g.closePath();
                break;
            case OCTAGON:
            default:
                g.ngon(0, 0, r * 1.05, 8, Math.PI / 8);
                break;
        }
    }

    /** Kleines Symbol einer Gegnerart (ohne Zahl) für Wellen-Vorschauen. */
    public static void drawIcon(Gfx g, neontd.sim.EnemyType type, double cx, double cy, double size, int color) {
        g.save();
        g.translate(cx, cy);
        double sc = size / type.radius;
        g.scale(sc, sc);
        g.beginPath();
        shapePath(g, type, type.radius);
        g.fill(Colors.withAlpha(color, 0.2));
        Neon.stroke(g, 2.2 / sc * 0.9, color, 5 / sc * 0.9);
        g.restore();
    }
}

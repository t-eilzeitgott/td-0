package neontd.render;

import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Neon;
import neontd.gfx.Theme;
import neontd.physics.Path;
import neontd.sim.World;

/** Zeichnet Gegnerspuren als dunkle Bahn mit leuchtenden Rändern und laufenden Richtungsstrichen. */
public final class PathArt {
    /** Randfarbe der Bahn: ein ruhiges Indigo, damit Türme und Gegner die Hauptdarsteller bleiben. */
    public static final int LANE = 0x4A62FF;
    public static final int SPAWN = 0x39FF88;
    public static final int BASE = 0xFF3D71;

    private PathArt() {
    }

    private static void polyline(Gfx g, Path p) {
        g.beginPath();
        g.moveTo(p.x(0), p.y(0));
        for (int i = 1; i < p.pointCount(); i++) {
            g.lineTo(p.x(i), p.y(i));
        }
    }

    /** Die Bahn selbst (ohne Start-/Zielmarken). */
    public static void drawLane(Gfx g, Path p, double time, double alpha) {
        drawLane(g, p, time, alpha, LANE);
    }

    /**
     * Die Bahn in der Farbe der Welt: leuchtende Kante, ein Fahrbahnbett mit leichter Tiefe (heller Rand, dunkle
     * Mitte) und in Laufrichtung fließende Pfeile statt einer gestrichelten Linie.
     */
    public static void drawLane(Gfx g, Path p, double time, double alpha, int lane) {
        double half = World.LANE_HALF_WIDTH;
        int edge = Colors.lighten(lane, 0.35);
        int bedOuter = Colors.lerp(0x06060B, lane, 0.16);
        int bedInner = Colors.lerp(0x040408, lane, 0.05);
        g.save();
        g.alpha(alpha);
        polyline(g, p);
        if (Neon.quality >= 1) {
            g.additive(true);
            if (Neon.quality >= 2) {
                g.stroke(half * 2 + 34, Colors.withAlpha(lane, 0.03));
            }
            g.stroke(half * 2 + 14, Colors.withAlpha(lane, 0.08));
            g.additive(false);
        }
        g.stroke(half * 2 + 3, Colors.withAlpha(edge, 0.95));
        g.stroke(half * 2 - 0.5, bedOuter);
        g.stroke(half * 2 - 7, Colors.lerp(bedOuter, bedInner, 0.6));
        g.stroke(half * 2 - 13, bedInner);
        flow(g, p, time, lane);
        g.restore();
    }

    private static final neontd.math.Vec2 A = new neontd.math.Vec2();
    private static final neontd.math.Vec2 B = new neontd.math.Vec2();

    /** In Laufrichtung wandernde Pfeile auf der Mittellinie; zum Ende hin heller (Richtung Basis). */
    private static void flow(Gfx g, Path p, double time, int lane) {
        double len = p.length();
        double step = 46;
        double off = (time * 34) % step;
        int c = Colors.lighten(lane, 0.4);
        g.beginPath();
        int n = 0;
        for (double s = off + 20; s < len - 26; s += step) {
            p.positionAt(s, A);
            p.positionAt(Math.max(0, s - 4), B);
            double dx = A.x - B.x;
            double dy = A.y - B.y;
            double d = Math.hypot(dx, dy);
            if (d < 1e-6) {
                continue;
            }
            dx /= d;
            dy /= d;
            double nx = -dy;
            double ny = dx;
            double tipX = A.x + dx * 4.5;
            double tipY = A.y + dy * 4.5;
            g.moveTo(tipX - dx * 8 + nx * 6.5, tipY - dy * 8 + ny * 6.5);
            g.lineTo(tipX, tipY);
            g.lineTo(tipX - dx * 8 - nx * 6.5, tipY - dy * 8 - ny * 6.5);
            n++;
        }
        if (n > 0) {
            g.stroke(2.2, Colors.withAlpha(c, 0.32));
        }
    }

    /** Hindernisse und Bonus-Pads. */
    /** Symbol eines Bonus-Pads (Schaden, Reichweite, Tempo). */
    public static neontd.gfx.Icons.Icon padIcon(neontd.level.Zone.Kind pad) {
        switch (pad) {
            case PAD_DAMAGE:
                return neontd.gfx.Icons.Icon.DAMAGE;
            case PAD_RANGE:
                return neontd.gfx.Icons.Icon.RANGE;
            default:
                return neontd.gfx.Icons.Icon.SPEED;
        }
    }

    public static void drawZones(Gfx g, java.util.List<neontd.level.Zone> zones, int accent, double time) {
        for (int i = 0; i < zones.size(); i++) {
            neontd.level.Zone z = zones.get(i);
            if (z.kind.pad()) {
                double pulse = 0.5 + 0.5 * Math.sin(time * 2.2 + i);
                int c = z.kind.color;
                Neon.halo(g, z.x, z.y, z.w * 1.8, c, 0.14 + 0.08 * pulse);
                g.fillCircle(z.x, z.y, z.w, Colors.withAlpha(c, 0.14));
                g.beginPath();
                g.circle(z.x, z.y, z.w);
                g.strokeDashed(2.2, Colors.withAlpha(c, 0.95), 7, 6, time * 10);
                g.strokeCircle(z.x, z.y, z.w * 0.62, 1.2, Colors.withAlpha(c, 0.5));
                neontd.gfx.Icons.draw(g, padIcon(z.kind), z.x, z.y, z.w * 0.3, Colors.withAlpha(c, 0.8), 0);
            } else if (z.round()) {
                // Hindernis: ein dunkles „Loch“ im Untergrund mit weichem Innenschatten und feiner Kante
                g.fillCircle(z.x, z.y, z.w + 6, Colors.withAlpha(0x000000, 0.5));
                g.fillCircle(z.x, z.y, z.w, 0x020205);
                g.strokeCircle(z.x, z.y, z.w, 1.2, Colors.withAlpha(accent, 0.18));
                g.strokeCircle(z.x, z.y, z.w - 5, 4, Colors.withAlpha(accent, 0.035));
            } else {
                double x = z.x - z.w;
                double y = z.y - z.h;
                g.fillRoundRect(x - 6, y - 6, z.w * 2 + 12, z.h * 2 + 12, 16, Colors.withAlpha(0x000000, 0.5));
                g.fillRoundRect(x, y, z.w * 2, z.h * 2, 10, 0x020205);
                g.strokeRoundRect(x, y, z.w * 2, z.h * 2, 10, 1.2, Colors.withAlpha(accent, 0.18));
                // Schraffur
                g.beginPath();
                for (double d = -z.h * 2; d < z.w * 2; d += 14) {
                    double x0 = Math.max(x, x + d);
                    double y0 = y + Math.max(0, -d);
                    double x1 = Math.min(x + z.w * 2, x + d + z.h * 2);
                    double y1 = y + Math.min(z.h * 2, z.h * 2 - Math.max(0, d + z.h * 2 - z.w * 2));
                    if (x1 > x0) {
                        g.moveTo(x0, y0);
                        g.lineTo(x1, y1);
                    }
                }
                g.stroke(1, Colors.withAlpha(accent, 0.045));
            }
        }
    }

    /** Start-Portal und Basis. */
    public static void drawEndpoints(Gfx g, Path p, double time, double baseFlash) {
        double sx = p.x(0);
        double sy = p.y(0);
        double ex = p.x(p.pointCount() - 1);
        double ey = p.y(p.pointCount() - 1);
        double pulse = 0.5 + 0.5 * Math.sin(time * 3);

        // Portal: zwei gegenläufig rotierende gestrichelte Ringe
        Neon.halo(g, sx, sy, 56, SPAWN, 0.18 + 0.1 * pulse);
        g.beginPath();
        g.circle(sx, sy, 27);
        g.strokeDashed(2.4, Colors.withAlpha(SPAWN, 0.95), 9, 7, time * 24);
        g.beginPath();
        g.circle(sx, sy, 18);
        g.strokeDashed(2, Colors.withAlpha(SPAWN, 0.7), 5, 6, -time * 30);
        g.fillCircle(sx, sy, 5 + 2 * pulse, Colors.withAlpha(SPAWN, 0.9));

        // Basis: Sechseck mit pulsierendem Kern
        double flash = Math.min(1, baseFlash);
        int baseColor = Colors.lerp(BASE, 0xFFFFFF, flash * 0.6);
        Neon.halo(g, ex, ey, 64 + 20 * flash, BASE, 0.20 + 0.25 * flash + 0.06 * pulse);
        Neon.ngon(g, ex, ey, 30, 6, Math.PI / 6, 2.8, baseColor, 9 + 8 * flash);
        g.beginPath();
        g.ngon(ex, ey, 17 + 2 * pulse + 3 * flash, 6, Math.PI / 6);
        g.fill(Colors.withAlpha(BASE, 0.28 + 0.3 * flash));
        Neon.ngon(g, ex, ey, 17 + 2 * pulse, 6, Math.PI / 6, 1.6, Colors.withAlpha(baseColor, 0.9), 0);
    }
}

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

    /** Die Bahn in der Farbe der Welt. */
    public static void drawLane(Gfx g, Path p, double time, double alpha, int lane) {
        double half = World.LANE_HALF_WIDTH;
        g.save();
        g.alpha(alpha);
        polyline(g, p);
        if (Neon.quality >= 1) {
            g.additive(true);
            if (Neon.quality >= 2) {
                g.stroke(half * 2 + 30, Colors.withAlpha(lane, 0.035));
            }
            g.stroke(half * 2 + 16, Colors.withAlpha(lane, 0.07));
            g.additive(false);
        }
        g.stroke(half * 2 + 3.5, Colors.withAlpha(lane, 0.9));
        g.stroke(half * 2 - 1.5, Theme.LANE_FILL);
        g.strokeDashed(2, Colors.withAlpha(lane, 0.40), 10, 30, -time * 38);
        g.restore();
    }

    /** Hindernisse und Bonus-Pads. */
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
                neontd.gfx.Icons.Icon icon = z.kind == neontd.level.Zone.Kind.PAD_DAMAGE ? neontd.gfx.Icons.Icon.DAMAGE
                        : (z.kind == neontd.level.Zone.Kind.PAD_RANGE ? neontd.gfx.Icons.Icon.RANGE : neontd.gfx.Icons.Icon.SPEED);
                neontd.gfx.Icons.draw(g, icon, z.x, z.y, z.w * 0.3, Colors.withAlpha(c, 0.8), 0);
            } else if (z.round()) {
                g.fillCircle(z.x, z.y, z.w, 0x0C0C16);
                g.strokeCircle(z.x, z.y, z.w, 1.4, Colors.withAlpha(accent, 0.22));
                g.beginPath();
                g.circle(z.x, z.y, z.w * 0.55);
                g.strokeDashed(1, Colors.withAlpha(accent, 0.16), 4, 6, 0);
            } else {
                double x = z.x - z.w;
                double y = z.y - z.h;
                g.fillRoundRect(x, y, z.w * 2, z.h * 2, 10, 0x0C0C16);
                g.strokeRoundRect(x, y, z.w * 2, z.h * 2, 10, 1.4, Colors.withAlpha(accent, 0.22));
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
                g.stroke(1, Colors.withAlpha(accent, 0.07));
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

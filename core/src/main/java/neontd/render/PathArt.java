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
        double half = World.LANE_HALF_WIDTH;
        g.save();
        g.alpha(alpha);
        polyline(g, p);
        if (Neon.quality >= 1) {
            g.additive(true);
            if (Neon.quality >= 2) {
                g.stroke(half * 2 + 30, Colors.withAlpha(LANE, 0.035));
            }
            g.stroke(half * 2 + 16, Colors.withAlpha(LANE, 0.07));
            g.additive(false);
        }
        g.stroke(half * 2 + 3.5, Colors.withAlpha(LANE, 0.9));
        g.stroke(half * 2 - 1.5, Theme.LANE_FILL);
        g.strokeDashed(2, Colors.withAlpha(LANE, 0.40), 10, 30, -time * 38);
        g.restore();
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

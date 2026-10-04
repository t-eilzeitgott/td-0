package neontd.render;

import neontd.fx.Effects;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Neon;
import neontd.gfx.Theme;
import neontd.physics.Path;
import neontd.sim.Enemy;
import neontd.sim.Projectile;
import neontd.sim.Tower;
import neontd.sim.World;

/**
 * Zeichnet die Spielwelt in Weltkoordinaten (0..width × 0..height). Der Aufrufer legt Verschiebung und
 * Skalierung auf Bildschirmgröße vorher mit {@code translate/scale} fest.
 */
public final class WorldView {
    private WorldView() {
    }

    /** Karte: tiefes Schwarz mit sehr dezentem Raster und Rahmen. */
    public static void drawBackground(Gfx g, double w, double h) {
        g.fillRect(0, 0, w, h, Theme.BLACK);
        g.beginPath();
        for (double x = 40; x < w; x += 40) {
            g.moveTo(x, 0);
            g.lineTo(x, h);
        }
        for (double y = 40; y < h; y += 40) {
            g.moveTo(0, y);
            g.lineTo(w, y);
        }
        g.stroke(1, Colors.withAlpha(Theme.GRID, 0.9));
    }

    public static void drawFrame(Gfx g, double w, double h) {
        Neon.roundRect(g, 0, 0, w, h, 6, 2, Colors.withAlpha(Theme.CYAN, 0.55), 8);
    }

    public static void drawPaths(Gfx g, Path[] paths, double time, double baseFlash) {
        for (Path p : paths) {
            PathArt.drawLane(g, p, time, 1);
        }
        for (Path p : paths) {
            PathArt.drawEndpoints(g, p, time, baseFlash);
        }
    }

    public static void drawTowers(Gfx g, World world, double time) {
        for (int i = 0; i < world.towers.size(); i++) {
            TowerArt.drawTower(g, world.towers.get(i), time);
        }
    }

    public static void drawEnemies(Gfx g, World world, double alpha, double time) {
        // Weiter hinten laufende Gegner zuerst, damit vordere Gegner oben liegen.
        for (int i = world.enemies.size() - 1; i >= 0; i--) {
            Enemy e = world.enemies.get(i);
            if (!e.alive) {
                continue;
            }
            double x = e.prevX + (e.x - e.prevX) * alpha;
            double y = e.prevY + (e.y - e.prevY) * alpha;
            EnemyArt.draw(g, e, x, y, time);
        }
    }

    public static void drawProjectiles(Gfx g, World world, double alpha) {
        for (int i = 0; i < world.projectiles.size(); i++) {
            Projectile p = world.projectiles.get(i);
            if (!p.alive) {
                continue;
            }
            double x = p.prevX + (p.x - p.prevX) * alpha;
            double y = p.prevY + (p.y - p.prevY) * alpha;
            if (p.kind == Projectile.Kind.SHELL) {
                double arc = p.arc();
                g.save();
                g.alpha(0.35);
                g.fillCircle(x, y, 6 + 2 * arc, Colors.withAlpha(0x000000, 1));
                g.strokeCircle(x, y, 7 + 3 * arc, 1.2, Colors.withAlpha(p.color, 0.5));
                g.restore();
                double lift = arc * 46;
                Neon.halo(g, x, y - lift, 20, p.color, 0.35);
                g.fillCircle(x, y - lift, 5 + 2.5 * arc, Colors.lighten(p.color, 0.35));
                g.strokeCircle(x, y - lift, 5 + 2.5 * arc, 1.6, p.color);
            } else {
                g.save();
                g.additive(true);
                g.line(x - p.vx * 0.04, y - p.vy * 0.04, x, y, 4.5, Colors.withAlpha(p.color, 0.35));
                g.restore();
                g.line(x - p.vx * 0.022, y - p.vy * 0.022, x, y, 2.6, p.color);
                g.fillCircle(x, y, 2.8, 0xFFFFFF);
            }
        }
    }

    public static void drawEffects(Gfx g, Effects fx) {
        fx.render(g);
    }

    /** Reichweitenkreis (gestrichelt, mit zarter Füllung). */
    public static void drawRange(Gfx g, double x, double y, double range, int color, double time, boolean valid) {
        int c = valid ? color : Theme.RED;
        g.fillCircle(x, y, range, Colors.withAlpha(c, 0.055));
        g.beginPath();
        g.circle(x, y, range);
        g.strokeDashed(1.8, Colors.withAlpha(c, 0.75), 9, 8, -time * 12);
    }
}

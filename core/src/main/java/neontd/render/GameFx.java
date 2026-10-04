package neontd.render;

import neontd.fx.Effects;
import neontd.gfx.Colors;
import neontd.gfx.Theme;
import neontd.math.Mathx;
import neontd.sim.Enemy;
import neontd.sim.EnemyType;
import neontd.sim.Projectile;
import neontd.sim.SimListener;
import neontd.sim.Tower;
import neontd.sim.TowerType;
import neontd.sim.UpgradeTrack;

/** Übersetzt Simulationsereignisse in Effekte (Feuerwerk, Ringe, Strahlen …). */
public class GameFx implements SimListener {
    protected final Effects fx;
    /** Zeit seit dem letzten Verlust eines Lebens – lässt die Basis aufleuchten. */
    public double baseFlash;

    public GameFx(Effects fx) {
        this.fx = fx;
    }

    public Effects effects() {
        return fx;
    }

    @Override
    public void onEnemyDamaged(Enemy e, int damage) {
        double a = fx.particles.rng().angle();
        fx.sparks(e.x, e.y, a, 3.2, 2, 120, Theme.enemyColor(e.hp));
    }

    @Override
    public void onEnemyKilled(Enemy e, int reward) {
        int c = Theme.enemyColor(e.maxHp);
        double power = e.radius / 17.0;
        fx.firework(e.x, e.y, c, power);
        if (e.type == EnemyType.BOSS) {
            for (int i = 1; i <= 5; i++) {
                double a = fx.particles.rng().angle();
                double d = fx.particles.rng().range(10, 46);
                fx.fireworkLater(0.11 * i, e.x + Math.cos(a) * d, e.y + Math.sin(a) * d,
                        Colors.lerp(c, i % 2 == 0 ? 0xFFFFFF : Theme.CYAN, 0.35), 1.5);
            }
            fx.shake = 10;
        }
        fx.moneyPopup(e.x, e.y - e.radius - 4, reward, Theme.MONEY, 15);
    }

    @Override
    public void onEnemyLeaked(Enemy e, int livesLost) {
        fx.ring(e.x, e.y, 56, Theme.RED, 0.45);
        fx.flash(e.x, e.y, 46, Theme.RED, 0.3);
        fx.shake = Math.max(fx.shake, 5 + livesLost);
        baseFlash = 1;
    }

    @Override
    public void onExplosion(double x, double y, double radius, int color) {
        fx.explosion(x, y, radius, color);
    }

    @Override
    public void onNova(Tower t, double radius) {
        fx.ring(t.x, t.y, radius, t.type.color, 0.55);
        fx.ring(t.x, t.y, radius * 0.7, Colors.lighten(t.type.color, 0.5), 0.4);
        fx.flash(t.x, t.y, 34, t.type.color, 0.25);
    }

    @Override
    public void onBeam(double x1, double y1, double x2, double y2, int color) {
        fx.beam(x1, y1, x2, y2, color);
    }

    @Override
    public void onLightning(double[] pts, int count, int color) {
        fx.bolt(pts, count, color);
    }

    @Override
    public void onTowerFired(Tower t, double angle) {
        int c = t.type.color;
        double mx = t.x + Math.cos(angle) * 34;
        double my = t.y + Math.sin(angle) * 34;
        if (t.type == TowerType.PULSE) {
            fx.sparks(mx, my, angle, 0.35, 3, 160, c);
        } else if (t.type == TowerType.MORTAR) {
            fx.sparks(mx, my, angle, 0.5, 6, 140, c);
            fx.flash(mx, my, 20, c, 0.14);
        } else if (t.type == TowerType.SNIPER) {
            fx.flash(mx, my, 24, c, 0.16);
        }
    }

    @Override
    public void onProjectileSpawned(Projectile p) {
        // Schweif entsteht pro Frame in der Szene (siehe trail()).
    }

    @Override
    public void onTowerPlaced(Tower t) {
        fx.ring(t.x, t.y, 64, t.type.color, 0.5);
        fx.ring(t.x, t.y, 40, Colors.lighten(t.type.color, 0.5), 0.35);
        fx.sparks(t.x, t.y, -Math.PI / 2, Math.PI, 14, 150, t.type.color);
    }

    @Override
    public void onTowerUpgraded(Tower t, UpgradeTrack track) {
        fx.ring(t.x, t.y, 56, track.color, 0.5);
        fx.sparks(t.x, t.y - 10, -Math.PI / 2, 0.8, 12, 190, track.color);
        fx.flash(t.x, t.y, 44, track.color, 0.3);
    }

    @Override
    public void onTowerSold(Tower t, int refund) {
        fx.sparks(t.x, t.y, -Math.PI / 2, Math.PI, 16, 130, Theme.MONEY);
        fx.moneyPopup(t.x, t.y - 20, refund, Theme.MONEY, 17);
    }

    /** Schweif für ein fliegendes Geschoss; pro Frame aufrufen. */
    public void trail(Projectile p, double x, double y) {
        if (p.kind == Projectile.Kind.SHELL) {
            double h = p.arc() * 46;
            fx.particles.add(neontd.fx.Particles.DOT, x, y - h, 0, 0, 0.32, 3.6, Colors.lighten(p.color, 0.1));
        }
    }

    public void update(double dt) {
        fx.update(dt);
        baseFlash = Mathx.approach(baseFlash, 0, dt * 2.5);
    }
}

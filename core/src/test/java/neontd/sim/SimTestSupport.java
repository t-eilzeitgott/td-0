package neontd.sim;

import neontd.level.LevelDef;
import neontd.level.Levels;

/** Gemeinsame Helfer für Simulationstests. */
final class SimTestSupport {
    private SimTestSupport() {
    }

    /** Zählt Ereignisse. */
    static final class Counter implements SimListener {
        int spawned;
        int damaged;
        int killed;
        int leaked;
        int explosions;
        int novas;
        int beams;
        int lightnings;
        int maxChain;
        int bullets;
        int shells;
        int wavesCleared;
        Boolean ended;

        @Override public void onEnemySpawned(Enemy e) { spawned++; }
        @Override public void onEnemyDamaged(Enemy e, int d) { damaged++; }
        @Override public void onEnemyKilled(Enemy e, int r) { killed++; }
        @Override public void onEnemyLeaked(Enemy e, int l) { leaked++; }
        @Override public void onExplosion(double x, double y, double r, int c) { explosions++; }
        @Override public void onNova(Tower t, double r) { novas++; }
        @Override public void onBeam(double a, double b, double c, double d, int col) { beams++; }
        @Override public void onLightning(double[] p, int n, int c) { lightnings++; maxChain = Math.max(maxChain, n - 1); }
        @Override public void onProjectileSpawned(Projectile p) {
            if (p.kind == Projectile.Kind.BULLET) { bullets++; } else { shells++; }
        }
        @Override public void onWaveCleared(int i, int b) { wavesCleared++; }
        @Override public void onGameEnded(boolean won) { ended = won; }
    }

    static LevelDef level() {
        return Levels.serpentine();
    }

    /** Kleines Level mit kurzem Testlauf: eine Welle nach Vorgabe. */
    static World worldWithWaves(SimListener l, WaveDef... waves) {
        LevelDef def = level();
        return new World(def, java.util.Arrays.asList(waves), l);
    }

    static void steps(World w, int n) {
        for (int i = 0; i < n; i++) {
            w.step();
        }
    }

    /** Läuft, bis der Bot nichts mehr zu tun hat oder die Zeit abläuft. Rückgabe: simulierte Sekunden. */
    static double run(World w, AutoPlayer bot, double maxSeconds) {
        int steps = (int) (maxSeconds / World.STEP);
        for (int i = 0; i < steps && w.state == World.State.RUNNING; i++) {
            if (bot != null && i % 20 == 0) {
                bot.think();
            }
            w.step();
        }
        return w.time;
    }

    /** Ein stabiler Hash über den gesamten Spielzustand. */
    static long stateHash(World w) {
        long h = 17;
        h = h * 31 + w.money;
        h = h * 31 + w.lives;
        h = h * 31 + w.kills;
        h = h * 31 + w.enemies.size();
        for (Enemy e : w.enemies) {
            h = h * 31 + e.id;
            h = h * 31 + e.hp;
            h = h * 31 + Double.doubleToLongBits(e.dist);
        }
        for (Projectile p : w.projectiles) {
            h = h * 31 + Double.doubleToLongBits(p.x);
            h = h * 31 + Double.doubleToLongBits(p.y);
        }
        for (Tower t : w.towers) {
            h = h * 31 + Double.doubleToLongBits(t.cooldown);
        }
        return h;
    }
}

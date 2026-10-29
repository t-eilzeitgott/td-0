package neontd.sim;

import neontd.level.LevelDef;
import neontd.progress.Arena;

/**
 * Kein Test, sondern ein Messwerkzeug: Der Bot spielt jede Arena der Rotation bis zum Ende und gibt Welle, Punkte,
 * längste Serie und Spielzeit aus. Optional mit anderer Härte zum Kalibrieren.
 *
 * <pre>java -cp core/build/classes/java/main:core/build/classes/java/test neontd.sim.ArenaProbe [hpMul hpRamp]</pre>
 */
public final class ArenaProbe {
    private ArenaProbe() {
    }

    /** Ergebnis eines Bot-Laufs: {Welle, Punkte, Serie, Spielzeit}. */
    public static double[] run(LevelDef level, TowerType[] order, int maxWaves) {
        World w = new World(level, SimListener.NONE);
        w.arena = true;
        w.enableEndless();
        AutoPlayer bot = new AutoPlayer(w, true, 40, order);
        int step = 0;
        int last = -1;
        boolean verbose = Boolean.getBoolean("probe.verbose");
        int waveAt = 0;
        double since = 0;
        while (w.state == World.State.RUNNING && w.waveIndex <= maxWaves) {
            if (step % 20 == 0) {
                bot.think();
            }
            if (w.waveIndex != waveAt) {
                waveAt = w.waveIndex;
                since = w.time;
            } else if (w.time - since > 90) {
                w.startNextWave(); // festgehaltene Gegner (Graviton) – wie ein Mensch einfach weiter
            }
            w.step();
            step++;
            if (verbose && w.waveIndex != last && w.waveIndex % 5 == 0) {
                last = w.waveIndex;
                System.out.printf("   W%3d  Leben %2d  Geld %,8.0f  Türme %2d  Gegner %3d  Punkte %,10d  t=%5.0f%n",
                        w.waveIndex, w.lives, w.money, w.towers.size(), w.enemies.size(), w.score, w.time);
            }
        }
        if (verbose) {
            System.out.println("   Ende: " + w.state + " Welle " + w.waveIndex + " Leben " + w.lives + " Gegner " + w.enemies.size());
        }
        return new double[] {w.clearedWaves(), w.score, w.bestStreak, w.time};
    }

    public static void main(String[] args) {
        int week = Arena.weekOf(20_000);
        int only = Integer.getInteger("probe.only", -1);
        int maxWaves = Integer.getInteger("probe.max", 80);
        for (int i = 0; i < Arena.rotationSize(); i++) {
            if (only >= 0 && i != only) {
                continue;
            }
            Arena a = Arena.ofRotation(i, week);
            LevelDef l = a.build();
            if (args.length >= 2) {
                l.hpMul = Double.parseDouble(args[0]);
                l.hpRamp = Double.parseDouble(args[1]);
            }
            long t0 = System.nanoTime();
            double[] r = run(l, a.botOrder(), maxWaves);
            System.out.printf("%-12s Welle %3.0f  Punkte %,10.0f  Serie %2.0f  Spielzeit %5.0fs  (%.1fs real)%n",
                    a.def.name, r[0], r[1], r[2], r[3], (System.nanoTime() - t0) / 1e9);
        }
    }
}

package neontd.sim;

import neontd.level.Levels;

/**
 * Kein Test, sondern ein Messwerkzeug zum Kalibrieren des Endlosmodus: Ein Bot spielt Level 1 im Endlosmodus
 * und gibt in Abständen Kennzahlen aus.
 *
 * <pre>java -cp core/build/classes/java/main:core/build/classes/java/test neontd.sim.EndlessProbe [bisWelle] [schritt]</pre>
 */
public final class EndlessProbe {
    private EndlessProbe() {
    }

    public static void main(String[] args) {
        int target = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        int every = args.length > 1 ? Integer.parseInt(args[1]) : 25;
        World w = new World(Levels.serpentine(), SimListener.NONE);
        w.enableEndless();
        AutoPlayer bot = new AutoPlayer(w, true, 40, null);
        int step = 0;
        double lastTime = 0;
        int lastWave = 0;
        long t0 = System.nanoTime();
        while (w.state == World.State.RUNNING && w.waveIndex <= target) {
            if (step % 20 == 0) {
                bot.think();
            }
            w.step();
            step++;
            if (w.waveIndex != lastWave && w.waveIndex % every == 0) {
                double avg = (w.time - lastTime) / (w.waveIndex - lastWave);
                int maxD = 0;
                int maxS = 0;
                for (Tower t : w.towers) {
                    maxD = Math.max(maxD, t.level(UpgradeTrack.DAMAGE));
                    maxS = Math.max(maxS, t.level(UpgradeTrack.SPEED));
                }
                System.out.printf("Welle %4d  Spielzeit %7.0fs (%.1fs/Welle)  Leben %2d  Geld %,d  Türme %2d  maxStufe S%d T%d  Gegner %d  [%.1fs real]%n",
                        w.waveIndex, w.time, avg, w.lives, w.money, w.towers.size(), maxD, maxS, w.enemies.size(),
                        (System.nanoTime() - t0) / 1e9);
                lastTime = w.time;
                lastWave = w.waveIndex;
            }
        }
        System.out.println("Ende: " + w.state + " bei Welle " + w.waveIndex + ", Leben " + w.lives);
    }
}

package neontd.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neontd.level.Levels;
import org.junit.jupiter.api.Test;

/**
 * Balance-Leitplanken für Level 1: Ein solider (aber nicht cleverer) Bot soll gewinnen, ein Spieler, der nur
 * wenige Türme ohne Upgrades baut, soll scheitern. Die Ausgabe hilft beim Justieren der Zahlen.
 */
class BalanceTest {
    private static String play(String name, boolean upgrades, int maxTowers, TowerType[] order, World[] out) {
        World w = new World(Levels.serpentine(), SimListener.NONE);
        AutoPlayer bot = new AutoPlayer(w, upgrades, maxTowers, order);
        double t = SimTestSupport.run(w, bot, 60 * 40);
        out[0] = w;
        return String.format("%-10s state=%s wave=%d/%d lives=%d money=%d towers=%d kills=%d time=%.0fs",
                name, w.state, w.waveIndex, w.totalWaves(), w.lives, w.money, w.towers.size(), w.kills, t);
    }

    @Test
    void decentPlayerWinsLevelOne() {
        World[] out = new World[1];
        System.out.println(play("decent", true, 14, null, out));
        assertEquals(World.State.WON, out[0].state);
        assertTrue(out[0].lives >= 6, "lives=" + out[0].lives);
    }

    @Test
    void lazyPlayerLoses() {
        World[] out = new World[1];
        System.out.println(play("lazy", false, 4, new TowerType[] {TowerType.PULSE}, out));
        assertEquals(World.State.LOST, out[0].state);
    }

    @Test
    void waveCurveGrowsSteadily() {
        long last = 0;
        for (int w = 1; w <= 20; w++) {
            long hp = 0;
            for (WaveDef.Group g : WaveFactory.wave(w).groups) {
                if (g.type != EnemyType.BOSS) {
                    hp += (long) g.count * g.hp; // Bosswellen sind bewusst Ausreißer
                }
            }
            assertTrue(hp > last * 0.8, "Welle " + w + " nicht deutlich leichter als die vorige");
            last = hp;
        }
        System.out.println("Gesamt-HP Welle 1: " + WaveFactory.wave(1).totalHp()
                + ", Welle 10: " + WaveFactory.wave(10).totalHp() + ", Welle 20: " + WaveFactory.wave(20).totalHp());
    }
}

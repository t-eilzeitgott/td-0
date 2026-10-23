package neontd.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import neontd.sim.EnemyType;
import neontd.sim.WaveDef;

/** Die Kampagnenwellen: Stile, Gegner-Freischaltung und Abwechslung. */
class CampaignWavesTest {
    @org.junit.jupiter.api.Test
    void everyBuiltinLevelHasAStyleAndDeterministicWaves() {
        for (LevelDef l : Levels.builtins()) {
            assertTrue(l.style != null, l.name);
            assertEquals(l.number % 5 == 0, l.style == WaveStyle.TITANEN, l.name);
            assertEquals(signature(l.buildWaves(), 1, 20, 1), signature(l.buildWaves(), 1, 20, 1), l.name);
        }
    }

    @org.junit.jupiter.api.Test
    void enemyTypesAreGatedByChapter() {
        for (LevelDef l : Levels.chapter(1)) {
            for (WaveDef w : l.buildWaves()) {
                for (WaveDef.Group g : w.groups) {
                    assertTrue(g.type != EnemyType.HEALER && g.type != EnemyType.GHOST
                            && g.type != EnemyType.ARMORED && g.type != EnemyType.SHIELD,
                            "Kapitel 1 hat noch keine " + g.type + " (" + l.name + ")");
                }
            }
        }
    }

    @org.junit.jupiter.api.Test
    void chaptersFeelDifferent() {
        Set<String> fingerprints = new HashSet<>();
        for (LevelDef l : Levels.builtins()) {
            fingerprints.add(signature(l.buildWaves(), 5, 18, 6));
        }
        assertTrue(fingerprints.size() >= 40, "zu ähnliche Level: nur " + fingerprints.size() + " Wellenbilder");
    }

    private static String signature(List<WaveDef> waves, int from, int to, int step) {
        StringBuilder sb = new StringBuilder();
        for (int wi = from; wi <= to; wi += step) {
            for (WaveDef.Group g : waves.get(wi - 1).groups) {
                sb.append(g.type).append(g.count).append(':').append(g.hp).append(';');
            }
            sb.append('|');
        }
        return sb.toString();
    }
}

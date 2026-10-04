package neontd.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import neontd.progress.Medals;
import neontd.progress.Progress;
import neontd.sim.AutoPlayer;
import neontd.sim.SimListener;
import neontd.sim.WaveDef;
import neontd.sim.World;
import org.junit.jupiter.api.Test;

/** Die zehn eingebauten Level und ihre Medaillen. */
class LevelsTest {
    @Test
    void tenValidLevelsFromEasyToComplex() {
        List<LevelDef> all = Levels.builtins();
        assertEquals(10, all.size());
        Set<String> ids = new HashSet<>();
        int paths = 0;
        for (int i = 0; i < all.size(); i++) {
            LevelDef l = all.get(i);
            assertTrue(ids.add(l.id), "Kennung doppelt: " + l.id);
            assertNull(l.validate(), l.name);
            assertEquals(i + 1, l.difficulty);
            assertTrue(l.builtin);
            assertEquals(20, l.waveCount);
            assertEquals(i, Levels.indexOf(l.id));
            assertEquals(20, l.buildWaves().size());
            // jedes Level lässt Bauplätze neben der Bahn frei
            World w = new World(l, SimListener.NONE);
            int spots = 0;
            for (double y = 40; y < w.height - 30; y += 30) {
                for (double x = 40; x < w.width - 30; x += 30) {
                    if (w.checkPlacement(x, y) == World.PlaceCheck.OK) {
                        spots++;
                    }
                }
            }
            assertTrue(spots > 60, l.name + ": nur " + spots + " Bauplätze");
            paths = Math.max(paths, l.paths.size());
        }
        assertEquals(3, paths, "die späten Level haben mehrere Pfade");
        assertEquals(1, all.get(0).paths.size());
        assertTrue(all.get(9).totalLength() > all.get(0).totalLength());
    }

    @Test
    void harderLevelsScaleHitPoints() {
        LevelDef easy = Levels.serpentine();
        LevelDef hard = Levels.find("labyrinth");
        WaveDef a = easy.buildWaves().get(9);
        WaveDef b = hard.buildWaves().get(9);
        assertTrue(b.groups.get(0).hp > a.groups.get(0).hp);
        // Endlosmodus: spätere Wellen folgen demselben Faktor
        World w = new World(hard, SimListener.NONE);
        w.enableEndless();
        assertEquals(neontd.sim.WaveFactory.scaleHp(neontd.sim.WaveFactory.wave(30), hard.hpMul).groups.get(0).hp,
                w.waveAt(29).groups.get(0).hp);
    }

    @Test
    void medalsFollowTheWaveThresholds() {
        assertEquals(0, Medals.tier(19));
        assertEquals(1, Medals.tier(20));
        assertEquals(1, Medals.tier(49));
        assertEquals(2, Medals.tier(50));
        assertEquals(3, Medals.tier(100));
        assertEquals(3, Medals.tier(249));
        assertEquals(4, Medals.tier(250));
        assertEquals(4, Medals.tier(1000));
        assertEquals(Medals.XP[0] + Medals.XP[1], Medals.xpBetween(0, 2));
        assertEquals(0, Medals.xpBetween(2, 2));
        assertEquals(250, Medals.nextTarget(3));
        assertEquals(0, Medals.nextTarget(4));
    }

    @Test
    void levelsUnlockWithBronzeOfThePreviousOne() {
        Progress p = new Progress();
        assertTrue(p.levelUnlocked("serpentine"));
        assertFalse(p.levelUnlocked("zickzack"));
        assertTrue(p.levelUnlocked("c12eigenes"), "eigene Level sind immer offen");
        p.onRecord("serpentine", 19, false);
        assertFalse(p.levelUnlocked("zickzack"));
        p.onRecord("serpentine", 20, false);
        assertTrue(p.levelUnlocked("zickzack"));
        assertFalse(p.levelUnlocked("spirale"));
        // Endlos-Wellen zählen für die Medaille mit
        p.onRecord("zickzack", 55, true);
        assertEquals(2, p.medal("zickzack"));
        assertTrue(p.levelUnlocked("spirale"));
        assertEquals(3, p.totalMedals());
    }

    @Test
    void aSolidBotEarnsBronzeOnEveryLevel() {
        for (LevelDef l : Levels.builtins()) {
            World w = new World(l, SimListener.NONE);
            AutoPlayer bot = new AutoPlayer(w, true, 40, null);
            int steps = 0;
            while (w.state == World.State.RUNNING && steps < 60 * 3600) {
                if (steps % 20 == 0) {
                    bot.think();
                }
                w.step();
                steps++;
            }
            assertEquals(World.State.WON, w.state, l.name + ": Welle " + w.waveIndex + ", Leben " + w.lives);
            assertTrue(Medals.tier(w.clearedWaves()) >= 1);
        }
    }
}

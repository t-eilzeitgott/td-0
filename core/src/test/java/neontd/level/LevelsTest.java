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
    void tenChaptersWithFifteenValidLevelsEach() {
        List<LevelDef> all = Levels.builtins();
        assertEquals(150, all.size());
        Set<String> ids = new HashSet<>();
        Set<String> sigs = new HashSet<>();
        for (int i = 0; i < all.size(); i++) {
            LevelDef l = all.get(i);
            assertTrue(ids.add(l.id), "Kennung doppelt: " + l.id);
            assertNull(l.validate(), l.name);
            assertEquals(i / 15 + 1, l.chapter);
            assertEquals(i % 15 + 1, l.number);
            assertEquals(l.chapter, l.difficulty);
            assertTrue(l.builtin);
            assertEquals(20, l.waveCount);
            assertEquals(i, Levels.indexOf(l.id));
            assertEquals(20, l.buildWaves().size());
            assertTrue(l.hpMul > 0.3 && l.hpMul < 5, l.name + ": hpMul " + l.hpMul);
            sigs.add(java.util.Arrays.deepToString(l.paths.toArray()));
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
        }
        assertTrue(sigs.size() >= 120, "zu viele identische Pfade: nur " + sigs.size() + " verschiedene");
        assertEquals("serpentine", all.get(0).id);
        assertEquals("zickzack", all.get(15).id);
        assertEquals(15, Levels.chapter(10).size());
        assertEquals(3, all.get(149).paths.size());
        assertEquals(1, all.get(0).paths.size());
        for (int c = 1; c <= 10; c++) {
            List<LevelDef> ch = Levels.chapter(c);
            assertTrue(ch.get(14).hpMul > ch.get(1).hpMul, "Schwierigkeit steigt im Kapitel " + c);
        }
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
        assertFalse(p.levelUnlocked("serpentine-02"));
        assertTrue(p.levelUnlocked("c12eigenes"), "eigene Level sind immer offen");
        p.onRecord("serpentine", 19, false);
        assertFalse(p.levelUnlocked("serpentine-02"));
        p.onRecord("serpentine", 20, false);
        assertTrue(p.levelUnlocked("serpentine-02"));
        assertFalse(p.levelUnlocked("serpentine-03"));
        // Endlos-Wellen zählen für die Medaille mit
        p.onRecord("serpentine-02", 55, true);
        assertEquals(2, p.medal("serpentine-02"));
        assertTrue(p.levelUnlocked("serpentine-03"));
        assertEquals(3, p.totalMedals());
        // das nächste Kapitel öffnet mit Bronze im 10. Level des vorigen
        assertFalse(p.chapterUnlocked(2));
        for (int n = 3; n <= 10; n++) {
            p.onRecord(Levels.chapter(1).get(n - 1).id, 20, false);
        }
        assertEquals(10, p.chapterMedals(1, 1));
        assertTrue(p.chapterUnlocked(2));
        assertEquals(1, p.chapterMedals(1, 2));
        // schon gespielte Level bleiben offen (alte Spielstände)
        Progress old = new Progress();
        old.onRecord("zickzack", 25, false);
        assertTrue(old.levelUnlocked("zickzack"));
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
            assertTrue(w.lives >= 5, l.name + ": nur " + w.lives + " Leben übrig");
            assertTrue(Medals.tier(w.clearedWaves()) >= 1);
        }
    }
}

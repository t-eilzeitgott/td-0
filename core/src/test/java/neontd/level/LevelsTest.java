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
    void eightWorldsWithSixHandmadeLevelsEach() {
        List<LevelDef> all = Levels.builtins();
        assertEquals(Worlds.COUNT * Worlds.LEVELS, all.size());
        Set<String> ids = new HashSet<>();
        Set<String> sigs = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < all.size(); i++) {
            LevelDef l = all.get(i);
            assertTrue(ids.add(l.id), "Kennung doppelt: " + l.id);
            assertTrue(names.add(l.name), "Name doppelt: " + l.name);
            assertNull(l.validate(), l.name);
            assertEquals(i / Worlds.LEVELS + 1, l.chapter);
            assertEquals(i % Worlds.LEVELS + 1, l.number);
            assertTrue(l.builtin);
            assertEquals(20, l.waveCount);
            assertEquals(i, Levels.indexOf(l.id));
            assertEquals(20, l.buildWaves().size());
            assertTrue(l.hpMul > 0.1 && l.hpMul < 5, l.name + ": hpMul " + l.hpMul);
            assertFalse(l.blurb.isEmpty(), l.name + " hat keine Beschreibung");
            sigs.add(java.util.Arrays.deepToString(l.paths.toArray()));
            // jedes Level lässt Bauplätze neben der Bahn frei – auch mit Hindernissen
            World w = new World(l, SimListener.NONE);
            int spots = 0;
            for (double y = 40; y < w.height - 30; y += 30) {
                for (double x = 40; x < w.width - 30; x += 30) {
                    if (w.checkPlacement(x, y) == World.PlaceCheck.OK) {
                        spots++;
                    }
                }
            }
            assertTrue(spots > 200, l.name + ": nur " + spots + " Bauplätze");
            // Pads liegen frei (ein Turm passt darauf) und Hindernisse sperren
            for (Zone z : l.zones) {
                if (z.kind.pad()) {
                    assertEquals(World.PlaceCheck.OK, w.checkPlacement(z.x, z.y), l.name + ": Pad verdeckt");
                    assertEquals(z, w.padAt(z.x, z.y));
                } else {
                    assertTrue(w.checkPlacement(z.x, z.y) != World.PlaceCheck.OK, l.name + ": Hindernis sperrt nicht");
                }
            }
        }
        assertEquals(all.size(), sigs.size(), "alle Strecken sind verschieden");
        assertEquals(Levels.FIRST, all.get(0).id);
        assertEquals(Worlds.LEVELS, Levels.chapter(Worlds.COUNT).size());
        for (int c = 1; c <= Worlds.COUNT; c++) {
            List<LevelDef> ch = Levels.chapter(c);
            java.util.Set<WaveStyle> styles = new java.util.HashSet<>();
            for (LevelDef l : ch) {
                styles.add(l.style);
                assertTrue(l.style != null);
            }
            assertTrue(styles.size() >= 3, "Welt " + c + " spielt sich abwechslungsreich: " + styles);
            assertTrue(ch.get(Worlds.LEVELS - 1).bossLevel);
        }
    }

    @Test
    void padsStrengthenTheTowerStandingOnThem() {
        LevelDef l = Levels.find("stadt-1");
        World w = new World(l, SimListener.NONE);
        w.money = 10000;
        Zone pad = l.zones.get(0);
        neontd.sim.Tower on = w.placeTower(neontd.sim.TowerType.PULSE, pad.x, pad.y);
        neontd.sim.Tower off = w.placeTower(neontd.sim.TowerType.PULSE, pad.x, pad.y + 200);
        assertTrue(on != null && off != null);
        assertEquals(Zone.Kind.PAD_DAMAGE, on.pad);
        assertNull(off.pad);
        assertTrue(on.damage > off.damage);
    }

    @Test
    void starsFollowLivesAndUnlockWorlds() {
        assertEquals(0, LevelDef.starsFor(false, 20, 20));
        assertEquals(3, LevelDef.starsFor(true, 20, 20));
        assertEquals(2, LevelDef.starsFor(true, 10, 20));
        assertEquals(1, LevelDef.starsFor(true, 9, 20));
        Progress p = new Progress();
        assertTrue(p.worldUnlocked(1));
        assertFalse(p.worldUnlocked(2));
        assertTrue(p.levelUnlocked("stadt-1"));
        assertFalse(p.levelUnlocked("stadt-2"));
        assertEquals(2, p.onStars("stadt-1", 2));
        assertTrue(p.levelUnlocked("stadt-2"));
        assertEquals(1, p.onStars("stadt-1", 3));
        assertEquals(0, p.onStars("stadt-1", 2), "schlechter als vorher zählt nicht");
        assertEquals(3, p.stars("stadt-1"));
        assertEquals("stadt-2", p.nextLevel().id);
        for (LevelDef l : Levels.chapter(1)) {
            p.onStars(l.id, 2);
        }
        assertEquals(13, p.totalStars());
        assertTrue(p.worldUnlocked(2), "Welt 2 ab " + Worlds.REQUIRED_STARS[1] + " Sternen");
        assertFalse(p.worldUnlocked(3));
        assertTrue(p.levelUnlocked("tiefsee-1"));
        assertFalse(p.levelUnlocked("tiefsee-2"));
        assertEquals(Worlds.REQUIRED_STARS[2] - 13, p.starsToNextWorld());
        assertEquals(3, p.nextLockedWorld());
        // Kristalle und XP für Sterne
        assertTrue(p.crystals() > 0);
    }

    @Test
    void harderLevelsScaleHitPoints() {
        LevelDef easy = Levels.find("stadt-1");
        LevelDef hard = Levels.find("horizont-6");
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
    void playedLevelsStayOpenAndCustomLevelsAreAlwaysOpen() {
        Progress p = new Progress();
        assertTrue(p.levelUnlocked("c12eigenes"), "eigene Level sind immer offen");
        assertTrue(p.levelUnlocked("serpentine"), "die Referenz-Serpentine ist kein Kampagnenlevel");
        Progress old = new Progress();
        old.onRecord("tiefsee-3", 25, false);
        assertTrue(old.levelUnlocked("tiefsee-3"), "schon gespielte Level bleiben offen");
        // Endlos-Wellen zählen für die Medaille mit
        old.onRecord("tiefsee-3", 55, true);
        assertEquals(2, old.medal("tiefsee-3"));
        assertEquals(2, old.totalMedals());
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

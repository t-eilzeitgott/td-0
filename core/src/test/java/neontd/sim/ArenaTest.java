package neontd.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import neontd.level.LevelDef;
import neontd.level.Levels;
import neontd.progress.Arena;
import neontd.progress.Calendar;
import org.junit.jupiter.api.Test;

/** Arena: Kalenderwochen, faire Regeln, Punkte mit Serie und Vorlauf, Speichern, Determinismus und Härte. */
class ArenaTest {
    private static int day(int y, int m, int d) {
        return Calendar.daysFromCivil(y, m, d);
    }

    @Test
    void weeksFollowIso8601InLocalTime() {
        assertEquals("2026-W44", Arena.of(day(2026, 10, 29)).id);
        assertEquals("2026-W44", Arena.of(day(2026, 10, 26)).id); // Montag
        assertEquals("2026-W44", Arena.of(day(2026, 11, 1)).id); // Sonntag
        assertEquals("2026-W43", Arena.of(day(2026, 10, 25)).id);
        assertEquals("2026-W53", Arena.of(day(2027, 1, 1)).id);
        assertEquals("2020-W53", Arena.of(day(2021, 1, 3)).id);
        assertEquals("2025-W01", Arena.of(day(2024, 12, 30)).id);
        Arena a = Arena.of(day(2026, 10, 29));
        assertEquals(day(2026, 10, 26), a.firstDay());
        assertEquals(day(2026, 11, 2), a.endDay());
        assertEquals("2026-W43", a.previous().id);
        assertEquals("KW 44", a.label());
        // Restzeit: Donnerstag 12 Uhr Ortszeit bis Montag 0 Uhr = 3 Tage 12 Stunden
        double now = day(2026, 10, 29) * Calendar.DAY_MS + 11 * 3_600_000.0;
        assertEquals("3 T 12 Std", Arena.remainingText(a.remainingMillis(now, 3_600_000.0)));
        // Die Rotation wechselt jede Woche
        assertFalse(a.def == a.previous().def);
    }

    @Test
    void arenaLevelIsTheSameForEveryoneAndHasNoMasterLevels() {
        for (int i = 0; i < Arena.rotationSize(); i++) {
            Arena a = Arena.ofRotation(i, 3000);
            LevelDef l = a.build();
            assertNotNull(Levels.find(a.def.levelId), a.def.name);
            assertEquals(Arena.LIVES, l.startLives);
            assertEquals(a.def.money, l.startMoney);
            assertTrue(l.hpRamp > 0);
            assertTrue(Arena.isArena(l.id));
            assertEquals(5, a.def.pool.length);
            assertEquals(2, a.def.jokers.length);
            for (TowerType t : a.def.pool) {
                assertTrue(a.allows(t));
                assertFalse(t == TowerType.MINT, "keine Geldtürme in der Arena");
            }
        }
        World w = new World(Arena.ofRotation(0, 3000).build(), SimListener.NONE);
        w.arena = true;
        w.enableEndless();
        for (UpgradeTrack t : UpgradeTrack.values()) {
            assertEquals(UpgradeTrack.MAX_LEVEL, w.maxLevel(t));
        }
    }

    private static World scoringWorld(SimListener l, WaveDef... waves) {
        LevelDef def = Levels.serpentine();
        def.startMoney = 1_000_000;
        def.startLives = 10;
        World w = new World(def, Arrays.asList(waves), l);
        w.arena = true;
        w.enableEndless();
        return w;
    }

    private static void strongTower(World w) {
        double[] spot = {300, 250};
        Tower t = w.placeTower(TowerType.RAILGUN, spot[0], spot[1]);
        if (t == null) {
            neontd.math.Vec2 v = new neontd.math.Vec2();
            w.paths[0].positionAt(300, v);
            t = w.placeTower(TowerType.RAILGUN, v.x, v.y + 80);
        }
        assertNotNull(t);
        for (int i = 0; i < UpgradeTrack.MAX_LEVEL; i++) {
            w.upgrade(t, UpgradeTrack.DAMAGE);
            w.upgrade(t, UpgradeTrack.RANGE);
        }
    }

    private static void runUntilCleared(World w, int cleared, int maxSteps) {
        for (int i = 0; i < maxSteps && w.clearedWaves() < cleared && w.state == World.State.RUNNING; i++) {
            w.step();
        }
    }

    @Test
    void killsAndWavesScoreWithStreakMultiplier() {
        WaveDef w1 = new WaveDef().add(EnemyType.BLOCK, 3, 0.6, 0, 1).bonus(0);
        WaveDef w2 = new WaveDef().add(EnemyType.BLOCK, 3, 0.6, 0, 1).bonus(0);
        World w = scoringWorld(SimListener.NONE, w1, w2, new WaveDef().add(EnemyType.BLOCK, 1, 1, 0, 1));
        strongTower(w);
        w.startNextWave();
        runUntilCleared(w, 1, 60 * 60);
        // Welle 1: 3 Abschüsse × Prämie 3 × Welle 1 × ×1,0 + 100 × 1 × ×1,0
        long expected1 = 3 * EnemyType.BLOCK.reward + World.WAVE_POINTS;
        assertEquals(expected1, w.score);
        assertEquals(1, w.streak);
        assertEquals(expected1, w.splitAt(1));
        assertEquals(1.1, w.multiplier(), 1e-9);
        w.startNextWave();
        runUntilCleared(w, 2, 60 * 60);
        // Welle 2: alles × 1,1 und Wellennummer 2
        long kills2 = 3 * Math.round(EnemyType.BLOCK.reward * 2 * 1.1);
        long wave2 = Math.round(World.WAVE_POINTS * 2 * 1.1);
        assertEquals(expected1 + kills2 + wave2, w.score);
        assertEquals(2, w.streak);
        assertEquals(expected1 + "," + w.score, w.splitsText());
    }

    @Test
    void aLeakBreaksTheStreakAndEarlyStartDoublesTheWaveBonus() {
        int[] broken = {0};
        long[] wavePts = new long[8];
        boolean[] waveEarly = new boolean[8];
        SimListener l = new SimListener() {
            @Override
            public void onStreakBroken(int streak) {
                broken[0] = streak;
            }

            @Override
            public void onArenaWave(int index, long points, boolean early, int streak) {
                wavePts[index] = points;
                waveEarly[index] = early;
            }
        };
        WaveDef easy = new WaveDef().add(EnemyType.BLOCK, 1, 1, 0, 1).bonus(0);
        WaveDef tanky = new WaveDef().add(EnemyType.DART, 1, 1, 0, 50_000_000).bonus(0);
        World w = scoringWorld(l, easy, easy, tanky, easy, easy);
        strongTower(w);
        w.startNextWave();
        runUntilCleared(w, 1, 60 * 60);
        w.startNextWave();
        runUntilCleared(w, 2, 60 * 60);
        assertEquals(2, w.streak);
        // Der zähe Dart kommt durch: Serie weg
        w.startNextWave();
        runUntilCleared(w, 3, 60 * 120);
        assertEquals(0, w.streak);
        assertEquals(2, broken[0]);
        assertEquals(2, w.bestStreak);
        assertTrue(w.lives < 10);
        // Vorlauf: Welle 5 startet, solange Welle 4 noch läuft
        assertFalse(w.wouldBeEarly());
        w.startNextWave();
        assertTrue(w.wouldBeEarly());
        w.startNextWave();
        runUntilCleared(w, 5, 60 * 60);
        assertFalse(waveEarly[3]);
        assertTrue(waveEarly[4]);
        // Welle 4: 100 × 4 × (×1,0 oder ×1,1); Welle 5: doppelt, 100 × 5 × 2 × (×1,0 … ×1,2)
        assertTrue(wavePts[3] == 400 || wavePts[3] == 440, "Welle 4: " + wavePts[3]);
        assertTrue(wavePts[4] == 1000 || wavePts[4] == 1100 || wavePts[4] == 1200, "Welle 5: " + wavePts[4]);
        assertEquals(2, w.streak);
    }

    @Test
    void arenaStateSurvivesSnapshotAndJson() {
        Arena a = Arena.ofRotation(0, 3000);
        LevelDef l = a.build();
        World w = new World(l, SimListener.NONE);
        w.arena = true;
        w.enableEndless();
        AutoPlayer bot = new AutoPlayer(w, true, 20, a.botOrder());
        int guard = 0;
        while (w.clearedWaves() < 4 && guard++ < 60 * 600) {
            if (guard % 20 == 0) {
                bot.think();
            }
            w.step();
        }
        while (!w.canSnapshot()) {
            w.step();
        }
        WorldSnapshot snap = w.snapshot();
        assertTrue(snap.arena);
        neontd.save.RunSave back = neontd.save.RunSave.decode(l.id, neontd.save.RunSave.of(l.id, 1, snap).encode());
        World r = new World(l, SimListener.NONE);
        r.restore(back.snapshot);
        assertTrue(r.arena);
        assertEquals(w.score, r.score);
        assertEquals(w.streak, r.streak);
        assertEquals(w.splitsText(), r.splitsText());
        assertEquals(w.wouldBeEarly(), r.wouldBeEarly());
    }

    @Test
    void sameArenaSameMovesSameScore() {
        Arena a = Arena.ofRotation(3, 3000);
        double[] r1 = ArenaProbe.run(a.build(), a.botOrder(), 12);
        double[] r2 = ArenaProbe.run(a.build(), a.botOrder(), 12);
        assertEquals(r1[1], r2[1], 1e-9);
        assertEquals(r1[0], r2[0], 1e-9);
    }

    @Test
    void everyArenaIsHardButNotHopelessForTheBot() {
        for (int i = 0; i < Arena.rotationSize(); i++) {
            Arena a = Arena.ofRotation(i, 3000);
            double[] r = ArenaProbe.run(a.build(), a.botOrder(), 70);
            assertTrue(r[0] >= 15 && r[0] <= 45, a.def.name + ": Bot kam bis Welle " + r[0]);
            assertTrue(r[1] > 0 && r[1] < 400.0 * r[0] * r[0] * r[0] + 100_000, a.def.name + ": " + r[1]);
        }
    }
}

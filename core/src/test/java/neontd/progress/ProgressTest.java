package neontd.progress;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neontd.sim.TowerType;
import org.junit.jupiter.api.Test;

class ProgressTest {
    @Test
    void levelCurveIsMonotonicAndConsistent() {
        int prev = 0;
        for (int l = 1; l < Progress.MAX_LEVEL; l++) {
            int at = Progress.xpAtLevel(l);
            assertTrue(at >= prev);
            assertEquals(l, Progress.levelForXp(at), "Level " + l);
            assertEquals(l, Progress.levelForXp(Progress.xpAtLevel(l + 1) - 1));
            prev = at;
        }
        assertEquals(1, Progress.levelForXp(0));
        assertEquals(Progress.MAX_LEVEL, Progress.levelForXp(Progress.MAX_XP));
    }

    @Test
    void startsWithOnlyPulseTowerUnlocked() {
        Progress p = new Progress();
        assertEquals(1, p.level());
        assertTrue(p.isUnlocked(TowerType.PULSE));
        for (TowerType t : TowerType.values()) {
            if (t != TowerType.PULSE) {
                assertFalse(p.isUnlocked(t), t + " muss gesperrt sein");
            }
        }
        assertEquals(2, p.nextUnlockLevel());
        assertEquals(0, p.startMoneyBonus());
        assertEquals(0, p.startLivesBonus());
    }

    @Test
    void firstFullCampaignRunUnlocksFrostAndMortar() {
        Progress p = new Progress();
        for (int w = 1; w <= 20; w++) {
            p.onWaveCleared(w, w % 10 == 0);
        }
        p.onWon("serpentine");
        assertTrue(p.level() >= 4, "Level nach dem ersten Sieg: " + p.level());
        assertTrue(p.level() <= 6);
        assertTrue(p.isUnlocked(TowerType.FROST));
        assertTrue(p.isUnlocked(TowerType.MORTAR));
        assertFalse(p.isUnlocked(TowerType.SNIPER));
        assertTrue(p.endlessUnlocked("serpentine"));
        assertEquals(1, p.gamesWon);
        // Der erste Sieg bringt den Einmalbonus, jeder weitere nur noch den normalen.
        double before = p.xp;
        assertEquals(Progress.WIN_XP, p.onWon("serpentine"));
        assertEquals(before + Progress.WIN_XP, p.xp);
    }

    @Test
    void thousandEndlessWavesGiveAHighButFiniteLevel() {
        Progress p = new Progress();
        for (int w = 1; w <= 1000; w++) {
            p.onWaveCleared(w, w % 10 == 0);
        }
        assertTrue(p.level() >= 45 && p.level() < 80, "Level nach 1000 Wellen: " + p.level());
        assertEquals("MEISTER", Progress.titleFor(45));
        assertEquals(5, p.startLivesBonus());
        assertEquals(300, p.startMoneyBonus());
    }

    @Test
    void xpNeverOverflows() {
        Progress p = new Progress();
        p.xp = Progress.MAX_XP - 5;
        p.addXp(Integer.MAX_VALUE);
        assertEquals(Progress.MAX_XP, p.xp);
        assertEquals(0, p.addXp(-4));
        assertEquals(1.0, p.levelFraction(), 0.0);
    }

    @Test
    void levelUpReportsGainedLevels() {
        Progress p = new Progress();
        assertEquals(0, p.addXp(10));
        assertEquals(1, p.addXp(Progress.xpForNext(1)));
        assertEquals(2, p.level());
        assertArrayEquals(new TowerType[] {TowerType.FROST}, Progress.unlockedAt(2));
        assertEquals(0, Progress.unlockedAt(3).length);
    }

    @Test
    void recordsKeepTheBestValueOnly() {
        Progress p = new Progress();
        p.onRecord("a", 12, false);
        p.onRecord("a", 7, false);
        p.onRecord("a", 300, true);
        assertEquals(12, p.best("a", false));
        assertEquals(300, p.best("a", true));
        assertEquals(0, p.best("b", true));
    }

    @Test
    void namesAreCleaned() {
        assertEquals("", Progress.cleanName(null));
        assertEquals("Anna", Progress.cleanName("  Anna \n"));
        assertEquals("abcdefghijklmnop", Progress.cleanName("abcdefghijklmnopqrstuvw"));
        assertEquals("ab", Progress.cleanName("a<b>"));
        assertEquals("SPIELER", new Progress().displayName());
    }

    @Test
    void xpGoesFarBeyondTheIntRange() {
        assertEquals(9.0e15, Progress.MAX_XP);
        Progress p = new Progress();
        p.xp = 5_000_000_000.0;
        assertEquals(Progress.MAX_LEVEL, p.level());
        p.addXp(1_000_000_000_000.0);
        assertEquals(1_005_000_000_000.0, p.xp);
        p.addXp(Progress.MAX_XP);
        assertEquals(Progress.MAX_XP, p.xp);
        Progress q = neontd.save.ProfileJson.fromMap(neontd.save.ProfileJson.toMap(p));
        assertEquals(Progress.MAX_XP, q.xp, "Speichern und Laden behält den vollen Betrag");
        assertEquals(1.0, q.levelFraction());
    }
}

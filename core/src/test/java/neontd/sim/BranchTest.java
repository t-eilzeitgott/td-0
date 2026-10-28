package neontd.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neontd.level.LevelDef;
import neontd.level.Levels;
import org.junit.jupiter.api.Test;

/** Ausbaupfade: Freischaltung über Upgrade-Stufen, gegenseitiger Ausschluss, Wirkung und Speichern. */
class BranchTest {
    private static double[] spotNear(World w, double s, double side) {
        neontd.math.Vec2 v = new neontd.math.Vec2();
        w.paths[0].positionAt(s, v);
        double h = w.paths[0].headingAt(s);
        return new double[] {v.x - Math.sin(h) * side, v.y + Math.cos(h) * side};
    }

    private static World world() {
        LevelDef def = Levels.serpentine();
        def.startMoney = 1_000_000;
        return new World(def, SimListener.NONE);
    }

    private static Tower tower(World w, TowerType type) {
        double[] spot = spotNear(w, 300, 80);
        Tower t = w.placeTower(type, spot[0], spot[1]);
        assertNotNull(t, type.name());
        return t;
    }

    @Test
    void everyBaseTowerHasTwoBranchesAndEliteTowersNone() {
        for (TowerType t : TowerType.values()) {
            Branch[] b = Branch.of(t);
            if (t.tier > 1 || t.passive) {
                assertEquals(0, b.length, t.name());
            } else {
                assertEquals(2, b.length, t.name());
                assertEquals(1, b[0].side());
                assertEquals(2, b[1].side());
                assertTrue(Branch.cost(t, 2) > Branch.cost(t, 1));
            }
        }
    }

    @Test
    void branchNeedsUpgradeLevelsLocksTheOtherPathAndCapsAtRankTwo() {
        World w = world();
        Tower t = tower(w, TowerType.PULSE);
        Branch a = Branch.PULSE_A;
        Branch b = Branch.PULSE_B;
        assertEquals(Branch.NEED_LEVELS_1, w.branchLevelsMissing(t, a));
        assertFalse(w.buyBranch(t, a), "ohne Upgrades gesperrt");
        for (int i = 0; i < Branch.NEED_LEVELS_1; i++) {
            assertTrue(w.upgrade(t, UpgradeTrack.values()[i % UpgradeTrack.values().length]));
        }
        assertEquals(0, w.branchLevelsMissing(t, a));
        double money = w.money;
        assertTrue(w.buyBranch(t, a));
        assertEquals(1, t.branch);
        assertEquals(1, t.rank);
        assertEquals(Branch.cost(TowerType.PULSE, 1), money - w.money, 1e-9);
        assertEquals(a, Branch.chosen(t));
        // der andere Pfad ist jetzt zu
        assertEquals(-1, w.branchCost(t, b));
        assertFalse(w.buyBranch(t, b));
        // Rang 2 erst ab NEED_LEVELS_2
        assertTrue(w.branchLevelsMissing(t, a) > 0);
        assertFalse(w.buyBranch(t, a));
        while (t.totalLevels() < Branch.NEED_LEVELS_2) {
            boolean any = false;
            for (UpgradeTrack tr : UpgradeTrack.values()) {
                any |= w.upgrade(t, tr);
            }
            assertTrue(any);
        }
        assertTrue(w.buyBranch(t, a));
        assertEquals(2, t.rank);
        assertEquals(-1, w.branchCost(t, a), "voll ausgebaut");
        assertFalse(w.buyBranch(t, a));
    }

    @Test
    void trommelfeuerRaisesFireRateAndKristallRange() {
        World w = world();
        Tower s = tower(w, TowerType.SALVE);
        for (int i = 0; i < Branch.NEED_LEVELS_1; i++) {
            w.upgrade(s, UpgradeTrack.DAMAGE);
        }
        double before = s.effectiveInterval();
        assertTrue(w.buyBranch(s, Branch.SALVE_A));
        assertTrue(s.effectiveInterval() < before * 0.85, before + " -> " + s.effectiveInterval());

        double[] spot = spotNear(w, 900, 80);
        Tower f = w.placeTower(TowerType.FROST, spot[0], spot[1]);
        assertNotNull(f);
        for (int i = 0; i < Branch.NEED_LEVELS_1; i++) {
            w.upgrade(f, UpgradeTrack.DAMAGE);
        }
        double range = f.range;
        assertTrue(w.buyBranch(f, Branch.FROST_B));
        assertTrue(f.range > range * 1.2, range + " -> " + f.range);
    }

    @Test
    void zwillingFiresMoreBulletsPerShot() {
        LevelDef def = Levels.serpentine();
        def.startMoney = 1_000_000;
        WaveDef wave = new WaveDef().add(EnemyType.TANK, 1, 1.0, 0, 100_000_000).bonus(0);
        WaveDef next = new WaveDef().add(EnemyType.BLOCK, 1, 1, 0, 5);
        SimTestSupport.Counter plain = new SimTestSupport.Counter();
        World w1 = new World(def, java.util.Arrays.asList(wave, next), plain);
        Tower t1 = tower(w1, TowerType.PULSE);
        w1.startNextWave();
        SimTestSupport.steps(w1, 60 * 6);

        SimTestSupport.Counter twin = new SimTestSupport.Counter();
        World w2 = new World(def, java.util.Arrays.asList(wave, next), twin);
        Tower t2 = tower(w2, TowerType.PULSE);
        for (int i = 0; i < Branch.NEED_LEVELS_1; i++) {
            w2.upgrade(t2, UpgradeTrack.RANGE);
            w1.upgrade(t1, UpgradeTrack.RANGE);
        }
        assertTrue(w2.buyBranch(t2, Branch.PULSE_A));
        w2.startNextWave();
        SimTestSupport.steps(w2, 60 * 6);
        assertTrue(plain.bullets > 0);
        assertTrue(twin.bullets >= plain.bullets * 1.6, plain.bullets + " vs " + twin.bullets);
    }

    @Test
    void branchSurvivesSnapshotAndJson() {
        World w = world();
        Tower t = tower(w, TowerType.MORTAR);
        for (int i = 0; i < Branch.NEED_LEVELS_1; i++) {
            w.upgrade(t, UpgradeTrack.DAMAGE);
        }
        assertTrue(w.buyBranch(t, Branch.MORTAR_B));
        WorldSnapshot snap = w.snapshot();
        LevelDef def = w.level;
        World r = new World(def, SimListener.NONE);
        r.restore(snap);
        assertEquals(2, r.towers.get(0).branch);
        assertEquals(1, r.towers.get(0).rank);
        assertEquals(Branch.MORTAR_B, Branch.chosen(r.towers.get(0)));
        neontd.save.RunSave back = neontd.save.RunSave.decode(def.id, neontd.save.RunSave.of(def.id, 1, snap).encode());
        World r2 = new World(def, SimListener.NONE);
        r2.restore(back.snapshot);
        assertEquals(2, r2.towers.get(0).branch);
        assertEquals(1, r2.towers.get(0).rank);
        assertEquals(t.invested, r2.towers.get(0).invested, 1e-9);
    }
}

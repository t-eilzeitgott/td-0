package neontd.sim;

import static neontd.sim.SimTestSupport.run;
import static neontd.sim.SimTestSupport.steps;
import static neontd.sim.SimTestSupport.worldWithWaves;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neontd.level.LevelDef;
import neontd.level.Levels;
import neontd.sim.SimTestSupport.Counter;
import org.junit.jupiter.api.Test;

class SimTest {
    private static double[] spotNear(World w, double s, double side) {
        // Ein gültiger Bauplatz seitlich vom Pfad bei Bogenlänge s.
        neontd.math.Vec2 v = new neontd.math.Vec2();
        w.paths[0].positionAt(s, v);
        double h = w.paths[0].headingAt(s);
        return new double[] {v.x - Math.sin(h) * side, v.y + Math.cos(h) * side};
    }

    @Test
    void placementRulesAreEnforced() {
        World w = new World(Levels.serpentine(), SimListener.NONE);
        double[] spot = spotNear(w, 400, 80);
        assertEquals(World.PlaceCheck.OK, w.checkPlacement(spot[0], spot[1]));
        assertEquals(World.PlaceCheck.OUT_OF_BOUNDS, w.checkPlacement(5, 5));
        neontd.math.Vec2 onPath = new neontd.math.Vec2();
        w.paths[0].positionAt(900, onPath);
        assertEquals(World.PlaceCheck.ON_PATH, w.checkPlacement(onPath.x, onPath.y));

        double before = w.money;
        Tower t = w.placeTower(TowerType.PULSE, spot[0], spot[1]);
        assertNotNull(t);
        assertEquals(before - TowerType.PULSE.cost, w.money);
        assertEquals(World.PlaceCheck.OVERLAP, w.checkPlacement(spot[0] + 20, spot[1]));
        assertNull(w.placeTower(TowerType.PULSE, spot[0] + 20, spot[1]));
        assertSame(t, w.towerAt(spot[0] + 5, spot[1], 0));
    }

    private static void assertSame(Object a, Object b) {
        assertTrue(a == b);
    }

    @Test
    void cannotBuildWithoutMoney() {
        LevelDef def = Levels.serpentine();
        def.startMoney = 50;
        World w = new World(def, SimListener.NONE);
        double[] spot = spotNear(w, 400, 80);
        assertNull(w.placeTower(TowerType.PULSE, spot[0], spot[1]));
        assertEquals(50, w.money);
    }

    @Test
    void upgradesCostMoneyAndImproveStats() {
        LevelDef def = Levels.serpentine();
        def.startMoney = 100000;
        World w = new World(def, SimListener.NONE);
        double[] spot = spotNear(w, 400, 80);
        Tower t = w.placeTower(TowerType.PULSE, spot[0], spot[1]);
        double range0 = t.range;
        int damage0 = t.damage;
        double interval0 = t.interval;
        double invested = t.invested;
        for (UpgradeTrack track : UpgradeTrack.values()) {
            for (int lvl = 0; lvl < UpgradeTrack.MAX_LEVEL; lvl++) {
                int cost = w.upgradeCost(t, track);
                assertTrue(cost > 0, "Preis vorhanden");
                double money = w.money;
                assertTrue(w.upgrade(t, track));
                assertEquals(money - cost, w.money);
                invested += cost;
            }
            assertEquals(-1, w.upgradeCost(t, track), "voll ausgebaut");
            assertFalse(w.upgrade(t, track));
        }
        assertTrue(t.range > range0 * 1.5);
        assertTrue(t.damage > damage0 * 6);
        assertTrue(t.interval < interval0 / 2.9);
        assertEquals(invested, t.invested);
        assertEquals(15, t.totalLevels());
        double refund = w.sellValue(t);
        double money = w.money;
        w.sell(t);
        assertEquals(money + refund, w.money);
        assertEquals(0, w.towers.size());
    }

    @Test
    void unprotectedWaveLeaksAndCostsLives() {
        Counter c = new Counter();
        World w = new World(Levels.serpentine(), c);
        assertTrue(w.startNextWave());
        run(w, null, 120);
        assertEquals(8, c.spawned);
        assertEquals(8, c.leaked);
        assertEquals(20 - 8, w.lives);
        assertEquals(1, c.wavesCleared);
        assertEquals(0, w.enemiesRemaining());
    }

    @Test
    void losingAllLivesEndsTheGame() {
        Counter c = new Counter();
        LevelDef def = Levels.serpentine();
        def.startLives = 3;
        World w = new World(def, c);
        w.startNextWave();
        run(w, null, 120);
        assertEquals(World.State.LOST, w.state);
        assertEquals(Boolean.FALSE, c.ended);
        assertEquals(0, w.lives);
    }

    @Test
    void pulseTowersDefendTheFirstWave() {
        Counter c = new Counter();
        World w = new World(Levels.serpentine(), c);
        AutoPlayer bot = new AutoPlayer(w, false, 3, new TowerType[] {TowerType.PULSE});
        bot.startWaves = false;
        w.startNextWave();
        run(w, bot, 120);
        assertEquals(3, w.towers.size());
        assertEquals(8, c.killed);
        assertEquals(0, c.leaked);
        assertEquals(20, w.lives);
        assertTrue(w.money > 450 - 300, "Abschussprämien und Wellenbonus fließen ein");
    }

    @Test
    void leadAimingHitsMostBullets() {
        // Ein einzelner Pulse-Turm gegen einen gleichmäßigen Strom: Dank Zielvorhersage und kontinuierlicher
        // Kollision treffen fast alle Kugeln.
        Counter c = new Counter();
        WaveDef wave = new WaveDef().add(EnemyType.TANK, 6, 1.0, 0, 100000).bonus(0);
        World w = worldWithWaves(c, wave);
        double[] spot = spotNear(w, 600, 70);
        assertNotNull(w.placeTower(TowerType.PULSE, spot[0], spot[1]));
        w.startNextWave();
        steps(w, 60 * 40);
        assertTrue(c.bullets > 20, "bullets=" + c.bullets);
        double hitRate = (double) c.damaged / c.bullets;
        assertTrue(hitRate > 0.9, "Trefferquote " + hitRate + " (" + c.damaged + "/" + c.bullets + ")");
    }

    @Test
    void sniperFiresInstantBeams() {
        Counter c = new Counter();
        WaveDef wave = new WaveDef().add(EnemyType.BLOCK, 5, 1.0, 0, 100).bonus(0);
        World w = worldWithWaves(c, wave);
        double[] spot = spotNear(w, 600, 70);
        w.placeTower(TowerType.SNIPER, spot[0], spot[1]);
        w.startNextWave();
        steps(w, 60 * 30);
        assertTrue(c.beams > 3, "beams=" + c.beams);
        assertTrue(c.damaged >= c.beams);
        assertEquals(0, c.bullets);
    }

    @Test
    void mortarSplashDamagesGroupsAndFrostSlows() {
        Counter c = new Counter();
        WaveDef wave = new WaveDef().add(EnemyType.TANK, 8, 0.4, 0, 100000).bonus(0);
        World w = worldWithWaves(c, wave);
        double[] a = spotNear(w, 600, 75);
        double[] b = spotNear(w, 700, -75);
        w.placeTower(TowerType.MORTAR, a[0], a[1]);
        w.placeTower(TowerType.FROST, b[0], b[1]);
        w.startNextWave();
        double minSlow = 1;
        for (int i = 0; i < 60 * 40; i++) {
            w.step();
            for (Enemy e : w.enemies) {
                minSlow = Math.min(minSlow, e.slowFactor);
            }
        }
        assertTrue(c.shells > 3, "shells=" + c.shells);
        assertTrue(c.explosions > 3, "explosions=" + c.explosions);
        assertTrue(c.novas > 3, "novas=" + c.novas);
        assertTrue(minSlow < 0.6, "minSlow=" + minSlow);
        // Splash trifft mehrere Gegner pro Explosion.
        assertTrue(c.damaged > c.explosions, "damaged=" + c.damaged + " explosions=" + c.explosions);
    }

    @Test
    void arcLightningChainsBetweenEnemies() {
        Counter c = new Counter();
        WaveDef wave = new WaveDef().add(EnemyType.TANK, 8, 0.35, 0, 100000).bonus(0);
        World w = worldWithWaves(c, wave);
        double[] spot = spotNear(w, 600, 70);
        w.placeTower(TowerType.ARC, spot[0], spot[1]);
        w.startNextWave();
        steps(w, 60 * 40);
        assertTrue(c.lightnings > 3, "lightnings=" + c.lightnings);
        assertTrue(c.maxChain >= 3, "maxChain=" + c.maxChain);
    }

    @Test
    void bigTowersCostMoreAndAreStrongerThanTheClassics() {
        TowerType[] big = {TowerType.SALVE, TowerType.TESLA, TowerType.RAILGUN};
        int maxClassic = 0;
        double bestDps = 0;
        for (TowerType t : new TowerType[] {TowerType.PULSE, TowerType.SNIPER, TowerType.MORTAR, TowerType.FROST, TowerType.ARC}) {
            maxClassic = Math.max(maxClassic, t.cost);
            bestDps = Math.max(bestDps, t.damage / t.interval);
        }
        for (TowerType t : big) {
            assertTrue(t.cost > maxClassic, t + " ist teurer als jeder Klassiker");
            assertTrue(t.damage / t.interval > bestDps, t + " macht mehr Schaden pro Sekunde");
        }
    }

    @Test
    void salveBulletsPierceSeveralEnemies() {
        Counter c = new Counter();
        WaveDef wave = new WaveDef().add(EnemyType.TANK, 8, 0.3, 0, 100000).bonus(0);
        World w = worldWithWaves(c, wave);
        double[] spot = spotNear(w, 600, 70);
        Tower t = w.placeTower(TowerType.SALVE, spot[0], spot[1]);
        w.money = 100000;
        w.startNextWave();
        steps(w, 60 * 30);
        assertTrue(c.bullets > 20, "bullets=" + c.bullets);
        // Durchschlag: deutlich mehr Treffer als Geschosse (jeder Schuss trifft bis zu 3 dicht stehende Gegner)
        assertTrue(c.damaged > c.bullets, "damaged=" + c.damaged + " bullets=" + c.bullets);
        assertEquals(3, TowerType.SALVE_PIERCE);
        assertNotNull(t);
    }

    @Test
    void teslaChainsFurtherThanArc() {
        Counter c = new Counter();
        WaveDef wave = new WaveDef().add(EnemyType.TANK, 12, 0.3, 0, 100000).bonus(0);
        World w = worldWithWaves(c, wave);
        double[] spot = spotNear(w, 600, 70);
        w.money = 100000;
        w.placeTower(TowerType.TESLA, spot[0], spot[1]);
        w.startNextWave();
        steps(w, 60 * 40);
        assertTrue(c.lightnings > 3);
        assertTrue(c.maxChain > TowerType.ARC_CHAINS, "maxChain=" + c.maxChain);
    }

    @Test
    void railgunHitsEveryEnemyOnTheLine() {
        Counter c = new Counter();
        WaveDef wave = new WaveDef().add(EnemyType.TANK, 10, 0.25, 0, 100000).bonus(0);
        World w = worldWithWaves(c, wave);
        double[] spot = spotNear(w, 600, 70);
        w.money = 100000;
        w.placeTower(TowerType.RAILGUN, spot[0], spot[1]);
        w.startNextWave();
        steps(w, 60 * 40);
        assertTrue(c.beams > 4, "beams=" + c.beams);
        assertEquals(0, c.bullets);
        assertTrue(c.damaged > c.beams * 1.5, "Strahl trifft mehrere: damaged=" + c.damaged + " beams=" + c.beams);
    }

    @Test
    void splitEnemiesBreakIntoMinis() {
        Counter c = new Counter();
        WaveDef wave = new WaveDef().add(EnemyType.SPLIT, 1, 1.0, 0, 30).bonus(0);
        World w = worldWithWaves(c, wave);
        double[] spot = spotNear(w, 500, 70);
        w.placeTower(TowerType.PULSE, spot[0], spot[1]);
        w.placeTower(TowerType.PULSE, spot[0] + 60, spot[1]);
        w.startNextWave();
        double money = w.money;
        run(w, null, 90);
        assertEquals(1 + EnemyType.SPLIT.splitCount, c.spawned);
        assertEquals(1 + EnemyType.SPLIT.splitCount, c.killed);
        assertEquals(money + EnemyType.SPLIT.reward + EnemyType.SPLIT.splitCount * EnemyType.MINI.reward, w.money);
        assertEquals(World.State.WON, w.state);
    }

    @Test
    void clearingAllWavesWinsTheGame() {
        Counter c = new Counter();
        WaveDef wave = new WaveDef().add(EnemyType.BLOCK, 2, 1.0, 0, 3).bonus(25);
        World w = worldWithWaves(c, wave);
        double[] spot = spotNear(w, 500, 70);
        w.placeTower(TowerType.PULSE, spot[0], spot[1]);
        double money = w.money;
        w.startNextWave();
        assertFalse(w.canStartWave());
        run(w, null, 60);
        assertEquals(World.State.WON, w.state);
        assertEquals(Boolean.TRUE, c.ended);
        assertEquals(money + 2 * EnemyType.BLOCK.reward + 25, w.money);
    }

    @Test
    void simulationIsDeterministic() {
        long[] hashes = new long[2];
        for (int run = 0; run < 2; run++) {
            World w = new World(Levels.serpentine(), SimListener.NONE);
            AutoPlayer bot = new AutoPlayer(w, true, 12, null);
            for (int i = 0; i < 60 * 150; i++) {
                if (i % 20 == 0) {
                    bot.think();
                }
                w.step();
            }
            hashes[run] = SimTestSupport.stateHash(w);
        }
        assertEquals(hashes[0], hashes[1]);
    }

    @Test
    void autoStartLaunchesWavesByItself() {
        Counter c = new Counter();
        World w = new World(Levels.serpentine(), c);
        w.autoStart = true;
        steps(w, 60 * 3);
        assertEquals(1, w.waveIndex);
    }

    @Test
    void levelValidation() {
        LevelDef empty = new LevelDef("x", "x");
        assertNotNull(empty.validate());
        LevelDef shortPath = new LevelDef("x", "x");
        shortPath.paths.add(new double[] {100, 100, 200, 100});
        assertNotNull(shortPath.validate());
        LevelDef outside = new LevelDef("x", "x");
        outside.paths.add(new double[] {-50, 100, 2000, 100});
        assertNotNull(outside.validate());
        assertNull(Levels.serpentine().validate());
    }
}

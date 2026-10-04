package neontd.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neontd.level.LevelDef;
import neontd.level.Levels;
import org.junit.jupiter.api.Test;

/** Endlosmodus: Wellenkurve bis 1000, Meisterstufen, Geld- und Zahlenlimits, Fortsetzen nach dem Sieg. */
class EndlessTest {
    @Test
    void campaignEndsInVictoryButEndlessContinues() {
        LevelDef def = Levels.serpentine();
        def.startMoney = 1_000_000;
        SimTestSupport.Counter c = new SimTestSupport.Counter();
        World w = new World(def, c);
        // Ein dichter Turmring am Pfad, damit alle 20 Wellen sicher fallen.
        AutoPlayer bot = new AutoPlayer(w, true, 30, null);
        double t = SimTestSupport.run(w, bot, 60 * 40);
        assertEquals(World.State.WON, w.state, "Sieg nach 20 Wellen (Zeit " + t + ")");
        assertEquals(Boolean.TRUE, c.ended);
        assertFalse(w.canStartWave());

        w.enableEndless();
        assertEquals(World.State.RUNNING, w.state);
        assertTrue(w.canStartWave());
        assertNotNull(w.nextWave());
        assertTrue(w.startNextWave());
        assertEquals(21, w.waveIndex);
        SimTestSupport.steps(w, 60 * 60);
        assertEquals(World.State.RUNNING, w.state, "im Endlosmodus gibt es keinen Sieg");
    }

    @Test
    void endlessWavesAreGeneratedLazilyAndStayWithinLimits() {
        World w = new World(Levels.serpentine(), SimListener.NONE);
        w.enableEndless();
        int prevHp = 0;
        for (int i = 0; i < 1200; i++) {
            WaveDef d = w.waveAt(i);
            assertTrue(d.totalEnemies() <= 240, "Welle " + (i + 1) + " hat " + d.totalEnemies() + " Gegner");
            assertTrue(d.bonus > 0 && d.bonus < World.MAX_MONEY, "Bonus Welle " + (i + 1));
            int blockHp = WaveFactory.hp(EnemyType.BLOCK, i + 1);
            assertTrue(blockHp >= prevHp, "HP wachsen nie rückwärts, Welle " + (i + 1));
            prevHp = blockHp;
            for (WaveDef.Group g : d.groups) {
                assertTrue(g.hp > 0 && g.hp <= WaveFactory.MAX_HP, "HP Welle " + (i + 1));
                assertTrue(g.reward >= 1);
                assertTrue(g.count > 0 && g.interval > 0);
            }
        }
        // Welle 1000: noch deutlich unter dem int-Limit, auch für den Titan.
        int boss = WaveFactory.hp(EnemyType.BOSS, 1000);
        assertTrue(boss > 100_000_000 && boss < WaveFactory.MAX_HP, "Titan HP in Welle 1000: " + boss);
    }

    @Test
    void waveCurveIsUnchangedUpToTwenty() {
        // Regressionsschutz für die handgebaute Kampagne: Welle 1, 10 und 20.
        assertEquals(10, WaveFactory.baseHp(1));
        assertEquals(1.0, WaveFactory.rewardScale(20), 0.0);
        WaveDef w10 = WaveFactory.wave(10);
        assertEquals(EnemyType.BOSS, w10.groups.get(w10.groups.size() - 1).type);
        assertEquals(EnemyType.BLOCK.reward, w10.groups.get(0).reward);
        assertEquals(WaveFactory.bonus(20), 150);
    }

    @Test
    void rewardsGrowSlowerThanHitPoints() {
        double prev = 1;
        for (int wave = 21; wave <= 1000; wave += 7) {
            double rs = WaveFactory.rewardScale(wave);
            assertTrue(rs >= prev, "Prämienfaktor steigt");
            prev = rs;
            double hpFactor = (double) WaveFactory.baseHp(wave) / WaveFactory.baseHp(20);
            assertTrue(rs < hpFactor, "Prämie wächst langsamer als HP bei Welle " + wave);
        }
    }

    @Test
    void masterLevelsAreMonotonicAndPricesGrowExponentially() {
        for (UpgradeTrack track : UpgradeTrack.values()) {
            double prevMult = 0;
            int prevCost = 0;
            for (int lvl = 0; lvl <= track.endlessMax; lvl++) {
                double m = track.multAt(lvl);
                assertTrue(m > prevMult, track + " Stufe " + lvl);
                prevMult = m;
                if (lvl < track.endlessMax) {
                    int cost = track.cost(TowerType.PULSE, lvl);
                    assertTrue(cost >= prevCost, track + " Preis Stufe " + lvl);
                    prevCost = cost;
                }
            }
            assertEquals(track.mult[UpgradeTrack.MAX_LEVEL], track.multAt(UpgradeTrack.MAX_LEVEL), 0.0);
            assertTrue(track.cost(TowerType.SNIPER, track.endlessMax - 1) <= 1_500_000_000);
        }
        // Meisterstufen: jede ist ungefähr 1,36-mal so teuer wie die vorige.
        int a = UpgradeTrack.DAMAGE.cost(TowerType.PULSE, 20);
        int b = UpgradeTrack.DAMAGE.cost(TowerType.PULSE, 21);
        assertEquals(1.36, (double) b / a, 0.02);
    }

    @Test
    void masterLevelsOnlyAvailableInEndlessMode() {
        LevelDef def = Levels.serpentine();
        def.startMoney = 2_000_000_000;
        World w = new World(def, SimListener.NONE);
        double[] spot = {400, 80};
        Tower t = null;
        for (double x = 300; x < 700 && t == null; x += 10) {
            t = w.placeTower(TowerType.PULSE, x, 220);
        }
        assertNotNull(t);
        for (int i = 0; i < UpgradeTrack.MAX_LEVEL; i++) {
            assertTrue(w.upgrade(t, UpgradeTrack.DAMAGE));
        }
        assertEquals(-1, w.upgradeCost(t, UpgradeTrack.DAMAGE));
        assertFalse(w.upgrade(t, UpgradeTrack.DAMAGE));
        w.enableEndless();
        assertTrue(w.upgradeCost(t, UpgradeTrack.DAMAGE) > 0);
        int before = t.damage;
        assertTrue(w.upgrade(t, UpgradeTrack.DAMAGE));
        assertTrue(t.damage > before * 1.2);
        assertEquals(6, t.level(UpgradeTrack.DAMAGE));
    }

    @Test
    void moneyAndDamageNeverOverflow() {
        LevelDef def = Levels.serpentine();
        def.startMoney = 2_000_000_000;
        World w = new World(def, SimListener.NONE);
        w.addMoney(1_500_000_000);
        assertEquals(3_500_000_000.0, w.money, "Geld darf über den int-Bereich hinaus wachsen");
        w.addMoney(Integer.MAX_VALUE);
        assertEquals(5_647_483_647.0, w.money);
        w.addMoney(9_000_000_000_000_000.0);
        assertEquals(World.MAX_MONEY, w.money);
        assertEquals(9.0e15, World.MAX_MONEY);
        assertTrue(World.MAX_MONEY + 1 > World.MAX_MONEY, "ganze Zahlen bleiben bis zur Grenze exakt");
        w.addMoney(Integer.MAX_VALUE);
        assertEquals(World.MAX_MONEY, w.money);
        // Speichern und Laden (Json) behält den vollen Betrag
        neontd.sim.WorldSnapshot snap = w.snapshot();
        assertEquals(World.MAX_MONEY, snap.money);
        neontd.save.RunSave back = neontd.save.RunSave.decode(def.id, neontd.save.RunSave.of(def.id, 1, snap).encode());
        assertEquals(World.MAX_MONEY, back.snapshot.money);
        assertEquals("9000000000000000", neontd.ui.Fmt.whole(World.MAX_MONEY));
        assertEquals("9.0Qa", neontd.ui.Fmt.compact(World.MAX_MONEY));
        assertEquals("1.2B", neontd.ui.Fmt.compact(1_234_567_890.0));
        assertEquals("45T", neontd.ui.Fmt.compact(45.6e12));
        assertEquals("123B", neontd.ui.Fmt.compact(123.9e9));

        w.enableEndless();
        Tower t = null;
        for (double x = 300; x < 700 && t == null; x += 10) {
            t = w.placeTower(TowerType.SNIPER, x, 220);
        }
        assertNotNull(t);
        for (int i = 0; i < 400; i++) {
            for (UpgradeTrack track : UpgradeTrack.values()) {
                w.money = World.MAX_MONEY;
                w.upgrade(t, track);
            }
        }
        for (UpgradeTrack track : UpgradeTrack.values()) {
            assertEquals(track.endlessMax, t.level(track), "voll ausgebaut: " + track);
        }
        assertTrue(t.damage > 0 && t.damage <= Tower.MAX_DAMAGE, "Schaden " + t.damage);
        assertTrue(t.invested > 0, "Investition läuft nicht über: " + t.invested);
        assertTrue(w.sellValue(t) > 0);
        assertTrue(t.interval > 0.01 && t.range < 1500);
    }

    @Test
    void earlyStartLetsEndlessWavesOverlapWhenOnlyStragglersRemain() {
        World w = new World(Levels.serpentine(), SimListener.NONE);
        w.enableEndless();
        assertTrue(w.readyForNextWave());
        w.startNextWave();
        assertFalse(w.readyForNextWave(), "Welle läuft noch");
        // Ohne Türme erreichen die Gegner das Ziel; vorher ist die Welle nie "fast fertig".
        SimTestSupport.steps(w, 60 * 3);
        assertFalse(w.readyForNextWave());
    }

    @Test
    void botSurvivesDeepIntoEndlessMode() {
        World w = new World(Levels.serpentine(), SimListener.NONE);
        w.enableEndless();
        AutoPlayer bot = new AutoPlayer(w, true, 40, null);
        int steps = 0;
        while (w.state == World.State.RUNNING && w.waveIndex <= 150 && steps < 60 * 3600 * 3) {
            if (steps % 20 == 0) {
                bot.think();
            }
            w.step();
            steps++;
        }
        assertEquals(World.State.RUNNING, w.state, "verloren bei Welle " + w.waveIndex);
        assertTrue(w.waveIndex > 150);
        assertTrue(w.lives > 0);
        assertTrue(w.towers.size() >= 30);
    }
}

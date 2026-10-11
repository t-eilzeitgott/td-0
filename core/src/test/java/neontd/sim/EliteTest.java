package neontd.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neontd.level.LevelDef;
import neontd.level.Levels;
import neontd.sim.SimTestSupport.Counter;
import org.junit.jupiter.api.Test;

/** Elite-Gegner, die acht Elite-Türme und die Spezial-Upgrades. */
class EliteTest {
    private static double[] spotNear(World w, double s, double side) {
        neontd.math.Vec2 v = new neontd.math.Vec2();
        w.paths[0].positionAt(s, v);
        double h = w.paths[0].headingAt(s);
        return new double[] {v.x - Math.sin(h) * side, v.y + Math.cos(h) * side};
    }

    /** Eine Welt mit einer Welle aus genau einem Gegner; er wird gleich gestartet und steht nach einem Schritt. */
    private static World withEnemy(EnemyType type, int hp) {
        LevelDef def = Levels.serpentine();
        def.startMoney = 1_000_000;
        WaveDef wave = new WaveDef().add(type, 1, 1.0, 0, hp).bonus(0);
        World w = new World(def, java.util.Arrays.asList(wave, new WaveDef().add(EnemyType.BLOCK, 1, 1, 0, 5)),
                SimListener.NONE);
        w.startNextWave();
        w.step();
        assertEquals(1, w.enemies.size());
        return w;
    }

    private static Tower tower(World w, TowerType type) {
        double[] spot = spotNear(w, 300, 80);
        Tower t = w.placeTower(type, spot[0], spot[1]);
        assertNotNull(t, type.name());
        return t;
    }

    // ------------------------------------------------------------------------------------------ Gegner

    @Test
    void shieldAbsorbsDamageFirstAndRechargesWhenLeftAlone() {
        World w = withEnemy(EnemyType.SHIELD, 100);
        Enemy e = w.enemies.get(0);
        assertEquals(40, e.shieldMax);
        assertEquals(40, e.shield);
        w.damage(e, 30, null);
        assertEquals(10, e.shield);
        assertEquals(100, e.hp);
        w.damage(e, 30, null);
        assertEquals(0, e.shield);
        assertEquals(80, e.hp);
        SimTestSupport.steps(w, 60 * 4);
        assertTrue(e.shield > 5, "Schild lädt sich nach der Pause auf: " + e.shield);
        assertEquals(80, e.hp, "Leben kommen nicht zurück");
    }

    @Test
    void armorHalvesOnlyWeakHits() {
        World w = withEnemy(EnemyType.ARMORED, 1000);
        Enemy e = w.enemies.get(0);
        w.damage(e, 10, null); // unter 3 % von 1000
        assertEquals(1000 - 7, e.hp);
        w.damage(e, 100, null);
        assertEquals(1000 - 7 - 100, e.hp);
    }

    @Test
    void healerHealsNeighboursButNotItself() {
        LevelDef def = Levels.serpentine();
        WaveDef wave = new WaveDef().add(EnemyType.HEALER, 1, 1.0, 0, 1000).add(EnemyType.BLOCK, 1, 1.0, 0, 1000)
                .bonus(0);
        World w = new World(def, java.util.Arrays.asList(wave), SimListener.NONE);
        w.startNextWave();
        SimTestSupport.steps(w, 3);
        assertEquals(2, w.enemies.size());
        Enemy healer = w.enemies.get(0).type == EnemyType.HEALER ? w.enemies.get(0) : w.enemies.get(1);
        Enemy block = healer == w.enemies.get(0) ? w.enemies.get(1) : w.enemies.get(0);
        block.hp = 500;
        healer.hp = 500;
        SimTestSupport.steps(w, 60 * 2);
        assertTrue(block.hp > 500, "Nachbar geheilt: " + block.hp);
        assertEquals(500, healer.hp, "sich selbst heilt er nicht");
    }

    @Test
    void ghostCannotBeHitDuringItsPhase() {
        World w = withEnemy(EnemyType.GHOST, 1000);
        Enemy e = w.enemies.get(0);
        e.age = 0.5;
        assertFalse(e.phased());
        e.age = 3.5; // letzte 1,2 s des ersten Zyklus (3,0 bis 4,2 s)
        assertTrue(e.phased());
        assertFalse(e.targetable());
        w.damage(e, 100, null);
        assertEquals(1000, e.hp, "in der Phase unverwundbar");
        e.age = 2.0;
        assertTrue(e.targetable());
        e.age = EnemyType.PHASE_CYCLE + 3.5;
        assertTrue(e.phased(), "und der nächste Zyklus beginnt");
    }

    @Test
    void wavesBringEliteEnemiesAtTheirWaveNumbers() {
        assertTrue(hasType(WaveFactory.wave(7), EnemyType.SHIELD));
        assertTrue(hasType(WaveFactory.wave(15), EnemyType.HEALER));
        assertFalse(hasType(WaveFactory.wave(20), EnemyType.HEALER), "keine Heiler in Titan-Wellen");
        assertTrue(hasType(WaveFactory.wave(12), EnemyType.ARMORED));
        assertTrue(hasType(WaveFactory.wave(15), EnemyType.GHOST));
        assertFalse(hasType(WaveFactory.wave(6), EnemyType.ARMORED));
        // Die Gesamtlast bleibt im Rahmen: Elite-Gegner ersetzen Blöcke.
        for (int wave = 1; wave <= 400; wave++) {
            assertTrue(WaveFactory.wave(wave).totalEnemies() <= 240, "Welle " + wave);
        }
    }

    private static boolean hasType(WaveDef w, EnemyType t) {
        for (WaveDef.Group g : w.groups) {
            if (g.type == t) {
                return true;
            }
        }
        return false;
    }

    // ----------------------------------------------------------------------------------------- Türme

    @Test
    void eliteTowersAreTierTwoAndCostCrystals() {
        int n = 0;
        for (TowerType t : TowerType.values()) {
            if (t.tier == 2) {
                n++;
                assertTrue(t.crystals > 0 && t.unlockLevel >= 12, t.name());
            } else {
                assertEquals(0, t.crystals, t.name());
            }
        }
        assertEquals(8, n);
        assertEquals(16, TowerType.values().length);
        assertTrue(TowerType.MINT.passive && TowerType.BEACON.passive);
        assertFalse(TowerType.PULSE.passive);
    }

    @Test
    void infernoSetsEnemiesOnFireAndTheyBurnDown() {
        World w = withEnemy(EnemyType.TANK, 100000);
        Tower t = tower(w, TowerType.INFERNO);
        Enemy e = w.enemies.get(0);
        SimTestSupport.steps(w, 60 * 12);
        assertTrue(e.hp < 100000, "Brandschaden: " + e.hp);
        assertTrue(t.hasTarget || e.hp < 100000);
    }

    @Test
    void venomStacksGrowAndPoisonTicks() {
        World w = withEnemy(EnemyType.TANK, 1_000_000);
        tower(w, TowerType.VENOM);
        Enemy e = w.enemies.get(0);
        SimTestSupport.steps(w, 60 * 30);
        assertTrue(e.poisonStacks >= 2 || e.hp < 1_000_000 - 300, "stacks=" + e.poisonStacks + " hp=" + e.hp);
        assertTrue(e.poisonStacks <= TowerType.VENOM_MAX_STACKS);
        assertTrue(e.hp < 1_000_000 - 100);
    }

    @Test
    void shredderFiresAFanOfPellets() {
        Counter c = new Counter();
        LevelDef def = Levels.serpentine();
        def.startMoney = 100000;
        WaveDef wave = new WaveDef().add(EnemyType.TANK, 3, 0.8, 0, 100000).bonus(0);
        World w = new World(def, java.util.Arrays.asList(wave), c);
        double[] spot = spotNear(w, 300, 60);
        assertNotNull(w.placeTower(TowerType.SHREDDER, spot[0], spot[1]));
        w.startNextWave();
        SimTestSupport.steps(w, 60 * 25);
        assertTrue(c.bullets >= TowerType.SHREDDER_PELLETS, "bullets=" + c.bullets);
        assertEquals(0, c.bullets % TowerType.SHREDDER_PELLETS, "immer ganze Salven");
        assertTrue(c.damaged > 0);
    }

    @Test
    void gravitonPullsEnemiesBackAndSlowsThem() {
        World w = withEnemy(EnemyType.BLOCK, 1_000_000);
        // Platz in der Nähe der Startstrecke: Der Gegner läuft in die Reichweite.
        tower(w, TowerType.GRAVITON);
        Enemy e = w.enemies.get(0);
        double maxDist = 0;
        boolean pulled = false;
        for (int i = 0; i < 60 * 20; i++) {
            double before = e.dist;
            w.step();
            maxDist = Math.max(maxDist, e.dist);
            if (e.dist < before - 5) {
                pulled = true;
            }
        }
        assertTrue(pulled, "mindestens einmal zurückgezogen");
        assertTrue(e.slowTimer > 0 || e.dist < maxDist);
    }

    @Test
    void mintEarnsMoneyAndScalesWithUpgrades() {
        LevelDef def = Levels.serpentine();
        def.startMoney = 100000;
        World w = new World(def, SimListener.NONE);
        double[] spot = spotNear(w, 300, 80);
        Tower t = w.placeTower(TowerType.MINT, spot[0], spot[1]);
        double before = w.money;
        SimTestSupport.steps(w, 60 * 26);
        double earned = w.money - before;
        assertTrue(earned >= 4 * TowerType.MINT_INCOME, "verdient: " + earned);
        // stärker mit dem Schaden-Upgrade (Ertrag)
        for (int i = 0; i < 3; i++) {
            w.upgrade(t, UpgradeTrack.DAMAGE);
        }
        double b2 = w.money;
        SimTestSupport.steps(w, 60 * 26);
        assertTrue(w.money - b2 > earned * 1.5, "mit Upgrades mehr: " + (w.money - b2) + " statt " + earned);
    }

    @Test
    void beaconBuffsTowersInRangeButNotThoseFarAway() {
        LevelDef def = Levels.serpentine();
        def.startMoney = 100000;
        World w = new World(def, SimListener.NONE);
        double[] a = spotNear(w, 300, 80);
        Tower beacon = w.placeTower(TowerType.BEACON, a[0], a[1]);
        Tower near = null;
        for (double dx = 70; dx < 140 && near == null; dx += 10) {
            near = w.placeTower(TowerType.PULSE, a[0] + dx, a[1]);
        }
        assertNotNull(near);
        Tower far = null;
        double[] f = spotNear(w, 1500, 80);
        far = w.placeTower(TowerType.PULSE, f[0], f[1]);
        assertNotNull(far);
        w.step();
        assertTrue(near.buffDamage >= TowerType.BEACON_DAMAGE - 1e-9, "buff=" + near.buffDamage);
        assertEquals(0, far.buffDamage, 1e-9);
        assertTrue(near.power() > near.damage || near.damage <= 5);
        assertTrue(near.effectiveInterval() < near.interval);
        assertNotNull(beacon);
        // Das Upgrade verstärkt das Feld
        double before = near.buffDamage;
        w.upgrade(beacon, UpgradeTrack.DAMAGE);
        w.step();
        assertTrue(near.buffDamage > before);
    }

    @Test
    void prismaBuildsUpPowerOnOneTargetAndResetsOnNewTargets() {
        World w = withEnemy(EnemyType.TANK, 100_000_000);
        Tower t = tower(w, TowerType.PRISMA);
        SimTestSupport.steps(w, 60 * 8);
        assertTrue(t.ramp > 1.5, "ramp=" + t.ramp);
        assertTrue(t.ramp <= TowerType.PRISMA_MAX + 1e-9);
        // Ziel weg → zurück auf 1
        w.enemies.get(0).hp = 0;
        SimTestSupport.steps(w, 5);
        assertEquals(1.0, t.ramp, 1e-9);
    }

    @Test
    void orbitalStrikesTheStrongestEnemyAnywhere() {
        Counter c = new Counter();
        LevelDef def = Levels.serpentine();
        def.startMoney = 100000;
        WaveDef wave = new WaveDef().add(EnemyType.BLOCK, 4, 1.0, 0, 200).add(EnemyType.TANK, 1, 1.0, 1.0, 2000)
                .bonus(0);
        World w = new World(def, java.util.Arrays.asList(wave), c);
        double[] spot = spotNear(w, 300, 80);
        Tower t = w.placeTower(TowerType.ORBITAL, spot[0], spot[1]);
        assertEquals(TargetMode.STRONG, t.mode);
        assertTrue(t.range > 600);
        w.startNextWave();
        SimTestSupport.steps(w, 60 * 40);
        assertTrue(c.shells >= 2, "shells=" + c.shells);
        assertTrue(c.explosions >= 2, "explosions=" + c.explosions);
    }

    // ----------------------------------------------------------------------------------------- Spezials

    @Test
    void specialCostsMoneyAndRespectsAllowedTowers() {
        LevelDef def = Levels.serpentine();
        def.startMoney = 100000;
        World w = new World(def, SimListener.NONE);
        double[] spot = spotNear(w, 300, 80);
        Tower t = w.placeTower(TowerType.PULSE, spot[0], spot[1]);
        double money = w.money;
        double invested = t.invested;
        assertEquals(Special.buildCost(TowerType.PULSE), w.specialCost(t));
        assertTrue(w.setSpecial(t, Special.CRIT));
        assertEquals(money - w.specialCost(t), w.money);
        assertEquals(invested + w.specialCost(t), t.invested);
        assertEquals(Special.CRIT, t.special);
        assertFalse(w.setSpecial(t, Special.CRIT), "gleiches Spezial doppelt kaufen geht nicht");
        assertTrue(w.setSpecial(t, Special.BURN), "tauschen geht");
        // Münze und Kommando: nur Zinsen
        double[] s2 = spotNear(w, 1500, 80);
        Tower mint = w.placeTower(TowerType.MINT, s2[0], s2[1]);
        assertFalse(w.setSpecial(mint, Special.CRIT));
        assertTrue(w.setSpecial(mint, Special.INTEREST));
        // Durchschlag nur für Geschosse und Blitze
        assertFalse(Special.PIERCE.allowedFor(TowerType.SNIPER));
        assertTrue(Special.PIERCE.allowedFor(TowerType.TESLA));
        // zu wenig Geld
        w.money = 1;
        assertFalse(w.setSpecial(t, Special.CHILL));
    }

    @Test
    void critHitsAboutOneInFive() {
        World w = withEnemy(EnemyType.BOSS, 2_000_000_000);
        Tower t = tower(w, TowerType.PULSE);
        t.special = Special.CRIT;
        Enemy e = w.enemies.get(0);
        int crits = 0;
        int n = 2000;
        for (int i = 0; i < n; i++) {
            int before = e.hp;
            w.damage(e, 1000, t);
            if (before - e.hp > 1000) {
                crits++;
            }
            e.hp = 2_000_000_000;
        }
        assertTrue(crits > n * 0.12 && crits < n * 0.26, "crits=" + crits);
    }

    @Test
    void executeBossbaneAndVulnerabilityMultiplyDamage() {
        World w = withEnemy(EnemyType.BOSS, 10000);
        Tower t = tower(w, TowerType.PULSE);
        Enemy e = w.enemies.get(0);
        t.special = Special.EXECUTE;
        w.damage(e, 100, t);
        assertEquals(9900, e.hp);
        e.hp = 2000; // unter 30 %
        w.damage(e, 100, t);
        assertEquals(1800, e.hp, "doppelter Schaden");
        t.special = Special.BOSSBANE;
        e.hp = 10000;
        w.damage(e, 100, t);
        assertEquals(10000 - 175, e.hp);
        t.special = Special.VULN;
        e.hp = 10000;
        w.damage(e, 100, t);
        assertTrue(e.vulnTimer > 0);
        t.special = null;
        e.hp = 10000;
        w.damage(e, 100, t);
        assertEquals(10000 - 125, e.hp, "Rüstungsbruch: 25 % mehr");
    }

    @Test
    void burnChillStunAndKnockbackApplyOnHit() {
        World w = withEnemy(EnemyType.TANK, 1_000_000);
        Tower t = tower(w, TowerType.PULSE);
        Enemy e = w.enemies.get(0);
        t.special = Special.BURN;
        w.damage(e, 100, t);
        assertTrue(e.burning());
        int hp = e.hp;
        SimTestSupport.steps(w, 90);
        assertTrue(e.hp < hp, "brennt");
        t.special = Special.CHILL;
        w.damage(e, 100, t);
        assertTrue(e.slowFactor < 1 && e.slowTimer > 0);
        t.special = Special.STUN;
        boolean stunned = false;
        for (int i = 0; i < 100 && !stunned; i++) {
            w.damage(e, 1, t);
            stunned = e.stunTimer > 0;
        }
        assertTrue(stunned);
        assertEquals(0, e.speed(), 1e-9);
        t.special = Special.KNOCKBACK;
        e.dist = 500;
        w.damage(e, 1, t);
        assertEquals(500 - Special.KNOCKBACK_DIST, e.dist, 1e-9);
    }

    @Test
    void splashAndAftershockHitNeighbours() {
        LevelDef def = Levels.serpentine();
        def.startMoney = 100000;
        WaveDef wave = new WaveDef().add(EnemyType.TANK, 2, 0.01, 0, 5000).bonus(0);
        World w = new World(def, java.util.Arrays.asList(wave), SimListener.NONE);
        double[] spot = spotNear(w, 300, 80);
        Tower t = w.placeTower(TowerType.PULSE, spot[0], spot[1]);
        t.cooldown = 1e9; // nur manuelle Treffer
        w.startNextWave();
        SimTestSupport.steps(w, 3);
        assertEquals(2, w.enemies.size());
        Enemy a = w.enemies.get(0);
        Enemy b = w.enemies.get(1);
        a.dist = b.dist + 10;
        t.special = Special.SPLASH;
        int hb = b.hp;
        w.damage(a, 1000, t);
        assertTrue(b.hp < hb, "Nachbar bekommt Streuschaden");
        t.special = Special.AFTERSHOCK;
        hb = b.hp;
        w.damage(a, 100000, t); // tötet
        w.step();
        assertTrue(b.hp < hb, "Nachbeben nach dem Abschuss");
    }

    @Test
    void bountyInterestAndAuraChangeTheEconomyAndBuffs() {
        LevelDef def = Levels.serpentine();
        def.startMoney = 100000;
        WaveDef wave = new WaveDef().add(EnemyType.BLOCK, 1, 1.0, 0, 10).bonus(0);
        World w = new World(def, java.util.Arrays.asList(wave, wave), SimListener.NONE);
        double[] spot = spotNear(w, 300, 80);
        Tower t = w.placeTower(TowerType.PULSE, spot[0], spot[1]);
        t.cooldown = 1e9;
        w.startNextWave();
        SimTestSupport.steps(w, 2);
        Enemy e = w.enemies.get(0);
        t.special = Special.BOUNTY;
        double money = w.money;
        w.damage(e, 1000, t);
        w.step();
        assertEquals(money + Math.round(EnemyType.BLOCK.reward * 1.5), w.money, 1.01);
        // Zinsen: die Welle ist besiegt → 4 % des investierten Geldes
        t.special = Special.INTEREST;
        double m2 = w.money;
        SimTestSupport.steps(w, 5);
        assertEquals(1, w.clearedWaves());
        assertTrue(w.money >= m2, "Zinsen oder zumindest kein Verlust");
        // Aura
        Tower b = null;
        for (double dx = 70; dx < 140 && b == null; dx += 10) {
            b = w.placeTower(TowerType.PULSE, spot[0] + dx, spot[1]);
        }
        assertNotNull(b);
        t.special = Special.AURA;
        w.step();
        assertEquals(Special.AURA_BONUS, b.buffDamage, 1e-9);
    }

    @Test
    void interestPaysAfterEachClearedWave() {
        LevelDef def = Levels.serpentine();
        def.startMoney = 100000;
        WaveDef wave = new WaveDef().add(EnemyType.BLOCK, 1, 1.0, 0, 1).bonus(0);
        World w = new World(def, java.util.Arrays.asList(wave, wave), SimListener.NONE);
        double[] spot = spotNear(w, 300, 80);
        Tower t = w.placeTower(TowerType.PULSE, spot[0], spot[1]);
        assertTrue(w.setSpecial(t, Special.INTEREST));
        double invested = t.invested;
        w.startNextWave();
        double before = w.money;
        SimTestSupport.run(w, null, 40);
        assertEquals(1, w.clearedWaves());
        double gain = w.money - before;
        assertTrue(gain >= Math.floor(invested * Special.INTEREST_RATE), "gain=" + gain);
    }

    @Test
    void overclockLongshotAndPierceChangeTheNumbers() {
        LevelDef def = Levels.serpentine();
        def.startMoney = 100000;
        World w = new World(def, SimListener.NONE);
        double[] spot = spotNear(w, 300, 80);
        Tower t = w.placeTower(TowerType.PULSE, spot[0], spot[1]);
        double range = t.range;
        double interval = t.interval;
        t.special = Special.OVERCLOCK;
        t.recompute();
        assertEquals(interval / Special.OVERCLOCK_SPEED, t.interval, 1e-9);
        t.special = Special.LONGSHOT;
        t.recompute();
        assertEquals(range * Special.LONGSHOT_RANGE, t.range, 1e-9);
    }

    @Test
    void specialsSurviveSnapshotAndRestore() {
        LevelDef def = Levels.serpentine();
        def.startMoney = 100000;
        World w = new World(def, SimListener.NONE);
        double[] spot = spotNear(w, 300, 80);
        Tower t = w.placeTower(TowerType.TESLA, spot[0], spot[1]);
        assertTrue(w.setSpecial(t, Special.PIERCE));
        double[] s2 = spotNear(w, 1500, 80);
        assertNotNull(w.placeTower(TowerType.ORBITAL, s2[0], s2[1]));
        WorldSnapshot snap = w.snapshot();
        assertNotNull(snap);
        World r = new World(def, SimListener.NONE);
        r.restore(snap);
        assertEquals(2, r.towers.size());
        assertEquals(Special.PIERCE, r.towers.get(0).special);
        assertNull(r.towers.get(1).special);
        // über JSON
        neontd.save.RunSave back = neontd.save.RunSave.decode(def.id, neontd.save.RunSave.of(def.id, 1, snap).encode());
        assertEquals(snap.towers.get(0).special, back.snapshot.towers.get(0).special);
        World r2 = new World(def, SimListener.NONE);
        r2.restore(back.snapshot);
        assertEquals(Special.PIERCE, r2.towers.get(0).special);
    }

    @Test
    void shieldSurvivesSnapshotAndRestore() {
        LevelDef def = Levels.serpentine();
        WaveDef wave = new WaveDef().add(EnemyType.SHIELD, 1, 1.0, 0, 1000).bonus(0);
        WaveDef next = new WaveDef().add(EnemyType.BLOCK, 1, 1.0, 0, 5).bonus(0);
        World w = new World(def, java.util.Arrays.asList(wave, next), SimListener.NONE);
        w.startNextWave();
        SimTestSupport.steps(w, 5);
        w.enemies.get(0).shield = 123;
        WorldSnapshot snap = w.snapshot();
        assertNotNull(snap);
        World r = new World(def, java.util.Arrays.asList(wave, next), SimListener.NONE);
        r.restore(snap);
        assertEquals(1, r.enemies.size());
        assertEquals(123, r.enemies.get(0).shield);
        assertEquals(400, r.enemies.get(0).shieldMax);
    }

    @Test
    void perksChangeCostsDamageSellValueAndRewards() {
        LevelDef def = Levels.serpentine();
        def.startMoney = 100000;
        World w = new World(def, SimListener.NONE);
        Perks p = new Perks();
        p.buildCost = 0.9;
        p.damage = 1.5;
        p.range = 1.1;
        p.sell = 0.1;
        w.setPerks(p);
        double[] spot = spotNear(w, 300, 80);
        assertEquals(90, w.buildCost(TowerType.PULSE));
        double money = w.money;
        Tower t = w.placeTower(TowerType.PULSE, spot[0], spot[1]);
        assertEquals(money - 90, w.money);
        assertEquals(Math.round(TowerType.PULSE.damage * 1.5), t.damage);
        assertEquals(TowerType.PULSE.range * 1.1, t.range, 1e-9);
        assertEquals(Math.floor(90 * 0.8), w.sellValue(t));
        // nachträglich gesetzt wirkt auch auf stehende Türme
        w.setPerks(Perks.NONE);
        assertEquals(TowerType.PULSE.damage, t.damage);
    }
}

package neontd.sim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import neontd.level.LevelDef;
import neontd.math.Mathx;
import neontd.math.Rng;
import neontd.math.Vec2;
import neontd.physics.Collision;
import neontd.physics.Path;
import neontd.physics.SpatialHash;

/**
 * Die komplette Spielsimulation: Pfade, Gegner, Türme, Geschosse, Wellen und Wirtschaft.
 *
 * <p>Die Welt kennt weder Bildschirm noch Eingabegeräte. Sie wird mit {@link #step()} in festen Schritten von
 * {@link #STEP} Sekunden vorangetrieben und ist dadurch deterministisch und ohne Fenster testbar. Veränderungen
 * von außen laufen ausschließlich über die öffentlichen Befehle ({@link #placeTower}, {@link #upgrade},
 * {@link #sell}, {@link #startNextWave}); Ereignisse für die Darstellung gehen über den {@link SimListener}.
 */
public final class World {
    /** Länge eines Simulationsschritts in Sekunden (60 Hz). */
    public static final double STEP = 1.0 / 60.0;
    /** Halbe Breite der Gegnerspur – Türme dürfen nicht darauf stehen. */
    public static final double LANE_HALF_WIDTH = 24;
    public static final double TOWER_RADIUS = 28;
    private static final double TOWER_GAP = 2;
    /** Größter Gegnerradius; nötig, um Broad-Phase-Suchen konservativ zu erweitern. */
    public static final double MAX_ENEMY_RADIUS = 36;
    public static final double SELL_RATIO = 0.7;
    private static final double BULLET_RADIUS = 4;
    private static final double MUZZLE = TOWER_RADIUS * 0.7;
    private static final double TURN_RATE = 16;
    private static final double AUTO_START_DELAY = 1.2;
    private static final int QUERY_CAP = 2048;
    /** Obergrenze für das Geld: 9·10¹⁵ – Geld ist ein double mit ganzen Zahlen, das bis 2⁵³ ≈ 9,007·10¹⁵ exakt rechnet. */
    public static final double MAX_MONEY = 9_000_000_000_000_000.0;
    /** Endlos: Die nächste Welle darf automatisch früher kommen, wenn von der letzten nur noch so viele übrig sind. */
    public static final int EARLY_START_LEFT = 6;
    /** Im Endlosmodus gibt jede so-vielte besiegte Welle ein verlorenes Leben zurück. */
    public static final int LIFE_EVERY = 10;

    public enum State { RUNNING, WON, LOST }

    /** Ergebnis einer Platzierungsprüfung. */
    public enum PlaceCheck { OK, OUT_OF_BOUNDS, ON_PATH, OVERLAP, BLOCKED }

    public final LevelDef level;
    public final Path[] paths;
    /** Die bisher bekannten Wellen; im Endlosmodus wächst die Liste bei Bedarf (siehe {@link #waveAt}). */
    private final ArrayList<WaveDef> waves;
    private final int baseWaveCount;
    public final double width;
    public final double height;

    public final ArrayList<Enemy> enemies = new ArrayList<>();
    public final ArrayList<Tower> towers = new ArrayList<>();
    public final ArrayList<Projectile> projectiles = new ArrayList<>();

    public double money;
    public int lives;
    public int kills;
    public double time;
    public State state = State.RUNNING;
    /** Anzahl bereits gestarteter Wellen (= Index der nächsten Welle). */
    public int waveIndex;
    public boolean autoStart;
    /** Dauerhafte Boni aus dem Shop; vor dem ersten Turm setzen (siehe {@link #setPerks}). */
    public Perks perks = Perks.NONE;
    /** Endlosmodus: Nach der letzten festen Welle kommen immer weitere, es gibt keinen Sieg mehr. */
    public boolean endless;

    private final SimListener listener;
    private final SpatialHash hash;
    private final int[] query = new int[QUERY_CAP];
    private final Vec2 tmp = new Vec2();
    /** Zufall für Spezial-Upgrades (Volltreffer, Betäubung …): fester Seed, überall dieselbe Folge. */
    private final Rng rng = new Rng(0x5EED1234);
    private int[] aliveByWave;
    private int[] spawnsLeft;
    private boolean[] cleared;
    /** Kleinster Index einer Welle, die noch nicht besiegt ist (alles davor ist erledigt). */
    private int firstOpen;
    private int clearedCount;
    private final ArrayList<Emitter> emitters = new ArrayList<>();
    private final double[] chainPts = new double[2 * (TowerType.MAX_CHAINS + 2)];
    private final int[] chainIds = new int[TowerType.MAX_CHAINS + 1];
    private int nextId = 1;
    private int activeWaves;
    private double autoTimer;

    private static final class Emitter {
        final WaveDef.Group group;
        final int wave;
        int remaining;
        double timer;
        int index;

        Emitter(WaveDef.Group group, int wave) {
            this.group = group;
            this.wave = wave;
            this.remaining = group.count;
            this.timer = group.delay;
        }
    }

    public World(LevelDef level, SimListener listener) {
        this(level, level.buildWaves(), listener);
    }

    /** Wie {@link #World(LevelDef, SimListener)}, aber mit ausdrücklich vorgegebenen Wellen (Tests, Spezialmodi). */
    public World(LevelDef level, List<WaveDef> waves, SimListener listener) {
        this.level = level;
        this.listener = listener == null ? SimListener.NONE : listener;
        this.paths = level.buildPaths();
        this.waves = new ArrayList<>(waves);
        this.baseWaveCount = waves.size();
        this.width = level.width;
        this.height = level.height;
        this.money = level.startMoney;
        this.lives = level.startLives;
        this.hash = new SpatialHash(width, height, 64);
        int cap = Math.max(8, waves.size());
        this.aliveByWave = new int[cap];
        this.spawnsLeft = new int[cap];
        this.cleared = new boolean[cap];
    }

    /** Nur für Werkzeuge und Tests: beendet das Spiel sofort als Sieg oder Niederlage. */
    public void forceEnd(boolean won) {
        if (state != State.RUNNING) {
            return;
        }
        state = won ? State.WON : State.LOST;
        if (!won) {
            lives = 0;
        }
        listener.onGameEnded(won);
    }

    /** Wechselt in den Endlosmodus (auch mitten im Spiel, z. B. nach dem Sieg über die letzte feste Welle). */
    public void enableEndless() {
        endless = true;
        if (state == State.WON) {
            state = State.RUNNING;
        }
    }

    // ------------------------------------------------------------------------------------------ Befehle

    /** Gibt es noch eine Welle, die gestartet werden kann? */
    public boolean canStartWave() {
        return state == State.RUNNING && (endless || waveIndex < waves.size());
    }

    /**
     * Ist der Moment für die nächste Welle gekommen? Sonst-Fall: alle Wellen sind besiegt. Im Endlosmodus gilt das auch,
     * sobald alles gespawnt ist und nur noch wenige Nachzügler laufen – so zieht sich eine Welle nicht endlos hin,
     * weil ein langsamer Koloss noch über die Karte schleicht.
     */
    public boolean readyForNextWave() {
        if (!canStartWave()) {
            return false;
        }
        return activeWaves == 0 || (endless && emitters.isEmpty() && enemiesRemaining() <= EARLY_START_LEFT);
    }

    /** Startet die nächste Welle (auch während noch Gegner der vorigen unterwegs sind). */
    public boolean startNextWave() {
        if (!canStartWave()) {
            return false;
        }
        int w = waveIndex++;
        ensureWaveCapacity(w + 1);
        WaveDef def = waveAt(w);
        int total = 0;
        for (WaveDef.Group g : def.groups) {
            emitters.add(new Emitter(g, w));
            total += g.count;
        }
        spawnsLeft[w] = total;
        activeWaves++;
        listener.onWaveStarted(w, def);
        return true;
    }

    /** Die Welle mit dem 0-basierten Index {@code i}; im Endlosmodus wird sie bei Bedarf erzeugt. */
    public WaveDef waveAt(int i) {
        while (endless && waves.size() <= i) {
            waves.add(level.buildWave(waves.size() + 1));
        }
        return waves.get(i);
    }

    private void ensureWaveCapacity(int n) {
        if (n > aliveByWave.length) {
            int cap = Math.max(n, aliveByWave.length * 2);
            aliveByWave = Arrays.copyOf(aliveByWave, cap);
            spawnsLeft = Arrays.copyOf(spawnsLeft, cap);
            cleared = Arrays.copyOf(cleared, cap);
        }
    }

    public PlaceCheck checkPlacement(double x, double y) {
        if (x < TOWER_RADIUS || y < TOWER_RADIUS || x > width - TOWER_RADIUS || y > height - TOWER_RADIUS) {
            return PlaceCheck.OUT_OF_BOUNDS;
        }
        double clearance = LANE_HALF_WIDTH + TOWER_RADIUS + 2;
        for (Path p : paths) {
            if (p.distanceTo(x, y) < clearance) {
                return PlaceCheck.ON_PATH;
            }
        }
        for (int i = 0; i < level.zones.size(); i++) {
            if (level.zones.get(i).blocks(x, y, TOWER_RADIUS - 2)) {
                return PlaceCheck.BLOCKED;
            }
        }
        double minGap = 2 * TOWER_RADIUS + TOWER_GAP;
        for (int i = 0; i < towers.size(); i++) {
            Tower o = towers.get(i);
            if (Mathx.distSq(x, y, o.x, o.y) < minGap * minGap) {
                return PlaceCheck.OVERLAP;
            }
        }
        return PlaceCheck.OK;
    }

    /** Das Bonus-Pad, auf dem ein Turm an (x, y) stünde, oder {@code null}. */
    public neontd.level.Zone padAt(double x, double y) {
        for (int i = 0; i < level.zones.size(); i++) {
            neontd.level.Zone z = level.zones.get(i);
            if (z.covers(x, y)) {
                return z;
            }
        }
        return null;
    }

    public boolean canAfford(int cost) {
        return money >= cost;
    }

    /** Übernimmt die Boni aus dem Shop (auch für schon stehende Türme). */
    public void setPerks(Perks p) {
        perks = p == null ? Perks.NONE : p;
        for (int i = 0; i < towers.size(); i++) {
            applyPerks(towers.get(i));
        }
    }

    private void applyPerks(Tower t) {
        t.perkDamage = perks.damage;
        t.perkRange = perks.range;
        t.recompute();
    }

    /** Tatsächlicher Baupreis eines Turmtyps (mit Rabatt aus dem Shop). */
    public int buildCost(TowerType type) {
        return perks.buildCost == 1 ? type.cost : Math.max(1, Mathx.roundToInt(type.cost * perks.buildCost));
    }

    /** Baut einen Turm. Liefert {@code null}, wenn die Stelle ungültig ist oder das Geld nicht reicht. */
    public Tower placeTower(TowerType type, double x, double y) {
        int cost = buildCost(type);
        if (state != State.RUNNING || checkPlacement(x, y) != PlaceCheck.OK || money < cost) {
            return null;
        }
        Tower t = newTower(type, x, y);
        t.invested = cost;
        money -= cost;
        towers.add(t);
        listener.onTowerPlaced(t);
        return t;
    }

    private Tower newTower(TowerType type, double x, double y) {
        Tower t = new Tower(nextId++, type, x, y);
        neontd.level.Zone pad = padAt(x, y);
        t.pad = pad == null ? null : pad.kind;
        t.aim = -Mathx.PI / 2;
        if (type == TowerType.ORBITAL) {
            t.mode = TargetMode.STRONG;
        }
        applyPerks(t);
        return t;
    }

    /** Preis, ein Spezial-Upgrade in den Turm einzubauen oder zu tauschen. */
    public int specialCost(Tower t) {
        return Special.buildCost(t.type);
    }

    /** Baut ein Spezial-Upgrade ein (ersetzt ein vorhandenes). @return ob es geklappt hat */
    public boolean setSpecial(Tower t, Special special) {
        int cost = specialCost(t);
        if (state != State.RUNNING || special == null || t.special == special || !special.allowedFor(t.type)
                || money < cost) {
            return false;
        }
        money -= cost;
        t.invested = Math.min(MAX_MONEY, t.invested + cost);
        t.special = special;
        t.recompute();
        listener.onSpecialSet(t, special);
        return true;
    }

    /** Preis der nächsten Stufe oder -1, wenn die Kategorie voll ausgebaut ist. */
    public int upgradeCost(Tower t, UpgradeTrack track) {
        int lvl = t.level(track);
        return lvl >= maxLevel(track) ? -1 : track.cost(t.type, lvl);
    }

    /** Höchste Stufe einer Kategorie: im Endlosmodus mit Meisterstufen. */
    public int maxLevel(UpgradeTrack track) {
        return track.maxLevel(endless);
    }

    public boolean upgrade(Tower t, UpgradeTrack track) {
        int cost = upgradeCost(t, track);
        if (state != State.RUNNING || cost < 0 || money < cost) {
            return false;
        }
        money -= cost;
        t.invested = Math.min(MAX_MONEY, t.invested + cost);
        t.level[track.ordinal()]++;
        t.recompute();
        listener.onTowerUpgraded(t, track);
        return true;
    }

    public double sellValue(Tower t) {
        return Math.floor(t.invested * (SELL_RATIO + perks.sell));
    }

    /** Schreibt Geld gut, ohne dass die Summe das Limit überschreitet. */
    public void addMoney(double amount) {
        double m = money + amount;
        money = m >= MAX_MONEY ? MAX_MONEY : m;
    }

    public void sell(Tower t) {
        if (state != State.RUNNING || !towers.remove(t)) {
            return;
        }
        double refund = sellValue(t);
        addMoney(refund);
        listener.onTowerSold(t, refund);
    }

    /** Der Turm, dessen Fläche (plus Toleranz) den Punkt enthält; bei mehreren der nächste. */
    public Tower towerAt(double x, double y, double slop) {
        Tower best = null;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < towers.size(); i++) {
            Tower t = towers.get(i);
            double d = Mathx.dist(x, y, t.x, t.y);
            if (d <= TOWER_RADIUS + slop && d < bestD) {
                best = t;
                bestD = d;
            }
        }
        return best;
    }

    // --------------------------------------------------------------------------------------- Abfragen

    public WaveDef nextWave() {
        return canStartWave() ? waveAt(waveIndex) : null;
    }

    /** Wellen, die gestartet, aber noch nicht besiegt sind. */
    public int activeWaves() {
        return activeWaves;
    }

    /** Anzahl bisher vollständig besiegter Wellen. */
    public int clearedWaves() {
        return clearedCount;
    }

    /** Lebende Gegner plus noch nicht erschienene aus den gestarteten Wellen. */
    public int enemiesRemaining() {
        int n = 0;
        for (int w = firstOpen; w < waveIndex; w++) {
            n += aliveByWave[w] + spawnsLeft[w];
        }
        return n;
    }

    /** Anzahl der festen Wellen des Levels (im Endlosmodus kommen weitere hinzu). */
    public int totalWaves() {
        return baseWaveCount;
    }

    // ------------------------------------------------------------------------------------ Speichern

    /** Läuft das Spiel und liefert keine Welle mehr Gegner nach? Nur dann lässt sich der Stand sauber abbilden. */
    public boolean canSnapshot() {
        return state == State.RUNNING && emitters.isEmpty();
    }

    /** Bildet den Spielstand ab oder liefert {@code null}, wenn gerade nicht möglich ({@link #canSnapshot}). */
    public WorldSnapshot snapshot() {
        if (!canSnapshot()) {
            return null;
        }
        WorldSnapshot s = new WorldSnapshot();
        s.money = money;
        s.lives = lives;
        s.kills = kills;
        s.waveIndex = waveIndex;
        s.clearedWaves = clearedCount;
        s.time = time;
        s.endless = endless;
        s.autoStart = autoStart;
        for (int i = 0; i < towers.size(); i++) {
            Tower t = towers.get(i);
            WorldSnapshot.TowerData d = new WorldSnapshot.TowerData();
            d.type = t.type.ordinal();
            d.x = t.x;
            d.y = t.y;
            System.arraycopy(t.level, 0, d.level, 0, 3);
            d.invested = t.invested;
            d.mode = t.mode.ordinal();
            d.special = t.special == null ? 0 : t.special.ordinal() + 1;
            s.towers.add(d);
        }
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            if (!e.alive || e.hp <= 0) {
                continue;
            }
            WorldSnapshot.EnemyData d = new WorldSnapshot.EnemyData();
            d.type = e.type.ordinal();
            d.path = e.pathIndex;
            d.wave = e.wave;
            d.hp = e.hp;
            d.dist = e.dist;
            d.reward = e.reward;
            d.slowFactor = e.slowFactor;
            d.slowTimer = e.slowTimer;
            d.shield = e.shield;
            s.enemies.add(d);
        }
        return s;
    }

    /**
     * Stellt einen gespeicherten Stand in einer frisch erzeugten Welt wieder her. Ungültige Einträge (fremde
     * Ordnungszahlen, unmögliche Plätze) werden übersprungen, damit ein beschädigter Speicherstand nie abstürzt.
     */
    public void restore(WorldSnapshot s) {
        TowerType[] tt = TowerType.values();
        EnemyType[] et = EnemyType.values();
        TargetMode[] modes = TargetMode.values();
        if (s.endless) {
            endless = true;
        }
        autoStart = s.autoStart;
        money = Mathx.clamp(s.money, 0, MAX_MONEY);
        lives = Mathx.clamp(s.lives, 1, Math.max(1, level.startLives + 50));
        kills = Math.max(0, s.kills);
        time = Math.max(0, s.time);
        for (WorldSnapshot.TowerData d : s.towers) {
            if (d.type < 0 || d.type >= tt.length || checkPlacement(d.x, d.y) != PlaceCheck.OK) {
                continue;
            }
            Tower t = newTower(tt[d.type], d.x, d.y);
            Special[] specials = Special.values();
            if (d.special > 0 && d.special <= specials.length && specials[d.special - 1].allowedFor(t.type)) {
                t.special = specials[d.special - 1];
            }
            for (int k = 0; k < 3; k++) {
                t.level[k] = Mathx.clamp(d.level[k], 0, UpgradeTrack.values()[k].maxLevel(endless));
            }
            t.invested = Mathx.clamp(d.invested, t.type.cost, MAX_MONEY);
            t.mode = modes[Mathx.clamp(d.mode, 0, modes.length - 1)];
            t.recompute();
            towers.add(t);
        }
        int wi = Math.max(0, s.waveIndex);
        if (!endless) {
            wi = Math.min(wi, waves.size());
        }
        waveIndex = wi;
        ensureWaveCapacity(wi + 1);
        if (wi > 0) {
            waveAt(wi - 1);
        }
        for (int w = 0; w < wi; w++) {
            cleared[w] = true;
            spawnsLeft[w] = 0;
            aliveByWave[w] = 0;
        }
        for (WorldSnapshot.EnemyData d : s.enemies) {
            if (d.type < 0 || d.type >= et.length || d.wave < 0 || d.wave >= wi || d.hp <= 0 || paths.length == 0) {
                continue;
            }
            int path = Mathx.clamp(d.path, 0, paths.length - 1);
            double dist = Mathx.clamp(d.dist, 0, paths[path].length() - 1);
            Enemy e = addEnemy(et[d.type], path, d.wave, d.hp, dist, Math.max(1, d.reward));
            e.slowFactor = Mathx.clamp(d.slowFactor, 0.2, 1);
            e.slowTimer = Math.max(0, d.slowTimer);
            if (e.shieldMax > 0) {
                e.shield = Mathx.clamp(d.shield, 0, e.shieldMax);
            }
        }
        activeWaves = 0;
        clearedCount = 0;
        firstOpen = wi;
        for (int w = 0; w < wi; w++) {
            if (aliveByWave[w] > 0) {
                cleared[w] = false;
                activeWaves++;
                if (w < firstOpen) {
                    firstOpen = w;
                }
            } else {
                clearedCount++;
            }
        }
        state = State.RUNNING;
    }

    // ------------------------------------------------------------------------------------ Simulation

    /** Ein Simulationsschritt von {@link #STEP} Sekunden. */
    public void step() {
        if (state != State.RUNNING) {
            return;
        }
        time += STEP;
        updateAutoStart();
        updateEmitters();
        moveEnemies();
        rebuildHash();
        updateBuffs();
        updateTowers();
        updateProjectiles();
        processDeaths();
        removeDead();
        checkWaves();
        if (lives <= 0 && state == State.RUNNING) {
            state = State.LOST;
            listener.onGameEnded(false);
        }
    }

    private void updateAutoStart() {
        if (autoStart && readyForNextWave()) {
            autoTimer += STEP;
            if (autoTimer >= AUTO_START_DELAY) {
                autoTimer = 0;
                startNextWave();
            }
        } else {
            autoTimer = 0;
        }
    }

    private void updateEmitters() {
        for (int i = emitters.size() - 1; i >= 0; i--) {
            Emitter em = emitters.get(i);
            em.timer -= STEP;
            while (em.timer <= 0 && em.remaining > 0) {
                spawn(em);
                em.remaining--;
                em.timer += em.group.interval;
            }
            if (em.remaining == 0) {
                emitters.remove(i);
            }
        }
    }

    private void spawn(Emitter em) {
        WaveDef.Group g = em.group;
        int pathIndex = g.path >= 0 ? g.path % paths.length : em.index % paths.length;
        em.index++;
        spawnsLeft[em.wave]--;
        addEnemy(g.type, pathIndex, em.wave, g.hp, 0, g.reward);
    }

    private Enemy addEnemy(EnemyType type, int pathIndex, int wave, int hp, double dist, int reward) {
        Enemy e = new Enemy(nextId++, type, pathIndex, wave, hp, dist, reward);
        if (level.speedMul != 1) {
            e.baseSpeed = type.speed * level.speedMul;
        }
        Path p = paths[pathIndex];
        p.positionAt(dist, tmp);
        e.x = tmp.x;
        e.y = tmp.y;
        e.prevX = e.x;
        e.prevY = e.y;
        e.heading = p.headingAt(dist);
        if (type == EnemyType.SHIELD) {
            e.shieldMax = Math.max(1, Mathx.roundToInt(hp * EnemyType.SHIELD_FRACTION));
            e.shield = e.shieldMax;
        } else if (type == EnemyType.HEALER) {
            e.healTimer = EnemyType.HEAL_INTERVAL;
        }
        enemies.add(e);
        aliveByWave[wave]++;
        listener.onEnemySpawned(e);
        return e;
    }

    private void moveEnemies() {
        for (int i = 0, n = enemies.size(); i < n; i++) {
            Enemy e = enemies.get(i);
            if (!e.alive) {
                continue;
            }
            e.prevX = e.x;
            e.prevY = e.y;
            e.age += STEP;
            if (e.hitFlash > 0) {
                e.hitFlash -= STEP;
            }
            if (e.slowTimer > 0) {
                e.slowTimer -= STEP;
                if (e.slowTimer <= 0) {
                    e.slowTimer = 0;
                    e.slowFactor = 1;
                }
            }
            updateStatus(e);
            Path p = paths[e.pathIndex];
            e.dist += e.speed() * STEP;
            if (e.dist >= p.length()) {
                e.dist = p.length();
                e.alive = false;
                e.leaked = true;
                aliveByWave[e.wave]--;
                lives = Math.max(0, lives - e.type.leakDamage);
                listener.onEnemyLeaked(e, e.type.leakDamage);
                continue;
            }
            p.positionAt(e.dist, tmp);
            e.x = tmp.x;
            e.y = tmp.y;
            e.heading = p.headingAt(e.dist);
        }
    }

    /** Betäubung, Brennen/Gift, Schild-Aufladung und Heilung eines Gegners für einen Schritt. */
    private void updateStatus(Enemy e) {
        if (e.stunTimer > 0) {
            e.stunTimer = Math.max(0, e.stunTimer - STEP);
        }
        if (e.vulnTimer > 0) {
            e.vulnTimer = Math.max(0, e.vulnTimer - STEP);
        }
        if (e.dotTimer > 0) {
            e.dotTimer -= STEP;
            e.dotAcc += e.dotDps * STEP;
            if (e.dotAcc >= 1) {
                int d = (int) e.dotAcc;
                e.dotAcc -= d;
                hurt(e, d);
            }
            if (e.dotTimer <= 0) {
                e.dotTimer = 0;
                e.dotDps = 0;
                e.dotAcc = 0;
                e.poisonStacks = 0;
            }
        }
        if (e.shieldMax > 0 && e.hp > 0) {
            if (e.shieldDelay > 0) {
                e.shieldDelay -= STEP;
            } else if (e.shield < e.shieldMax) {
                e.shieldAcc += e.shieldMax * EnemyType.SHIELD_REGEN * STEP;
                if (e.shieldAcc >= 1) {
                    int add = (int) e.shieldAcc;
                    e.shieldAcc -= add;
                    e.shield = Math.min(e.shieldMax, e.shield + add);
                }
            }
        }
        if (e.type == EnemyType.HEALER && e.hp > 0) {
            e.healTimer -= STEP;
            if (e.healTimer <= 0) {
                e.healTimer += EnemyType.HEAL_INTERVAL;
                healAround(e);
            }
        }
    }

    private void healAround(Enemy healer) {
        double r2 = EnemyType.HEAL_RADIUS * EnemyType.HEAL_RADIUS;
        for (int i = 0, n = enemies.size(); i < n; i++) {
            Enemy o = enemies.get(i);
            if (o == healer || !o.alive || o.hp <= 0 || o.hp >= o.maxHp) {
                continue;
            }
            if (Mathx.distSq(healer.x, healer.y, o.x, o.y) <= r2) {
                double f = o.type == EnemyType.BOSS ? EnemyType.HEAL_FRACTION * 0.3 : EnemyType.HEAL_FRACTION;
                o.hp = Math.min(o.maxHp, o.hp + Math.max(1, Mathx.roundToInt(o.maxHp * f)));
            }
        }
    }

    private void rebuildHash() {
        hash.clear();
        for (int i = 0, n = enemies.size(); i < n; i++) {
            Enemy e = enemies.get(i);
            if (e.alive) {
                hash.insert(i, e.x, e.y);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ Türme

    /**
     * Felder der Kommando-Türme und Kommandanten (Spezial): verstärken Schaden und Tempo der Türme in der Nähe.
     * Mehrere Felder stapeln sich nicht – es gilt das stärkste.
     */
    private void updateBuffs() {
        int n = towers.size();
        boolean any = false;
        for (int i = 0; i < n; i++) {
            Tower t = towers.get(i);
            t.buffDamage = 0;
            t.buffSpeed = 0;
            if (t.type == TowerType.BEACON || t.special == Special.AURA) {
                any = true;
            }
        }
        if (!any) {
            return;
        }
        for (int i = 0; i < n; i++) {
            Tower s = towers.get(i);
            boolean beacon = s.type == TowerType.BEACON;
            if (!beacon && s.special != Special.AURA) {
                continue;
            }
            double dmgB = 0;
            double spdB = 0;
            double radius = 0;
            if (beacon) {
                dmgB = Math.min(TowerType.BEACON_CAP, TowerType.BEACON_DAMAGE
                        + TowerType.BEACON_DAMAGE_PER_LEVEL * s.level(UpgradeTrack.DAMAGE));
                spdB = Math.min(TowerType.BEACON_CAP, TowerType.BEACON_SPEED
                        + TowerType.BEACON_SPEED_PER_LEVEL * s.level(UpgradeTrack.SPEED));
                radius = s.range;
            }
            for (int k = 0; k < n; k++) {
                Tower o = towers.get(k);
                if (o == s) {
                    continue;
                }
                double d2 = Mathx.distSq(s.x, s.y, o.x, o.y);
                if (beacon && d2 <= radius * radius) {
                    o.buffDamage = Math.max(o.buffDamage, dmgB);
                    o.buffSpeed = Math.max(o.buffSpeed, spdB);
                }
                if (s.special == Special.AURA && d2 <= Special.AURA_RADIUS * Special.AURA_RADIUS) {
                    o.buffDamage = Math.max(o.buffDamage, Special.AURA_BONUS);
                }
            }
        }
    }

    private void updateTowers() {
        for (int i = 0, n = towers.size(); i < n; i++) {
            Tower t = towers.get(i);
            t.age += STEP;
            if (t.recoil > 0) {
                t.recoil = Math.max(0, t.recoil - STEP * 6);
            }
            t.cooldown -= STEP;
            if (t.type.passive) {
                updatePassive(t);
                continue;
            }
            Enemy target = pickTarget(t);
            t.hasTarget = target != null;
            if (target == null) {
                t.ramp = 1;
                t.rampTarget = 0;
                if (t.cooldown < 0) {
                    t.cooldown = 0; // ungenutzte Bereitschaft verfällt: kein Feuerstoß nach langer Pause
                }
                continue;
            }
            double desired = Math.atan2(target.y - t.y, target.x - t.x);
            double maxTurn = TURN_RATE * STEP;
            t.aim += Mathx.clamp(Mathx.angleDiff(t.aim, desired), -maxTurn, maxTurn);
            if (t.cooldown <= 0) {
                fire(t, target);
                t.cooldown += t.effectiveInterval();
                if (t.cooldown < 0) {
                    t.cooldown = 0;
                }
            }
        }
    }

    /** Münze: Im Takt Geld erzeugen (wächst mit den Belohnungen der Wellen). Kommando: wirkt in {@link #updateBuffs}. */
    private void updatePassive(Tower t) {
        t.hasTarget = false;
        if (t.type != TowerType.MINT) {
            if (t.cooldown < 0) {
                t.cooldown = 0;
            }
            return;
        }
        if (t.cooldown <= 0) {
            double income = Math.max(1, Mathx.roundToInt(t.power() * WaveFactory.rewardScale(Math.max(1, waveIndex))));
            addMoney(income);
            t.recoil = 1;
            t.cooldown += t.effectiveInterval();
            if (t.cooldown < 0) {
                t.cooldown = 0;
            }
            listener.onTowerIncome(t, income);
        }
    }

    private Enemy pickTarget(Tower t) {
        int cnt = hash.query(t.x, t.y, t.range + MAX_ENEMY_RADIUS, query);
        Enemy best = null;
        double bestKey = 0;
        for (int k = 0; k < cnt; k++) {
            Enemy e = enemies.get(query[k]);
            if (!e.targetable()) {
                continue;
            }
            double d2 = Mathx.distSq(t.x, t.y, e.x, e.y);
            double reach = t.range + e.radius;
            if (d2 > reach * reach) {
                continue;
            }
            double key;
            switch (t.mode) {
                case LAST:
                    key = paths[e.pathIndex].length() - e.dist;
                    break;
                case STRONG:
                    key = e.hp;
                    break;
                case CLOSE:
                    key = -d2;
                    break;
                case FIRST:
                default:
                    key = e.dist - paths[e.pathIndex].length();
                    break;
            }
            if (best == null || key > bestKey || (key == bestKey && e.id < best.id)) {
                best = e;
                bestKey = key;
            }
        }
        return best;
    }

    private void fire(Tower t, Enemy target) {
        double angle = Math.atan2(target.y - t.y, target.x - t.x);
        switch (t.type) {
            case PULSE:
                firePulse(t, target);
                break;
            case SNIPER:
                damage(target, t.power(), t);
                listener.onBeam(t.x + Math.cos(angle) * MUZZLE, t.y + Math.sin(angle) * MUZZLE,
                        target.x, target.y, t.type.color);
                break;
            case MORTAR:
                fireMortar(t, target);
                break;
            case FROST:
                fireFrost(t);
                break;
            case ARC:
            case TESLA:
                fireArc(t, target);
                break;
            case SALVE:
                firePulse(t, target);
                break;
            case RAILGUN:
                fireRail(t, angle);
                break;
            case INFERNO:
                fireInferno(t);
                break;
            case VENOM:
                fireVenom(t, target, angle);
                break;
            case SHREDDER:
                fireShredder(t, target);
                break;
            case GRAVITON:
                fireGraviton(t);
                break;
            case PRISMA:
                firePrisma(t, target, angle);
                break;
            case ORBITAL:
                fireShell(t, target, TowerType.ORBITAL_SHELL_SPEED, 0.9, TowerType.ORBITAL_SPLASH,
                        TowerType.ORBITAL_EDGE_FALLOFF);
                break;
            default:
                break;
        }
        t.recoil = 1;
        listener.onTowerFired(t, angle);
    }

    /**
     * Zielvorhersage entlang des Pfades: Wo ist der Gegner, wenn ein Geschoss mit Geschwindigkeit
     * {@code projSpeed} dort ankommt? Weil Gegner exakt der Bogenlänge folgen, genügt eine kleine
     * Fixpunkt-Iteration (konvergiert, solange das Geschoss schneller ist als der Gegner).
     */
    private double leadTime(Tower t, Enemy e, double projSpeed, double minTime, Vec2 out) {
        Path p = paths[e.pathIndex];
        double v = e.speed();
        double tt = Math.max(minTime, Mathx.dist(t.x, t.y, e.x, e.y) / projSpeed);
        for (int i = 0; i < 5; i++) {
            p.positionAt(e.dist + v * tt, out);
            tt = Math.max(minTime, Mathx.dist(t.x, t.y, out.x, out.y) / projSpeed);
        }
        p.positionAt(e.dist + v * tt, out);
        return tt;
    }

    private void firePulse(Tower t, Enemy target) {
        leadTime(t, target, TowerType.PULSE_BULLET_SPEED, 0, tmp);
        double angle = Math.atan2(tmp.y - t.y, tmp.x - t.x);
        Projectile p = new Projectile(nextId++, Projectile.Kind.BULLET, t.type);
        p.x = t.x + Math.cos(angle) * MUZZLE;
        p.y = t.y + Math.sin(angle) * MUZZLE;
        p.prevX = p.x;
        p.prevY = p.y;
        p.vx = Math.cos(angle) * TowerType.PULSE_BULLET_SPEED;
        p.vy = Math.sin(angle) * TowerType.PULSE_BULLET_SPEED;
        p.radius = BULLET_RADIUS;
        p.damage = t.power();
        p.tower = t;
        p.pierce = (t.type == TowerType.SALVE ? TowerType.SALVE_PIERCE : 1)
                + (t.special == Special.PIERCE ? Special.PIERCE_BONUS : 0);
        p.life = t.range * 1.6 / TowerType.PULSE_BULLET_SPEED;
        projectiles.add(p);
        listener.onProjectileSpawned(p);
    }

    /** Shredder: eine Salve aus Schrotkugeln im Fächer, jede mit vollem Turmschaden, auf kurze Distanz. */
    private void fireShredder(Tower t, Enemy target) {
        double speed = TowerType.SHREDDER_SPEED;
        leadTime(t, target, speed, 0, tmp);
        double angle = Math.atan2(tmp.y - t.y, tmp.x - t.x);
        int n = TowerType.SHREDDER_PELLETS;
        int pierce = 1 + (t.special == Special.PIERCE ? Special.PIERCE_BONUS : 0);
        for (int i = 0; i < n; i++) {
            double a = angle + TowerType.SHREDDER_SPREAD * ((i - (n - 1) / 2.0) / ((n - 1) / 2.0))
                    + rng.range(-0.04, 0.04);
            Projectile p = new Projectile(nextId++, Projectile.Kind.BULLET, t.type);
            p.x = t.x + Math.cos(a) * MUZZLE;
            p.y = t.y + Math.sin(a) * MUZZLE;
            p.prevX = p.x;
            p.prevY = p.y;
            p.vx = Math.cos(a) * speed * rng.range(0.9, 1.05);
            p.vy = Math.sin(a) * speed * rng.range(0.9, 1.05);
            p.radius = 3;
            p.damage = t.power();
            p.tower = t;
            p.pierce = pierce;
            p.life = (t.range + 40) / speed;
            projectiles.add(p);
            listener.onProjectileSpawned(p);
        }
    }

    /** Inferno: Alle Gegner im Ring bekommen Treffer und brennen weiter (stärkstes Feuer gewinnt, Dauer wird erneuert). */
    private void fireInferno(Tower t) {
        int cnt = hash.query(t.x, t.y, t.range + MAX_ENEMY_RADIUS, query);
        int power = t.power();
        for (int k = 0; k < cnt; k++) {
            Enemy e = enemies.get(query[k]);
            if (!e.targetable()) {
                continue;
            }
            double reach = t.range + e.radius;
            if (Mathx.distSq(t.x, t.y, e.x, e.y) > reach * reach) {
                continue;
            }
            damage(e, Math.max(1, power / 2), t);
            ignite(e, power * TowerType.INFERNO_DPS, TowerType.INFERNO_BURN_TIME);
        }
        listener.onNova(t, t.range);
    }

    private static void ignite(Enemy e, double dps, double seconds) {
        e.dotDps = Math.max(e.dotDps, dps);
        e.dotTimer = Math.max(e.dotTimer, seconds);
    }

    /** Gift: Jeder Treffer legt einen Stapel nach (bis 5); jeder Stapel brennt mit festem Anteil des Turmschadens. */
    private void fireVenom(Tower t, Enemy target, double angle) {
        int power = t.power();
        damage(target, power, t);
        target.poisonStacks = Math.min(TowerType.VENOM_MAX_STACKS, target.poisonStacks + 1);
        double dps = power * TowerType.VENOM_DPS * target.poisonStacks;
        target.dotDps = Math.max(target.dotDps, dps);
        target.dotTimer = Math.max(target.dotTimer, TowerType.VENOM_TIME);
        listener.onBeam(t.x + Math.cos(angle) * MUZZLE, t.y + Math.sin(angle) * MUZZLE, target.x, target.y,
                t.type.color);
    }

    /** Graviton: zieht alle Gegner im Feld ein Stück zurück (Titanen kaum) und bremst sie. */
    private void fireGraviton(Tower t) {
        int cnt = hash.query(t.x, t.y, t.range + MAX_ENEMY_RADIUS, query);
        int power = t.power();
        double pull = TowerType.GRAVITON_PULL * (1 + 0.12 * Math.min(20, t.level(UpgradeTrack.DAMAGE)));
        for (int k = 0; k < cnt; k++) {
            Enemy e = enemies.get(query[k]);
            if (!e.targetable()) {
                continue;
            }
            double reach = t.range + e.radius;
            if (Mathx.distSq(t.x, t.y, e.x, e.y) > reach * reach) {
                continue;
            }
            damage(e, power, t);
            e.dist = Math.max(0, e.dist - pull * (e.type == EnemyType.BOSS ? TowerType.GRAVITON_BOSS_FACTOR : 1));
            e.slowFactor = e.slowTimer > 0 ? Math.min(e.slowFactor, TowerType.GRAVITON_SLOW) : TowerType.GRAVITON_SLOW;
            e.slowTimer = Math.max(e.slowTimer, TowerType.GRAVITON_SLOW_TIME);
        }
        listener.onNova(t, t.range);
    }

    /** Prisma: schneller Dauerstrahl; solange das Ziel dasselbe bleibt, wächst die Stärke bis auf das Vierfache. */
    private void firePrisma(Tower t, Enemy target, double angle) {
        if (t.rampTarget != target.id) {
            t.rampTarget = target.id;
            t.ramp = 1;
        }
        double d = t.power() * t.ramp;
        damage(target, d >= Tower.MAX_DAMAGE ? Tower.MAX_DAMAGE : Math.max(1, Mathx.roundToInt(d)), t);
        t.ramp = Math.min(TowerType.PRISMA_MAX, t.ramp + TowerType.PRISMA_RAMP);
        listener.onBeam(t.x + Math.cos(angle) * MUZZLE, t.y + Math.sin(angle) * MUZZLE, target.x, target.y,
                t.type.color);
    }

    private void fireMortar(Tower t, Enemy target) {
        fireShell(t, target, TowerType.MORTAR_SHELL_SPEED, 0.45, TowerType.MORTAR_SPLASH,
                TowerType.MORTAR_EDGE_FALLOFF);
    }

    /** Granate mit festem Ziel (Mörser, Orbital): fliegt im Bogen und explodiert am vorausberechneten Punkt. */
    private void fireShell(Tower t, Enemy target, double speed, double minFlight, double splash, double falloff) {
        double flight = leadTime(t, target, speed, minFlight, tmp);
        Projectile p = new Projectile(nextId++, Projectile.Kind.SHELL, t.type);
        p.x = t.x;
        p.y = t.y;
        p.prevX = p.x;
        p.prevY = p.y;
        p.startX = t.x;
        p.startY = t.y;
        p.targetX = tmp.x;
        p.targetY = tmp.y;
        p.flightTime = flight;
        p.splash = splash;
        p.edgeFalloff = falloff;
        p.tower = t;
        p.damage = t.power();
        p.radius = 6;
        projectiles.add(p);
        listener.onProjectileSpawned(p);
    }

    private void fireFrost(Tower t) {
        int cnt = hash.query(t.x, t.y, t.range + MAX_ENEMY_RADIUS, query);
        for (int k = 0; k < cnt; k++) {
            Enemy e = enemies.get(query[k]);
            if (!e.targetable()) {
                continue;
            }
            double reach = t.range + e.radius;
            if (Mathx.distSq(t.x, t.y, e.x, e.y) > reach * reach) {
                continue;
            }
            damage(e, t.power(), t);
            // Der stärkste laufende Effekt gewinnt; die Dauer wird immer aufgefrischt.
            e.slowFactor = e.slowTimer > 0 ? Math.min(e.slowFactor, t.slow) : t.slow;
            e.slowTimer = Math.max(e.slowTimer, TowerType.FROST_SLOW_TIME);
        }
        listener.onNova(t, t.range);
    }

    private void fireArc(Tower t, Enemy first) {
        chainPts[0] = t.x;
        chainPts[1] = t.y;
        boolean tesla = t.type == TowerType.TESLA;
        int chains = (tesla ? TowerType.TESLA_CHAINS : TowerType.ARC_CHAINS)
                + (t.special == Special.PIERCE ? Special.PIERCE_BONUS : 0);
        double radius = tesla ? TowerType.TESLA_CHAIN_RADIUS : TowerType.ARC_CHAIN_RADIUS;
        double falloff = tesla ? TowerType.TESLA_FALLOFF : TowerType.ARC_FALLOFF;
        double dmg = t.power();
        int hits = 0;
        Enemy cur = first;
        while (cur != null && hits < chains) {
            chainIds[hits] = cur.id;
            hits++;
            chainPts[hits * 2] = cur.x;
            chainPts[hits * 2 + 1] = cur.y;
            damage(cur, dmg >= Tower.MAX_DAMAGE ? Tower.MAX_DAMAGE : Math.max(1, Mathx.roundToInt(dmg)), t);
            dmg *= falloff;
            cur = hits < chains ? nearestChainTarget(cur, hits, radius) : null;
        }
        listener.onLightning(chainPts, hits + 1, t.type.color);
    }

    /** Railgun: Ein Strahl bis ans Ende der Reichweite trifft <b>alle</b> Gegner auf der Linie, ohne Verlust. */
    private void fireRail(Tower t, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double sx = t.x + cos * MUZZLE;
        double sy = t.y + sin * MUZZLE;
        double ex = t.x + cos * t.range;
        double ey = t.y + sin * t.range;
        int cnt = hash.query((sx + ex) / 2, (sy + ey) / 2, t.range / 2 + MAX_ENEMY_RADIUS, query);
        for (int k = 0; k < cnt; k++) {
            Enemy e = enemies.get(query[k]);
            if (!e.targetable()) {
                continue;
            }
            double reach = e.radius + TowerType.RAIL_HALF_WIDTH;
            if (Collision.pointSegmentDistSq(e.x, e.y, sx, sy, ex, ey) <= reach * reach) {
                damage(e, t.power(), t);
            }
        }
        listener.onBeam(sx, sy, ex, ey, t.type.color);
    }

    private Enemy nearestChainTarget(Enemy from, int usedCount, double chainRadius) {
        int cnt = hash.query(from.x, from.y, chainRadius + MAX_ENEMY_RADIUS, query);
        Enemy best = null;
        double bestD = Double.MAX_VALUE;
        for (int k = 0; k < cnt; k++) {
            Enemy e = enemies.get(query[k]);
            if (!e.targetable() || isChained(e.id, usedCount)) {
                continue;
            }
            double d2 = Mathx.distSq(from.x, from.y, e.x, e.y);
            double reach = chainRadius + e.radius;
            if (d2 > reach * reach) {
                continue;
            }
            if (d2 < bestD || (d2 == bestD && e.id < best.id)) {
                best = e;
                bestD = d2;
            }
        }
        return best;
    }

    private boolean isChained(int id, int usedCount) {
        for (int i = 0; i < usedCount; i++) {
            if (chainIds[i] == id) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------- Geschosse

    private void updateProjectiles() {
        for (int i = 0, n = projectiles.size(); i < n; i++) {
            Projectile p = projectiles.get(i);
            if (!p.alive) {
                continue;
            }
            p.prevX = p.x;
            p.prevY = p.y;
            if (p.kind == Projectile.Kind.SHELL) {
                updateShell(p);
            } else {
                updateBullet(p);
            }
        }
        int w = 0;
        for (int i = 0, n = projectiles.size(); i < n; i++) {
            Projectile p = projectiles.get(i);
            if (p.alive) {
                projectiles.set(w++, p);
            }
        }
        while (projectiles.size() > w) {
            projectiles.remove(projectiles.size() - 1);
        }
    }

    private void updateShell(Projectile p) {
        p.elapsed += STEP;
        double u = Math.min(1, p.elapsed / p.flightTime);
        p.x = Mathx.lerp(p.startX, p.targetX, u);
        p.y = Mathx.lerp(p.startY, p.targetY, u);
        if (u >= 1) {
            p.alive = false;
            explode(p.targetX, p.targetY, p.splash, p.damage, p.edgeFalloff, p.color, p.tower);
        }
    }

    private void updateBullet(Projectile p) {
        double dx = p.vx * STEP;
        double dy = p.vy * STEP;
        double reach = Math.sqrt(dx * dx + dy * dy) * 0.5 + p.radius + MAX_ENEMY_RADIUS;
        int cnt = hash.query(p.x + dx * 0.5, p.y + dy * 0.5, reach, query);
        Enemy best = null;
        double bestT = 2;
        for (int k = 0; k < cnt; k++) {
            Enemy e = enemies.get(query[k]);
            if (!e.targetable() || p.alreadyHit(e.id)) {
                continue;
            }
            double t = Collision.sweptCircles(p.x, p.y, dx, dy, p.radius,
                    e.prevX, e.prevY, e.x - e.prevX, e.y - e.prevY, e.radius);
            if (t >= 0 && (t < bestT || (t == bestT && e.id < best.id))) {
                best = e;
                bestT = t;
            }
        }
        if (best != null) {
            p.x += dx * bestT;
            p.y += dy * bestT;
            p.markHit(best.id);
            damage(best, p.damage, p.tower);
            if (--p.pierce <= 0) {
                p.alive = false;
                return;
            }
        } else {
            p.x += dx;
            p.y += dy;
        }
        p.life -= STEP;
        if (p.life <= 0 || p.x < -80 || p.y < -80 || p.x > width + 80 || p.y > height + 80) {
            p.alive = false;
        }
    }

    private void explode(double x, double y, double radius, int dmg, double edgeFalloff, int color, Tower src) {
        int cnt = hash.query(x, y, radius + MAX_ENEMY_RADIUS, query);
        for (int k = 0; k < cnt; k++) {
            Enemy e = enemies.get(query[k]);
            if (!e.targetable()) {
                continue;
            }
            double d = Mathx.dist(x, y, e.x, e.y);
            if (d > radius + e.radius * 0.5) {
                continue;
            }
            double f = 1 - edgeFalloff * Mathx.clamp01(d / radius);
            damage(e, Math.max(1, Mathx.roundToInt(dmg * f)), src);
        }
        listener.onExplosion(x, y, radius, color);
    }

    // ----------------------------------------------------------------------------------- Schaden und Tod

    /** Titanen, Kolosse und Panzer: die "dicken" Ziele für den Titanjäger. */
    private static boolean isHeavy(EnemyType t) {
        return t == EnemyType.BOSS || t == EnemyType.TANK || t == EnemyType.ARMORED;
    }

    /**
     * Ein Treffer von {@code src} (oder {@code null} bei Umgebungsschaden wie Splitter und Nachbeben, der keine
     * Spezial-Upgrades auslöst). Reihenfolge: Spezial-Multiplikatoren, Rüstungsbruch, Panzerung, dann Schild und Leben,
     * zuletzt die Treffer-Effekte des Spezials.
     */
    void damage(Enemy e, int amount, Tower src) {
        if (!e.targetable()) {
            return;
        }
        double a = amount;
        Special sp = src == null ? null : src.special;
        if (sp != null) {
            switch (sp) {
                case CRIT:
                    if (rng.chance(Special.CRIT_CHANCE)) {
                        a *= Special.CRIT_MULT;
                    }
                    break;
                case EXECUTE:
                    if (e.hp < e.maxHp * Special.EXECUTE_BELOW) {
                        a *= Special.EXECUTE_MULT;
                    }
                    break;
                case BOSSBANE:
                    if (isHeavy(e.type)) {
                        a *= Special.BOSSBANE_MULT;
                    }
                    break;
                default:
                    break;
            }
        }
        if (e.vulnTimer > 0) {
            a *= 1 + Special.VULN_BONUS;
        }
        if (e.type == EnemyType.ARMORED && amount < e.maxHp * EnemyType.ARMOR_THRESHOLD) {
            a *= EnemyType.ARMOR_FACTOR;
        }
        int dealt = a >= Tower.MAX_DAMAGE ? Tower.MAX_DAMAGE : Math.max(1, Mathx.roundToInt(a));
        hurt(e, dealt);
        e.hitFlash = 0.12;
        if (src != null) {
            e.lastHitBy = src;
        }
        listener.onEnemyDamaged(e, dealt);
        if (sp != null && e.hp > 0) {
            onHitSpecial(e, src, sp, dealt);
        }
    }

    /** Zieht Schaden erst vom Schild, dann von den Lebenspunkten ab (und hält das Schild vom Aufladen ab). */
    private void hurt(Enemy e, int dealt) {
        if (e.shield > 0) {
            int s = Math.min(e.shield, dealt);
            e.shield -= s;
            dealt -= s;
        }
        if (e.shieldMax > 0) {
            e.shieldDelay = EnemyType.SHIELD_DELAY;
        }
        e.hp -= dealt;
    }

    private void onHitSpecial(Enemy e, Tower src, Special sp, int dealt) {
        boolean boss = e.type == EnemyType.BOSS;
        switch (sp) {
            case SPLASH: {
                int each = Math.max(1, Mathx.roundToInt(dealt * Special.SPLASH_FRACTION));
                int cnt = hash.query(e.x, e.y, Special.SPLASH_RADIUS + MAX_ENEMY_RADIUS, query);
                for (int k = 0; k < cnt; k++) {
                    Enemy o = enemies.get(query[k]);
                    if (o != e && o.targetable()
                            && Mathx.distSq(e.x, e.y, o.x, o.y) <= Special.SPLASH_RADIUS * Special.SPLASH_RADIUS) {
                        damage(o, each, null);
                    }
                }
                break;
            }
            case BURN:
                ignite(e, src.damage * Special.BURN_DPS, Special.BURN_TIME);
                break;
            case CHILL:
                e.slowFactor = e.slowTimer > 0 ? Math.min(e.slowFactor, Special.CHILL_FACTOR) : Special.CHILL_FACTOR;
                e.slowTimer = Math.max(e.slowTimer, Special.CHILL_TIME);
                break;
            case VULN:
                e.vulnTimer = Special.VULN_TIME;
                break;
            case STUN:
                if (rng.chance(Special.STUN_CHANCE)) {
                    e.stunTimer = Math.max(e.stunTimer, boss ? Special.STUN_TIME * 0.35 : Special.STUN_TIME);
                }
                break;
            case KNOCKBACK:
                e.dist = Math.max(0, e.dist - (boss ? Special.KNOCKBACK_DIST * 0.3 : Special.KNOCKBACK_DIST));
                break;
            default:
                break;
        }
    }

    private void processDeaths() {
        for (int i = 0, n = enemies.size(); i < n; i++) {
            Enemy e = enemies.get(i);
            if (!e.alive || e.hp > 0) {
                continue;
            }
            e.alive = false;
            kills++;
            Tower killer = e.lastHitBy;
            double reward = e.reward * perks.bounty;
            if (killer != null && killer.special == Special.BOUNTY) {
                reward *= 1 + Special.BOUNTY_BONUS;
            }
            int paid = Math.max(1, Mathx.roundToInt(reward));
            addMoney(paid);
            aliveByWave[e.wave]--;
            listener.onEnemyKilled(e, paid);
            if (killer != null && killer.special == Special.AFTERSHOCK) {
                explode(e.x, e.y, Special.AFTERSHOCK_RADIUS,
                        Math.max(1, Mathx.roundToInt(killer.damage * Special.AFTERSHOCK_FRACTION)), 0.5,
                        killer.type.color, null);
            }
            if (e.type.splitCount > 0) {
                spawnChildren(e);
            }
        }
    }

    /** Splitter zerfallen in Minis, die leicht versetzt entlang des Pfades erscheinen. */
    private void spawnChildren(Enemy parent) {
        int hp = Math.max(1, Mathx.roundToInt(parent.maxHp * 0.30));
        int reward = Math.max(1, Mathx.roundToInt(EnemyType.MINI.reward * (double) parent.reward / parent.type.reward));
        for (int i = 0; i < parent.type.splitCount; i++) {
            double offset = (i - (parent.type.splitCount - 1) * 0.5) * 16;
            double d = Mathx.clamp(parent.dist + offset, 0, paths[parent.pathIndex].length() - 1);
            addEnemy(EnemyType.MINI, parent.pathIndex, parent.wave, hp, d, reward);
        }
    }

    private void removeDead() {
        int w = 0;
        for (int i = 0, n = enemies.size(); i < n; i++) {
            Enemy e = enemies.get(i);
            if (e.alive) {
                enemies.set(w++, e);
            }
        }
        while (enemies.size() > w) {
            enemies.remove(enemies.size() - 1);
        }
    }

    private void checkWaves() {
        for (int w = firstOpen; w < waveIndex; w++) {
            if (!cleared[w] && spawnsLeft[w] == 0 && aliveByWave[w] == 0) {
                cleared[w] = true;
                clearedCount++;
                activeWaves--;
                int bonus = perks.waveBonus == 1 ? waves.get(w).bonus
                        : Mathx.roundToInt(waves.get(w).bonus * perks.waveBonus);
                addMoney(bonus);
                for (int i = 0; i < towers.size(); i++) {
                    Tower t = towers.get(i);
                    if (t.special == Special.INTEREST) {
                        addMoney(Math.floor(t.invested * Special.INTEREST_RATE));
                    }
                }
                if (endless && (w + 1) % LIFE_EVERY == 0 && lives > 0 && lives < level.startLives) {
                    lives++;
                }
                listener.onWaveCleared(w, bonus);
            }
        }
        while (firstOpen < waveIndex && cleared[firstOpen]) {
            firstOpen++;
        }
        if (state == State.RUNNING && !endless && lives > 0 && waveIndex >= waves.size() && activeWaves == 0) {
            state = State.WON;
            listener.onGameEnded(true);
        }
    }
}

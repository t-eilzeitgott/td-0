package neontd.sim;

import java.util.ArrayList;
import java.util.List;
import neontd.level.LevelDef;
import neontd.math.Mathx;
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

    public enum State { RUNNING, WON, LOST }

    /** Ergebnis einer Platzierungsprüfung. */
    public enum PlaceCheck { OK, OUT_OF_BOUNDS, ON_PATH, OVERLAP }

    public final LevelDef level;
    public final Path[] paths;
    public final List<WaveDef> waves;
    public final double width;
    public final double height;

    public final ArrayList<Enemy> enemies = new ArrayList<>();
    public final ArrayList<Tower> towers = new ArrayList<>();
    public final ArrayList<Projectile> projectiles = new ArrayList<>();

    public int money;
    public int lives;
    public int kills;
    public double time;
    public State state = State.RUNNING;
    /** Anzahl bereits gestarteter Wellen (= Index der nächsten Welle). */
    public int waveIndex;
    public boolean autoStart;

    private final SimListener listener;
    private final SpatialHash hash;
    private final int[] query = new int[QUERY_CAP];
    private final Vec2 tmp = new Vec2();
    private final int[] aliveByWave;
    private final int[] spawnsLeft;
    private final boolean[] cleared;
    private final ArrayList<Emitter> emitters = new ArrayList<>();
    private final double[] chainPts = new double[2 * (TowerType.ARC_CHAINS + 2)];
    private final int[] chainIds = new int[TowerType.ARC_CHAINS + 1];
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
        this.waves = waves;
        this.width = level.width;
        this.height = level.height;
        this.money = level.startMoney;
        this.lives = level.startLives;
        this.hash = new SpatialHash(width, height, 64);
        this.aliveByWave = new int[waves.size()];
        this.spawnsLeft = new int[waves.size()];
        this.cleared = new boolean[waves.size()];
    }

    // ------------------------------------------------------------------------------------------ Befehle

    /** Gibt es noch eine Welle, die gestartet werden kann? */
    public boolean canStartWave() {
        return state == State.RUNNING && waveIndex < waves.size();
    }

    /** Startet die nächste Welle (auch während noch Gegner der vorigen unterwegs sind). */
    public boolean startNextWave() {
        if (!canStartWave()) {
            return false;
        }
        int w = waveIndex++;
        WaveDef def = waves.get(w);
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
        double minGap = 2 * TOWER_RADIUS + TOWER_GAP;
        for (int i = 0; i < towers.size(); i++) {
            Tower o = towers.get(i);
            if (Mathx.distSq(x, y, o.x, o.y) < minGap * minGap) {
                return PlaceCheck.OVERLAP;
            }
        }
        return PlaceCheck.OK;
    }

    public boolean canAfford(int cost) {
        return money >= cost;
    }

    /** Baut einen Turm. Liefert {@code null}, wenn die Stelle ungültig ist oder das Geld nicht reicht. */
    public Tower placeTower(TowerType type, double x, double y) {
        if (state != State.RUNNING || checkPlacement(x, y) != PlaceCheck.OK || money < type.cost) {
            return null;
        }
        Tower t = new Tower(nextId++, type, x, y);
        t.aim = -Mathx.PI / 2;
        money -= type.cost;
        towers.add(t);
        listener.onTowerPlaced(t);
        return t;
    }

    /** Preis der nächsten Stufe oder -1, wenn die Kategorie voll ausgebaut ist. */
    public int upgradeCost(Tower t, UpgradeTrack track) {
        int lvl = t.level(track);
        return lvl >= UpgradeTrack.MAX_LEVEL ? -1 : track.cost(t.type, lvl);
    }

    public boolean upgrade(Tower t, UpgradeTrack track) {
        int cost = upgradeCost(t, track);
        if (state != State.RUNNING || cost < 0 || money < cost) {
            return false;
        }
        money -= cost;
        t.invested += cost;
        t.level[track.ordinal()]++;
        t.recompute();
        listener.onTowerUpgraded(t, track);
        return true;
    }

    public int sellValue(Tower t) {
        return (int) (t.invested * SELL_RATIO);
    }

    public void sell(Tower t) {
        if (state != State.RUNNING || !towers.remove(t)) {
            return;
        }
        int refund = sellValue(t);
        money += refund;
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
        return waveIndex < waves.size() ? waves.get(waveIndex) : null;
    }

    /** Wellen, die gestartet, aber noch nicht besiegt sind. */
    public int activeWaves() {
        return activeWaves;
    }

    /** Lebende Gegner plus noch nicht erschienene aus den gestarteten Wellen. */
    public int enemiesRemaining() {
        int n = 0;
        for (int w = 0; w < waveIndex; w++) {
            n += aliveByWave[w] + spawnsLeft[w];
        }
        return n;
    }

    public int totalWaves() {
        return waves.size();
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
        if (autoStart && canStartWave() && activeWaves == 0) {
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
        addEnemy(g.type, pathIndex, em.wave, g.hp, 0);
    }

    private Enemy addEnemy(EnemyType type, int pathIndex, int wave, int hp, double dist) {
        Enemy e = new Enemy(nextId++, type, pathIndex, wave, hp, dist);
        Path p = paths[pathIndex];
        p.positionAt(dist, tmp);
        e.x = tmp.x;
        e.y = tmp.y;
        e.prevX = e.x;
        e.prevY = e.y;
        e.heading = p.headingAt(dist);
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

    private void updateTowers() {
        for (int i = 0, n = towers.size(); i < n; i++) {
            Tower t = towers.get(i);
            t.age += STEP;
            if (t.recoil > 0) {
                t.recoil = Math.max(0, t.recoil - STEP * 6);
            }
            t.cooldown -= STEP;
            Enemy target = pickTarget(t);
            t.hasTarget = target != null;
            if (target == null) {
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
                t.cooldown += t.interval;
                if (t.cooldown < 0) {
                    t.cooldown = 0;
                }
            }
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
                damage(target, t.damage);
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
                fireArc(t, target);
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
        p.damage = t.damage;
        p.life = t.range * 1.6 / TowerType.PULSE_BULLET_SPEED;
        projectiles.add(p);
        listener.onProjectileSpawned(p);
    }

    private void fireMortar(Tower t, Enemy target) {
        double flight = leadTime(t, target, TowerType.MORTAR_SHELL_SPEED, 0.45, tmp);
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
        p.splash = TowerType.MORTAR_SPLASH;
        p.damage = t.damage;
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
            damage(e, t.damage);
            // Der stärkste laufende Effekt gewinnt; die Dauer wird immer aufgefrischt.
            e.slowFactor = e.slowTimer > 0 ? Math.min(e.slowFactor, t.slow) : t.slow;
            e.slowTimer = Math.max(e.slowTimer, TowerType.FROST_SLOW_TIME);
        }
        listener.onNova(t, t.range);
    }

    private void fireArc(Tower t, Enemy first) {
        chainPts[0] = t.x;
        chainPts[1] = t.y;
        double dmg = t.damage;
        int hits = 0;
        Enemy cur = first;
        while (cur != null && hits < TowerType.ARC_CHAINS) {
            chainIds[hits] = cur.id;
            hits++;
            chainPts[hits * 2] = cur.x;
            chainPts[hits * 2 + 1] = cur.y;
            damage(cur, Math.max(1, Mathx.roundToInt(dmg)));
            dmg *= TowerType.ARC_FALLOFF;
            cur = hits < TowerType.ARC_CHAINS ? nearestChainTarget(cur, hits) : null;
        }
        listener.onLightning(chainPts, hits + 1, t.type.color);
    }

    private Enemy nearestChainTarget(Enemy from, int usedCount) {
        int cnt = hash.query(from.x, from.y, TowerType.ARC_CHAIN_RADIUS + MAX_ENEMY_RADIUS, query);
        Enemy best = null;
        double bestD = Double.MAX_VALUE;
        for (int k = 0; k < cnt; k++) {
            Enemy e = enemies.get(query[k]);
            if (!e.targetable() || isChained(e.id, usedCount)) {
                continue;
            }
            double d2 = Mathx.distSq(from.x, from.y, e.x, e.y);
            double reach = TowerType.ARC_CHAIN_RADIUS + e.radius;
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
            explode(p.targetX, p.targetY, p.splash, p.damage, TowerType.MORTAR_EDGE_FALLOFF, p.color);
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
            damage(best, p.damage);
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

    private void explode(double x, double y, double radius, int dmg, double edgeFalloff, int color) {
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
            damage(e, Math.max(1, Mathx.roundToInt(dmg * f)));
        }
        listener.onExplosion(x, y, radius, color);
    }

    // ----------------------------------------------------------------------------------- Schaden und Tod

    private void damage(Enemy e, int amount) {
        if (!e.targetable()) {
            return;
        }
        e.hp -= amount;
        e.hitFlash = 0.12;
        listener.onEnemyDamaged(e, amount);
    }

    private void processDeaths() {
        for (int i = 0, n = enemies.size(); i < n; i++) {
            Enemy e = enemies.get(i);
            if (!e.alive || e.hp > 0) {
                continue;
            }
            e.alive = false;
            kills++;
            money += e.type.reward;
            aliveByWave[e.wave]--;
            listener.onEnemyKilled(e, e.type.reward);
            if (e.type.splitCount > 0) {
                spawnChildren(e);
            }
        }
    }

    /** Splitter zerfallen in Minis, die leicht versetzt entlang des Pfades erscheinen. */
    private void spawnChildren(Enemy parent) {
        int hp = Math.max(1, Mathx.roundToInt(parent.maxHp * 0.30));
        for (int i = 0; i < parent.type.splitCount; i++) {
            double offset = (i - (parent.type.splitCount - 1) * 0.5) * 16;
            double d = Mathx.clamp(parent.dist + offset, 0, paths[parent.pathIndex].length() - 1);
            addEnemy(EnemyType.MINI, parent.pathIndex, parent.wave, hp, d);
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
        for (int w = 0; w < waveIndex; w++) {
            if (!cleared[w] && spawnsLeft[w] == 0 && aliveByWave[w] == 0) {
                cleared[w] = true;
                activeWaves--;
                int bonus = waves.get(w).bonus;
                money += bonus;
                listener.onWaveCleared(w, bonus);
            }
        }
        if (state == State.RUNNING && lives > 0 && waveIndex >= waves.size() && activeWaves == 0) {
            state = State.WON;
            listener.onGameEnded(true);
        }
    }
}

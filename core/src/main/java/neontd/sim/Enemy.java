package neontd.sim;

/** Ein Gegner. Seine Position ergibt sich vollständig aus {@link #dist} (Bogenlänge auf dem Pfad). */
public final class Enemy {
    public final int id;
    public final EnemyType type;
    public final int pathIndex;
    /** Index der Welle, zu der der Gegner zählt. */
    public final int wave;
    public final double radius;
    public final double baseSpeed;
    public final int maxHp;
    /** Geld beim Abschuss. */
    public final int reward;

    public int hp;
    public double dist;
    public double x;
    public double y;
    public double prevX;
    public double prevY;
    public double heading;
    /** 1 = normale Geschwindigkeit, kleiner = verlangsamt. */
    public double slowFactor = 1;
    public double slowTimer;
    /** Sekunden seit dem Erscheinen (Spawn-Animation, nur Darstellung). */
    public double age;
    /** Sekunden Rest des Treffer-Blitzes (nur Darstellung). */
    public double hitFlash;
    public boolean alive = true;
    public boolean leaked;

    /** Zwischengespeicherte Beschriftung der Lebenspunkte (spart String-Erzeugung beim Zeichnen). */
    public String hpLabel = "";
    public int hpLabelValue = -1;

    public Enemy(int id, EnemyType type, int pathIndex, int wave, int hp, double dist) {
        this(id, type, pathIndex, wave, hp, dist, type.reward);
    }

    public Enemy(int id, EnemyType type, int pathIndex, int wave, int hp, double dist, int reward) {
        this.id = id;
        this.reward = reward;
        this.type = type;
        this.pathIndex = pathIndex;
        this.wave = wave;
        this.radius = type.radius;
        this.baseSpeed = type.speed;
        this.maxHp = hp;
        this.hp = hp;
        this.dist = dist;
    }

    public double speed() {
        return baseSpeed * slowFactor;
    }

    public boolean targetable() {
        return alive && hp > 0;
    }
}

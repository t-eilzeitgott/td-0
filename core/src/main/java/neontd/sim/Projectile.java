package neontd.sim;

/** Ein Geschoss: gerade fliegende Kugel (Puls) oder Granate mit festem Ziel (Mörser). */
public final class Projectile {
    public enum Kind { BULLET, SHELL }

    public final int id;
    public final Kind kind;
    public final TowerType source;
    public final int color;

    public double x;
    public double y;
    public double prevX;
    public double prevY;
    public double vx;
    public double vy;
    public double radius;
    public int damage;
    /** Wie viele Gegner die Kugel noch durchschlägt. */
    public int pierce = 1;
    public double life;
    public boolean alive = true;

    // Granate
    public double startX;
    public double startY;
    public double targetX;
    public double targetY;
    public double flightTime;
    public double elapsed;
    public double splash;

    private final int[] hit = new int[4];
    private int hitCount;

    public Projectile(int id, Kind kind, TowerType source) {
        this.id = id;
        this.kind = kind;
        this.source = source;
        this.color = source.color;
    }

    public boolean alreadyHit(int enemyId) {
        for (int i = 0; i < hitCount; i++) {
            if (hit[i] == enemyId) {
                return true;
            }
        }
        return false;
    }

    public void markHit(int enemyId) {
        if (hitCount < hit.length) {
            hit[hitCount++] = enemyId;
        }
    }

    /** Flughöhe der Granate (0..1..0) für die Darstellung eines Bogens. */
    public double arc() {
        if (kind != Kind.SHELL || flightTime <= 0) {
            return 0;
        }
        double u = Math.min(1, elapsed / flightTime);
        return 4 * u * (1 - u);
    }
}

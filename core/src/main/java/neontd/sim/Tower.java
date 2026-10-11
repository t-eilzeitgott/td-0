package neontd.sim;

/** Ein platzierter Turm mit Upgrade-Stufen und abgeleiteten Werten. */
public final class Tower {
    /** Obergrenze für den Schaden eines Treffers (bleibt weit unter dem int-Bereich). */
    public static final int MAX_DAMAGE = 1_000_000_000;

    public final int id;
    public final TowerType type;
    public final double x;
    public final double y;
    /** Stufe je {@link UpgradeTrack} (Index = ordinal). */
    public final int[] level = new int[3];
    public TargetMode mode = TargetMode.FIRST;
    /** Insgesamt investiertes Geld (Kauf + Upgrades) – Grundlage für den Verkaufspreis. */
    public double invested;
    /** Eingebautes Spezial-Upgrade oder {@code null}. */
    public Special special;
    /** Dauerhafte Boni aus der Forschung im Shop (1 = keiner). */
    public double perkDamage = 1;
    public double perkRange = 1;

    // abgeleitete Werte (siehe recompute)
    public double range;
    public double interval;
    public int damage;
    public double slow;

    // Zustand
    public double cooldown;
    /** Aktueller Blickwinkel (nur Darstellung). */
    public double aim;
    /** Rückstoß-Animation 1 → 0 (nur Darstellung). */
    public double recoil;
    /** Sekunden seit dem Bau (Pop-Animation, nur Darstellung). */
    public double age;
    public boolean hasTarget;
    /** Verstärkung durch Kommando-Türme und Kommandanten in der Nähe (0 = keine), neu berechnet je Schritt. */
    public double buffDamage;
    public double buffSpeed;
    /** Prisma: aufgebaute Stärke und das Ziel, auf das sie sich bezieht. */
    public double ramp = 1;
    public int rampTarget;

    public Tower(int id, TowerType type, double x, double y) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.invested = type.cost;
        recompute();
    }

    public void recompute() {
        double r = type.range * UpgradeTrack.RANGE.multAt(level[UpgradeTrack.RANGE.ordinal()]) * perkRange;
        if (special == Special.LONGSHOT) {
            r *= Special.LONGSHOT_RANGE;
        }
        range = r;
        double dmg = type.damage * UpgradeTrack.DAMAGE.multAt(level[UpgradeTrack.DAMAGE.ordinal()]) * perkDamage;
        damage = dmg >= MAX_DAMAGE ? MAX_DAMAGE : Math.max(1, neontd.math.Mathx.roundToInt(dmg));
        double speed = UpgradeTrack.SPEED.multAt(level[UpgradeTrack.SPEED.ordinal()]);
        if (special == Special.OVERCLOCK) {
            speed *= Special.OVERCLOCK_SPEED;
        }
        interval = type.interval / speed;
        slow = Math.max(0.25, TowerType.FROST_SLOW - TowerType.FROST_SLOW_PER_LEVEL * level[UpgradeTrack.DAMAGE.ordinal()]);
    }

    public int level(UpgradeTrack t) {
        return level[t.ordinal()];
    }

    /** Anzahl aller gekauften Upgrade-Stufen (0..15 im normalen Spiel, mehr mit Meisterstufen). */
    public int totalLevels() {
        return level[0] + level[1] + level[2];
    }

    /** Schaden eines Treffers samt Verstärkung durch Kommando-Felder. */
    public int power() {
        if (buffDamage <= 0) {
            return damage;
        }
        double d = damage * (1 + buffDamage);
        return d >= MAX_DAMAGE ? MAX_DAMAGE : Math.max(1, neontd.math.Mathx.roundToInt(d));
    }

    /** Sekunden zwischen zwei Schüssen samt Verstärkung durch Kommando-Felder. */
    public double effectiveInterval() {
        return buffSpeed <= 0 ? interval : interval / (1 + buffSpeed);
    }

    /** Schüsse pro Sekunde. */
    public double rate() {
        return 1.0 / interval;
    }
}

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
    /** Bonus-Pad, auf dem der Turm steht, oder {@code null}. */
    public neontd.level.Zone.Kind pad;
    /** Gewählter Ausbaupfad (0 = keiner, 1 = A, 2 = B) und sein Rang (0–2). */
    public int branch;
    public int rank;
    /** Schüsse seit dem letzten Kondensator-Schlag (Tesla B). */
    public int shotCount;

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
        if (pad == neontd.level.Zone.Kind.PAD_RANGE) {
            r *= 1 + neontd.level.Zone.PAD_RANGE_BONUS;
        }
        if (has(Branch.FROST_B, 1)) {
            r *= 1.25;
        }
        range = r;
        double dmg = type.damage * UpgradeTrack.DAMAGE.multAt(level[UpgradeTrack.DAMAGE.ordinal()]) * perkDamage;
        if (pad == neontd.level.Zone.Kind.PAD_DAMAGE) {
            dmg *= 1 + neontd.level.Zone.PAD_DAMAGE_BONUS;
        }
        if (has(Branch.PULSE_B, 1)) {
            dmg *= 1.25;
        }
        damage = dmg >= MAX_DAMAGE ? MAX_DAMAGE : Math.max(1, neontd.math.Mathx.roundToInt(dmg));
        double speed = UpgradeTrack.SPEED.multAt(level[UpgradeTrack.SPEED.ordinal()]);
        if (special == Special.OVERCLOCK) {
            speed *= Special.OVERCLOCK_SPEED;
        }
        if (pad == neontd.level.Zone.Kind.PAD_SPEED) {
            speed *= 1 + neontd.level.Zone.PAD_SPEED_BONUS;
        }
        if (has(Branch.SALVE_A, 2)) {
            speed *= 1.5;
        } else if (has(Branch.SALVE_A, 1)) {
            speed *= 1.25;
        }
        interval = type.interval / speed;
        slow = Math.max(0.25, TowerType.FROST_SLOW - TowerType.FROST_SLOW_PER_LEVEL * level[UpgradeTrack.DAMAGE.ordinal()]
                - (has(Branch.FROST_A, 1) ? 0.12 : 0));
    }

    /** Hat der Turm den Pfad {@code b} mindestens in Rang {@code rank}? */
    public boolean has(Branch b, int rank) {
        return b.tower == type && branch == b.side() && this.rank >= rank;
    }

    /** Der Rang, der als Nächstes gekauft werden kann (1 oder 2), oder 0, wenn voll ausgebaut. */
    public int nextRank() {
        return rank >= 2 ? 0 : rank + 1;
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

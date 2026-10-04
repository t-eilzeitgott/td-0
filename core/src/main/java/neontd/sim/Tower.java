package neontd.sim;

/** Ein platzierter Turm mit Upgrade-Stufen und abgeleiteten Werten. */
public final class Tower {
    public final int id;
    public final TowerType type;
    public final double x;
    public final double y;
    /** Stufe je {@link UpgradeTrack} (Index = ordinal). */
    public final int[] level = new int[3];
    public TargetMode mode = TargetMode.FIRST;
    /** Insgesamt investiertes Geld (Kauf + Upgrades) – Grundlage für den Verkaufspreis. */
    public int invested;

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

    public Tower(int id, TowerType type, double x, double y) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.invested = type.cost;
        recompute();
    }

    public void recompute() {
        range = type.range * UpgradeTrack.RANGE.mult[level[UpgradeTrack.RANGE.ordinal()]];
        damage = Math.max(1, neontd.math.Mathx.roundToInt(
                type.damage * UpgradeTrack.DAMAGE.mult[level[UpgradeTrack.DAMAGE.ordinal()]]));
        interval = type.interval / UpgradeTrack.SPEED.mult[level[UpgradeTrack.SPEED.ordinal()]];
        slow = Math.max(0.25, TowerType.FROST_SLOW - TowerType.FROST_SLOW_PER_LEVEL * level[UpgradeTrack.DAMAGE.ordinal()]);
    }

    public int level(UpgradeTrack t) {
        return level[t.ordinal()];
    }

    public boolean maxed(UpgradeTrack t) {
        return level[t.ordinal()] >= UpgradeTrack.MAX_LEVEL;
    }

    /** Anzahl aller gekauften Upgrade-Stufen (0..15). */
    public int totalLevels() {
        return level[0] + level[1] + level[2];
    }

    /** Schüsse pro Sekunde. */
    public double rate() {
        return 1.0 / interval;
    }
}

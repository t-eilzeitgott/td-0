package neontd.sim;

import neontd.math.Mathx;

/**
 * Die drei Upgrade-Kategorien, die jeder Turm hat. Im normalen Spiel hat jede {@link #MAX_LEVEL} Stufen; im
 * Endlosmodus kommen <b>Meisterstufen</b> dazu (bis {@link #endlessMax}). Sie wachsen mit gleichbleibendem Faktor
 * pro Stufe und werden in gleichem Maß teurer – so hält die Turmstärke mit den Gegner-HP bis Welle 1000 Schritt.
 */
public enum UpgradeTrack {
    RANGE("REICHWEITE", 0x4DA6FF, 0.7, new double[] {1.00, 1.10, 1.21, 1.33, 1.47, 1.62}, 0.07, true, 12),
    DAMAGE("SCHADEN", 0xFF4D5E, 1.0, new double[] {1.0, 1.4, 2.0, 3.0, 4.6, 7.0}, 1.23, false, 60),
    SPEED("TEMPO", 0xFFD60A, 0.9, new double[] {1.0, 1.2, 1.5, 1.9, 2.4, 3.1}, 1.10, false, 18);

    public static final int MAX_LEVEL = 5;
    /** Preis der Stufe i+1 als Anteil des Turm-Grundpreises (vor dem Track-Faktor), bis {@link #MAX_LEVEL}. */
    private static final double[] COST_STEPS = {0.5, 0.8, 1.2, 1.8, 2.8};
    /** Jede Meisterstufe kostet so viel mehr wie die vorige. */
    public static final double MASTER_COST_GROWTH = 1.36;
    /** Obergrenze für Preise (bleibt unter dem Geld-Limit der Welt). */
    private static final double COST_CAP = 1.5e9;

    public final String label;
    public final int color;
    private final double costFactor;
    /** Multiplikatoren für die Stufen 0..5 (Index = Stufe). */
    public final double[] mult;
    /** Höchste Stufe im Endlosmodus. */
    public final int endlessMax;
    private final double[] masterMult;

    UpgradeTrack(String label, int color, double costFactor, double[] mult, double growth, boolean additive,
                 int endlessMax) {
        this.label = label;
        this.color = color;
        this.costFactor = costFactor;
        this.mult = mult;
        this.endlessMax = endlessMax;
        // Wiederholte Multiplikation statt Math.pow: auf JVM und im Browser bitgleich.
        this.masterMult = new double[endlessMax + 1];
        for (int i = 0; i <= endlessMax; i++) {
            if (i <= MAX_LEVEL) {
                masterMult[i] = mult[i];
            } else if (additive) {
                masterMult[i] = masterMult[i - 1] + growth;
            } else {
                masterMult[i] = masterMult[i - 1] * growth;
            }
        }
    }

    /** Multiplikator auf den Grundwert bei Stufe {@code level} (auch für Meisterstufen). */
    public double multAt(int level) {
        return masterMult[Mathx.clamp(level, 0, endlessMax)];
    }

    /** Höchste erreichbare Stufe. */
    public int maxLevel(boolean endless) {
        return endless ? endlessMax : MAX_LEVEL;
    }

    /** Preis, um von {@code level} auf {@code level + 1} zu verbessern (gerundet, auf int begrenzt). */
    public int cost(TowerType type, int level) {
        double step;
        if (level < COST_STEPS.length) {
            step = COST_STEPS[level];
        } else {
            step = COST_STEPS[COST_STEPS.length - 1];
            for (int i = COST_STEPS.length; i <= level; i++) {
                step *= MASTER_COST_GROWTH;
            }
        }
        double c = Math.min(COST_CAP, type.cost * step * costFactor);
        if (c < 1000) {
            return Math.max(5, Mathx.roundToInt(c / 5.0) * 5);
        }
        // Große Preise auf drei wertige Ziffern runden, damit die Zahlen ordentlich bleiben.
        double unit = 1;
        while (c / unit >= 1000) {
            unit *= 10;
        }
        return (int) (Math.floor(c / unit + 0.5) * unit);
    }
}

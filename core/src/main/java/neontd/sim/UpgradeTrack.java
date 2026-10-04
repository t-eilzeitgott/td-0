package neontd.sim;

/**
 * Die drei Upgrade-Kategorien, die jeder Turm hat. Jede hat {@link #MAX_LEVEL} Stufen; die Werte in
 * {@link #mult} sind Multiplikatoren auf den Grundwert des Turms (Index = aktuelle Stufe).
 */
public enum UpgradeTrack {
    RANGE("REICHWEITE", 0x4DA6FF, 0.7, new double[] {1.00, 1.10, 1.21, 1.33, 1.47, 1.62}),
    DAMAGE("SCHADEN", 0xFF4D5E, 1.0, new double[] {1.0, 1.4, 2.0, 3.0, 4.6, 7.0}),
    SPEED("TEMPO", 0xFFD60A, 0.9, new double[] {1.0, 1.2, 1.5, 1.9, 2.4, 3.1});

    public static final int MAX_LEVEL = 5;
    /** Preis der Stufe i+1 als Anteil des Turm-Grundpreises (vor dem Track-Faktor). */
    private static final double[] COST_STEPS = {0.5, 0.8, 1.2, 1.8, 2.8};

    public final String label;
    public final int color;
    private final double costFactor;
    public final double[] mult;

    UpgradeTrack(String label, int color, double costFactor, double[] mult) {
        this.label = label;
        this.color = color;
        this.costFactor = costFactor;
        this.mult = mult;
    }

    /** Preis, um von {@code level} auf {@code level + 1} zu verbessern (auf 5er gerundet). */
    public int cost(TowerType type, int level) {
        double c = type.cost * COST_STEPS[level] * costFactor;
        return Math.max(5, (int) (Math.round(c / 5.0) * 5));
    }
}

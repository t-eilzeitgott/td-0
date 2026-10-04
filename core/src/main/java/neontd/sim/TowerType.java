package neontd.sim;

/**
 * Die fünf Turmklassen. Alle Zahlen sind Grundwerte auf Stufe 0; die drei Upgrade-Kategorien
 * (Reichweite, Schaden, Tempo) skalieren sie über {@link UpgradeTrack}.
 */
public enum TowerType {
    PULSE("PULS", "Schneller Allrounder", 0x00E5FF, 100, 150, 4, 0.50, 1),
    SNIPER("SNIPER", "Sehr weit, sehr stark", 0xFF3DCB, 280, 330, 34, 1.90, 8),
    MORTAR("MÖRSER", "Flächenschaden", 0xFF8A00, 240, 235, 14, 1.60, 4),
    FROST("FROST", "Verlangsamt alle in Reichweite", 0x7DA2FF, 160, 108, 2, 1.40, 2),
    ARC("BLITZ", "Springt zwischen Zielen", 0xB6FF3B, 220, 142, 5, 0.90, 6),
    // Die drei großen Türme: teurer als alle anderen, dafür deutlich stärker.
    SALVE("SALVE", "Schnellfeuer, durchschlägt Gegner", 0xFF5470, 420, 190, 8, 0.26, 10),
    TESLA("TESLA", "Lange Blitzketten", 0x9B7DFF, 560, 175, 16, 0.85, 13),
    RAILGUN("RAILGUN", "Strahl durch die ganze Reihe", 0xFFD60A, 900, 400, 140, 2.40, 16);

    public final String label;
    public final String tagline;
    /** Neonfarbe 0xRRGGBB. */
    public final int color;
    public final int cost;
    public final double range;
    public final int damage;
    /** Sekunden zwischen zwei Schüssen. */
    public final double interval;
    /** Ab diesem Spielerlevel steht der Turm zur Verfügung (siehe {@code neontd.progress.Progress}). */
    public final int unlockLevel;

    // ---- typspezifische Parameter (nicht skalierend) ----
    /** Geschossgeschwindigkeit (Puls) in Welteinheiten/s. */
    public static final double PULSE_BULLET_SPEED = 820;
    /** Mörser: Fluggeschwindigkeit der Granate, Explosionsradius, Schadensverlust am Rand. */
    public static final double MORTAR_SHELL_SPEED = 330;
    public static final double MORTAR_SPLASH = 74;
    public static final double MORTAR_EDGE_FALLOFF = 0.5;
    /** Frost: Geschwindigkeitsfaktor (kleiner = langsamer) und Dauer; pro Schadensstufe etwas stärker. */
    public static final double FROST_SLOW = 0.55;
    public static final double FROST_SLOW_PER_LEVEL = 0.05;
    public static final double FROST_SLOW_TIME = 1.7;
    /** Blitz: maximale Sprünge, Sprungweite, Schadensfaktor pro Sprung. */
    public static final int ARC_CHAINS = 4;
    public static final double ARC_CHAIN_RADIUS = 120;
    public static final double ARC_FALLOFF = 0.75;
    /** Tesla: längere Kette, größere Sprünge, kaum Verlust pro Sprung. */
    public static final int TESLA_CHAINS = 8;
    public static final double TESLA_CHAIN_RADIUS = 150;
    public static final double TESLA_FALLOFF = 0.88;
    /** Größte Kettenlänge aller Türme (Puffergröße). */
    public static final int MAX_CHAINS = 8;
    /** Salve: Geschosse durchschlagen so viele Gegner. */
    public static final int SALVE_PIERCE = 3;
    /** Railgun: halbe Breite des Strahls (zusätzlich zum Gegnerradius). */
    public static final double RAIL_HALF_WIDTH = 7;

    TowerType(String label, String tagline, int color, int cost, double range, int damage, double interval,
              int unlockLevel) {
        this.label = label;
        this.tagline = tagline;
        this.color = color;
        this.cost = cost;
        this.range = range;
        this.damage = damage;
        this.interval = interval;
        this.unlockLevel = unlockLevel;
    }
}

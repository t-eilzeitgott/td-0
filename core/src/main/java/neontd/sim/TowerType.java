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
    RAILGUN("RAILGUN", "Strahl durch die ganze Reihe", 0xFFD60A, 900, 400, 140, 2.40, 16),
    // Die Elite-Türme (Stufe 2): kommen aus dem Shop im Hauptmenü (Kristalle) und bieten eigene Mechaniken.
    INFERNO("INFERNO", "Flammenring: Brandschaden über Zeit", 0xFF5A1F, 480, 112, 10, 0.50, 12, 600, false),
    VENOM("GIFT", "Giftstapel: je Treffer stärker", 0x3DFF9A, 520, 205, 12, 1.00, 18, 800, false),
    SHREDDER("SHREDDER", "Schrotsalve auf kurze Distanz", 0xFF9E5E, 650, 125, 4, 0.45, 24, 1000, false),
    GRAVITON("GRAVITON", "Zieht Gegner zurück und bremst", 0x6A5CFF, 700, 150, 5, 1.60, 30, 1300, false),
    MINT("MÜNZE", "Erzeugt Geld, schießt nicht", 0xFFC857, 600, 0, 15, 5.00, 36, 1600, true),
    PRISMA("PRISMA", "Dauerstrahl, wird mit der Zeit stärker", 0xF4A6FF, 950, 230, 2, 0.10, 42, 2000, false),
    BEACON("KOMMANDO", "Verstärkt Türme im Umkreis", 0xE6F7FF, 800, 180, 18, 1.00, 48, 2400, true),
    ORBITAL("ORBITAL", "Schlag auf den stärksten Gegner, überall", 0xFF2D55, 1400, 620, 220, 5.00, 55, 3000, false);

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
    /** 1 = Grundturm (frei mit Spielerlevel), 2 = Elite-Turm (Shop, Kristalle). */
    public final int tier;
    /** Preis im Shop (Kristalle); 0 bei Grundtürmen. */
    public final int crystals;
    /** Hilfsturm ohne Ziel: Münze und Kommando greifen nicht an. */
    public final boolean passive;

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
    public static final int MAX_CHAINS = 10;
    /** Inferno: Brenndauer und Anteil des Turmschadens, der pro Sekunde brennt; der Treffer selbst macht die Hälfte. */
    public static final double INFERNO_BURN_TIME = 2.6;
    public static final double INFERNO_DPS = 1.2;
    /** Gift: Stapelgrenze, Dauer und Anteil des Turmschadens pro Sekunde und Stapel. */
    public static final int VENOM_MAX_STACKS = 5;
    public static final double VENOM_TIME = 4.0;
    public static final double VENOM_DPS = 0.55;
    /** Shredder: Schrotkugeln pro Salve, halber Öffnungswinkel, Geschwindigkeit. */
    public static final int SHREDDER_PELLETS = 7;
    public static final double SHREDDER_SPREAD = 0.36;
    public static final double SHREDDER_SPEED = 900;
    /** Graviton: Rückzug in Welteinheiten (Titanen nur zu {@link #GRAVITON_BOSS_FACTOR}), Bremsfaktor und -dauer. */
    public static final double GRAVITON_PULL = 46;
    public static final double GRAVITON_BOSS_FACTOR = 0.35;
    public static final double GRAVITON_SLOW = 0.5;
    public static final double GRAVITON_SLOW_TIME = 1.6;
    /** Münze: Grundertrag je Takt (wächst mit den Belohnungen der Wellen). */
    public static final int MINT_INCOME = 15;
    /** Kommando: Grundstärke des Feldes (Schaden und Tempo) und Zuwachs je Upgrade-Stufe. */
    public static final double BEACON_DAMAGE = 0.18;
    public static final double BEACON_DAMAGE_PER_LEVEL = 0.06;
    public static final double BEACON_SPEED = 0.08;
    public static final double BEACON_SPEED_PER_LEVEL = 0.04;
    public static final double BEACON_CAP = 1.0;
    /** Prisma: Aufbau der Strahlstärke je Takt und Obergrenze (Faktor). */
    public static final double PRISMA_RAMP = 0.1;
    public static final double PRISMA_MAX = 4.0;
    /** Orbital: Fluggeschwindigkeit, Radius und Kantenverlust der Einschläge. */
    public static final double ORBITAL_SHELL_SPEED = 560;
    public static final double ORBITAL_SPLASH = 105;
    public static final double ORBITAL_EDGE_FALLOFF = 0.3;
    /** Salve: Geschosse durchschlagen so viele Gegner. */
    public static final int SALVE_PIERCE = 3;
    /** Railgun: halbe Breite des Strahls (zusätzlich zum Gegnerradius). */
    public static final double RAIL_HALF_WIDTH = 7;

    TowerType(String label, String tagline, int color, int cost, double range, int damage, double interval,
              int unlockLevel) {
        this(label, tagline, color, cost, range, damage, interval, unlockLevel, 0, false);
    }

    TowerType(String label, String tagline, int color, int cost, double range, int damage, double interval,
              int unlockLevel, int crystals, boolean passive) {
        this.tier = crystals > 0 ? 2 : 1;
        this.crystals = crystals;
        this.passive = passive;
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

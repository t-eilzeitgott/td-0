package neontd.sim;

/**
 * Gegnerarten. Die Form (Shape) dient der schnellen Unterscheidung auf dem Bildschirm, die Farbe zeigt die
 * aktuellen Lebenspunkte (wie bei BBTAN: je mehr, desto "heißer").
 */
public enum EnemyType {
    //     name        form                   radius speed hpMul reward leak split
    BLOCK("Block", Shape.SQUARE, 17, 84, 1.0, 3, 1, 0),
    DART("Dart", Shape.TRIANGLE, 14, 150, 0.6, 3, 1, 0),
    SPLIT("Splitter", Shape.DIAMOND, 20, 74, 2.0, 5, 2, 3),
    MINI("Mini", Shape.CIRCLE, 11, 112, 0.34, 1, 1, 0),
    TANK("Koloss", Shape.HEXAGON, 25, 50, 5.0, 14, 3, 0),
    BOSS("Titan", Shape.OCTAGON, 34, 38, 30.0, 150, 10, 0),
    // Die Elite-Gegner: jeder verlangt eine andere Antwort der Verteidigung.
    /** Panzer: schwache Treffer (unter 3 % seiner Lebenspunkte) richten nur 65 % Schaden an. */
    ARMORED("Panzer", Shape.PENTAGON, 22, 58, 2.0, 9, 2, 0),
    /** Schildträger: Schild in Höhe von 40 % der Lebenspunkte; lädt sich auf, wenn er kurz unbehelligt bleibt. */
    SHIELD("Schildträger", Shape.SHIELD, 19, 76, 1.1, 6, 1, 0),
    /** Sanitäter: heilt alle Gegner in der Nähe, solange er lebt. */
    HEALER("Sanitäter", Shape.CROSS, 17, 70, 1.0, 8, 1, 0),
    /** Phantom: wechselt immer wieder in eine Phase, in der es nicht getroffen werden kann. */
    GHOST("Phantom", Shape.STAR, 15, 104, 0.9, 7, 1, 0);

    /** Grundform für die Darstellung. */
    public enum Shape { SQUARE, TRIANGLE, DIAMOND, CIRCLE, HEXAGON, OCTAGON, PENTAGON, SHIELD, CROSS, STAR }

    /** Anteil der Lebenspunkte, den das Schild des Schildträgers zusätzlich trägt. */
    public static final double SHIELD_FRACTION = 0.4;
    /** Sekunden ohne Treffer, bis sich das Schild auflädt, und Anteil des Schilds, der pro Sekunde zurückkommt. */
    public static final double SHIELD_DELAY = 2.0;
    public static final double SHIELD_REGEN = 0.15;
    /** Panzer: Treffer unter diesem Anteil seiner Lebenspunkte richten nur {@link #ARMOR_FACTOR} Schaden an. */
    public static final double ARMOR_THRESHOLD = 0.03;
    public static final double ARMOR_FACTOR = 0.65;
    /** Sanitäter: Heilradius, Abstand zwischen zwei Heilungen und geheilter Anteil der Lebenspunkte. */
    public static final double HEAL_RADIUS = 110;
    public static final double HEAL_INTERVAL = 1.5;
    public static final double HEAL_FRACTION = 0.025;
    /** Phantom: Länge eines Zyklus und der Teil davon, in dem es unverwundbar ist. */
    public static final double PHASE_CYCLE = 4.2;
    public static final double PHASE_LENGTH = 1.2;

    public final String label;
    public final Shape shape;
    /** Trefferradius in Welteinheiten. */
    public final double radius;
    /** Grundgeschwindigkeit in Welteinheiten pro Sekunde. */
    public final double speed;
    /** Faktor auf die wellenabhängige Basis-HP. */
    public final double hpMul;
    /** Geld beim Abschuss. */
    public final int reward;
    /** Verlorene Leben, wenn der Gegner das Ziel erreicht. */
    public final int leakDamage;
    /** Anzahl Mini-Gegner, die beim Tod entstehen. */
    public final int splitCount;

    EnemyType(String label, Shape shape, double radius, double speed, double hpMul, int reward, int leakDamage,
              int splitCount) {
        this.label = label;
        this.shape = shape;
        this.radius = radius;
        this.speed = speed;
        this.hpMul = hpMul;
        this.reward = reward;
        this.leakDamage = leakDamage;
        this.splitCount = splitCount;
    }
}

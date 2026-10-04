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
    BOSS("Titan", Shape.OCTAGON, 34, 38, 30.0, 150, 10, 0);

    /** Grundform für die Darstellung. */
    public enum Shape { SQUARE, TRIANGLE, DIAMOND, CIRCLE, HEXAGON, OCTAGON }

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

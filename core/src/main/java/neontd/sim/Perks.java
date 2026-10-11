package neontd.sim;

/**
 * Dauerhafte Boni aus der Forschung im Shop des Hauptmenüs, die eine Welt beim Spielen berücksichtigt. Der
 * Standard {@link #NONE} ändert nichts (so bleiben Tests und Bot-Läufe unverändert).
 */
public final class Perks {
    public static final Perks NONE = new Perks();

    /** Faktor auf Turmschaden und Reichweite. */
    public double damage = 1;
    public double range = 1;
    /** Faktor auf die Baukosten der Türme. */
    public double buildCost = 1;
    /** Zuschlag auf den Verkaufswert (0,05 = 75 % statt 70 %). */
    public double sell;
    /** Faktor auf Abschussprämien und Wellenboni. */
    public double bounty = 1;
    public double waveBonus = 1;
}

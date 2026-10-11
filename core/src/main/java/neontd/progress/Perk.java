package neontd.progress;

/** Dauerhafte Boni, die man im Shop mit Kristallen stufenweise ausbaut ("Forschung"). */
public enum Perk {
    START_MONEY("STARTGELD", "+40 Startgeld je Stufe", 10, 28, 2),
    LIVES("ZUSATZLEBEN", "+1 Leben je Stufe", 5, 90, 6),
    DISCOUNT("BAURABATT", "−1,5 % Baukosten je Stufe", 10, 40, 4),
    SELL("RESTWERT", "+1,5 % Verkaufswert je Stufe", 6, 30, 5),
    BOUNTY("PRÄMIEN", "+2 % Abschussprämien je Stufe", 10, 40, 8),
    WAVE_BONUS("WELLENBONUS", "+3 % Wellenbonus je Stufe", 10, 30, 8),
    DAMAGE("FEUERKRAFT", "+1 % Turmschaden je Stufe", 10, 70, 14),
    RANGE("ZIELOPTIK", "+1 % Reichweite je Stufe", 8, 50, 10),
    CRYSTALS("KRISTALLSUCHE", "+3 % Kristalle aus dem Spiel je Stufe", 10, 80, 12),
    XP("ERFAHRUNG", "+3 % XP aus dem Spiel je Stufe", 10, 70, 12);

    public final String label;
    public final String desc;
    public final int maxLevel;
    public final int baseCost;
    /** Spielerlevel, ab dem der Bonus im Shop angeboten wird. */
    public final int needLevel;

    Perk(String label, String desc, int maxLevel, int baseCost, int needLevel) {
        this.label = label;
        this.desc = desc;
        this.maxLevel = maxLevel;
        this.baseCost = baseCost;
        this.needLevel = needLevel;
    }

    public String key() {
        return name().toLowerCase();
    }

    /** Preis, von {@code level} auf {@code level + 1} auszubauen (auf 5 gerundet). */
    public int cost(int level) {
        return Math.max(5, (int) (Math.round(baseCost * (1 + 0.55 * level) / 5.0) * 5));
    }

    /** Summe aller Preise der ersten {@code level} Stufen. */
    public int totalCost(int level) {
        int sum = 0;
        for (int l = 0; l < level; l++) {
            sum += cost(l);
        }
        return sum;
    }

    public static Perk byKey(String key) {
        for (Perk p : values()) {
            if (p.key().equals(key)) {
                return p;
            }
        }
        return null;
    }
}

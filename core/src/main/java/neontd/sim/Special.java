package neontd.sim;

/**
 * Spezial-Upgrades: Jeder Turm hat einen Spezial-Platz. Welche Spezials zur Wahl stehen, bestimmt der Spieler im
 * Shop des Hauptmenüs (Kristalle); im Spiel wird eines davon gegen Geld in den Turm eingebaut und lässt sich später
 * gegen ein anderes tauschen. Die Wirkung steckt in {@code World} (Schadensberechnung und Treffer-Effekte).
 */
public enum Special {
    CRIT("crit", "VOLLTREFFER", "18 % Chance auf 2,5-fachen Schaden", 0xFFD60A, 500, 5),
    OVERCLOCK("overclock", "ÜBERTAKTUNG", "+25 % Feuerrate", 0xFF9E3D, 600, 8),
    LONGSHOT("longshot", "FERNGLAS", "+30 % Reichweite", 0x4DA6FF, 450, 6),
    PIERCE("pierce", "DURCHSCHLAG", "Geschosse und Blitze treffen 2 Ziele mehr", 0x00E5FF, 800, 12),
    BURN("burn", "BRANDSATZ", "Treffer setzen Gegner 3 s lang in Brand", 0xFF5A1F, 750, 10),
    CHILL("chill", "KÄLTEKERN", "Treffer verlangsamen den Gegner", 0x7DA2FF, 700, 10),
    SPLASH("splash", "SPLITTERHÜLLE", "Treffer streuen 30 % Schaden in der Umgebung", 0xFF8A00, 900, 14),
    BOUNTY("bounty", "KOPFGELD", "+50 % Prämie für Abschüsse dieses Turms", 0x39FF88, 850, 16),
    EXECUTE("execute", "HENKER", "Doppelter Schaden gegen Gegner unter 30 % Leben", 0xFF3355, 1000, 18),
    VULN("vuln", "RÜSTUNGSBRUCH", "Ziele nehmen 3 s lang 25 % mehr Schaden", 0xFF3DCB, 1100, 22),
    BOSSBANE("bossbane", "TITANJÄGER", "1,75-facher Schaden gegen Titanen, Kolosse und Panzer", 0xB6FF3B, 1100, 26),
    STUN("stun", "SCHOCKFELD", "12 % Chance, den Gegner 0,7 s zu betäuben", 0x9B7DFF, 1300, 30),
    INTEREST("interest", "ZINSEN", "Jede besiegte Welle bringt 4 % des investierten Geldes", 0xFFC857, 1400, 34),
    AFTERSHOCK("aftershock", "NACHBEBEN", "Abschüsse lösen eine Explosion aus", 0xFF6B6B, 1500, 38),
    KNOCKBACK("knockback", "RÜCKSTOSS", "Treffer werfen Gegner ein Stück zurück", 0x8DF0FF, 1800, 42),
    AURA("aura", "KOMMANDANT", "+15 % Schaden für alle Türme im Umkreis", 0xFFFFFF, 2200, 48);

    public static final double CRIT_CHANCE = 0.18;
    public static final double CRIT_MULT = 2.5;
    public static final double OVERCLOCK_SPEED = 1.25;
    public static final double LONGSHOT_RANGE = 1.30;
    public static final int PIERCE_BONUS = 2;
    public static final double BURN_DPS = 0.45;
    public static final double BURN_TIME = 3.0;
    public static final double CHILL_FACTOR = 0.6;
    public static final double CHILL_TIME = 1.6;
    public static final double SPLASH_FRACTION = 0.30;
    public static final double SPLASH_RADIUS = 58;
    public static final double BOUNTY_BONUS = 0.5;
    public static final double EXECUTE_BELOW = 0.30;
    public static final double EXECUTE_MULT = 2.0;
    public static final double VULN_BONUS = 0.25;
    public static final double VULN_TIME = 3.0;
    public static final double BOSSBANE_MULT = 1.75;
    public static final double STUN_CHANCE = 0.12;
    public static final double STUN_TIME = 0.7;
    public static final double INTEREST_RATE = 0.04;
    public static final double AFTERSHOCK_FRACTION = 0.5;
    public static final double AFTERSHOCK_RADIUS = 80;
    public static final double KNOCKBACK_DIST = 18;
    public static final double AURA_BONUS = 0.15;
    public static final double AURA_RADIUS = 150;

    /** Stabile Kennung für Speicherstände. */
    public final String key;
    public final String label;
    public final String desc;
    public final int color;
    /** Preis im Shop (Kristalle) und Spielerlevel, ab dem er dort angeboten wird. */
    public final int price;
    public final int needLevel;

    Special(String key, String label, String desc, int color, int price, int needLevel) {
        this.key = key;
        this.label = label;
        this.desc = desc;
        this.color = color;
        this.price = price;
        this.needLevel = needLevel;
    }

    /** Darf der Turmtyp dieses Spezial tragen? Die Hilfstürme (Münze, Kommando) nur die wirtschaftlichen. */
    public boolean allowedFor(TowerType type) {
        if (type.passive) {
            return this == INTEREST;
        }
        if (this == PIERCE) {
            switch (type) {
                case PULSE:
                case SALVE:
                case SHREDDER:
                case ARC:
                case TESLA:
                    return true;
                default:
                    return false;
            }
        }
        return true;
    }

    public static Special byKey(String key) {
        for (Special s : values()) {
            if (s.key.equals(key)) {
                return s;
            }
        }
        return null;
    }

    /** Einbaupreis im Spiel (Geld): das 1,3-fache des Turmpreises, mindestens 150. */
    public static int buildCost(TowerType type) {
        return Math.max(150, (int) Math.round(type.cost * 1.3 / 10.0) * 10);
    }
}

package neontd.sim;

/**
 * Ausbaupfade: Jeder Grundturm hat zwei Spezialisierungen mit je zwei Rängen. Der erste Rang steht ab
 * {@link #NEED_LEVELS_1} gekauften Upgrade-Stufen offen, der zweite ab {@link #NEED_LEVELS_2}; wer einen Pfad
 * wählt, schließt den anderen für diesen Turm aus. Die Wirkung steckt in {@link World} und {@link Tower}.
 */
public enum Branch {
    // PULS
    PULSE_A(TowerType.PULSE, "ZWILLING", "Zwei Kugeln je Schuss", "Drei Kugeln je Schuss", 0x8DF0FF),
    PULSE_B(TowerType.PULSE, "HOHLSPITZ", "+25 % Schaden, Kugeln treffen ein Ziel mehr", "20 % Chance auf doppelten Schaden", 0xFFD60A),
    // SNIPER
    SNIPER_A(TowerType.SNIPER, "DURCHSCHUSS", "Der Strahl trifft auch den nächsten Gegner dahinter",
            "Der Strahl trifft alle Gegner auf der Linie", 0xFF8AE2),
    SNIPER_B(TowerType.SNIPER, "KOPFSCHUSS", "+60 % Schaden gegen Gegner über 60 % Leben",
            "Dazu doppelter Schaden gegen Gegner unter 25 % Leben", 0xFF3355),
    // MÖRSER
    MORTAR_A(TowerType.MORTAR, "STREUBOMBE", "+45 % Explosionsradius", "Drei Splitterbomben um den Einschlag", 0xFFB347),
    MORTAR_B(TowerType.MORTAR, "BRANDGRANATE", "Einschläge setzen Gegner in Brand", "Das Feuer brennt doppelt so stark", 0xFF5A1F),
    // FROST
    FROST_A(TowerType.FROST, "FROSTKERN", "Verlangsamt deutlich stärker", "25 % Chance, Gegner 0,6 s einzufrieren", 0xB8E6FF),
    FROST_B(TowerType.FROST, "KRISTALL", "+25 % Reichweite", "Verlangsamte Gegner nehmen 20 % mehr Schaden", 0x7DA2FF),
    // BLITZ
    ARC_A(TowerType.ARC, "KETTE", "Zwei Sprünge mehr", "Vier Sprünge mehr, kaum Verlust je Sprung", 0xD4FF8A),
    ARC_B(TowerType.ARC, "ÜBERSPANNUNG", "15 % Chance, Gegner 0,5 s zu betäuben", "Jeder Sprung macht 10 % mehr Schaden", 0xB6FF3B),
    // SALVE
    SALVE_A(TowerType.SALVE, "TROMMELFEUER", "+25 % Feuerrate", "+50 % Feuerrate", 0xFF9BAE),
    SALVE_B(TowerType.SALVE, "SPLITTER", "Treffer streuen 30 % Schaden in die Umgebung", "Kugeln durchschlagen zwei Gegner mehr", 0xFF5470),
    // TESLA
    TESLA_A(TowerType.TESLA, "LICHTBOGEN", "Sprünge reichen deutlich weiter", "Getroffene Gegner brennen 2 s", 0xC9B8FF),
    TESLA_B(TowerType.TESLA, "KONDENSATOR", "Jeder vierte Schlag mit 2,5-fachem Schaden", "Jeder dritte Schlag mit 3-fachem Schaden", 0x9B7DFF),
    // RAILGUN
    RAIL_A(TowerType.RAILGUN, "HYPERSTRAHL", "Treffer werfen Gegner zurück", "Doppelt so breiter Strahl", 0xFFF0A0),
    RAIL_B(TowerType.RAILGUN, "DURCHDRINGUNG", "Ignoriert Panzerung und Schilde", "Treffer: 25 % mehr Schaden von allen Türmen (3 s)", 0xFFD60A);

    public static final int NEED_LEVELS_1 = 3;
    public static final int NEED_LEVELS_2 = 7;
    public static final double COST_1 = 1.5;
    public static final double COST_2 = 2.5;

    public final TowerType tower;
    public final String label;
    public final String desc1;
    public final String desc2;
    public final int color;

    Branch(TowerType tower, String label, String desc1, String desc2, int color) {
        this.tower = tower;
        this.label = label;
        this.desc1 = desc1;
        this.desc2 = desc2;
        this.color = color;
    }

    /** 1 = erster Pfad (A), 2 = zweiter (B). */
    public int side() {
        return ordinal() % 2 + 1;
    }

    public String desc(int rank) {
        return rank <= 1 ? desc1 : desc2;
    }

    /** Preis des Rangs {@code rank} (1 oder 2) für diesen Turmtyp. */
    public static int cost(TowerType t, int rank) {
        double c = t.cost * (rank <= 1 ? COST_1 : COST_2);
        return (int) (Math.round(c / 10.0) * 10);
    }

    /** Die beiden Pfade eines Turmtyps oder ein leeres Feld (Elite-Türme haben keine). */
    public static Branch[] of(TowerType t) {
        Branch a = null;
        Branch b = null;
        for (Branch br : values()) {
            if (br.tower == t) {
                if (a == null) {
                    a = br;
                } else {
                    b = br;
                }
            }
        }
        return a == null ? new Branch[0] : new Branch[] {a, b};
    }

    /** Der gewählte Pfad eines Turms oder {@code null}. */
    public static Branch chosen(Tower t) {
        if (t.branch <= 0) {
            return null;
        }
        Branch[] both = of(t.type);
        return both.length == 0 ? null : both[Math.min(both.length - 1, t.branch - 1)];
    }

    /** Wie viele Upgrade-Stufen für den Rang nötig sind. */
    public static int needLevels(int rank) {
        return rank <= 1 ? NEED_LEVELS_1 : NEED_LEVELS_2;
    }
}

package neontd.progress;

import neontd.ui.Fmt;

/**
 * Errungenschaften: Familien mit mehreren Stufen (z. B. "Besiege 100 / 1K / 10K … Gegner"). Jede erreichte Stufe
 * schreibt sofort Kristalle gut. Der Stand je Familie steht im Profil; hier stehen nur die Definitionen.
 */
public final class Achievements {
    /** Was gezählt wird; {@link Progress#achievementValue} liefert den aktuellen Wert. */
    public enum Kind {
        KILLS, WAVES, BOSSES, ELITES, BUILDS, UPGRADES, SPECIALS_BUILT, WINS, MEDALS, PLATINUM, CHAPTERS,
        BEST_ENDLESS, PLAYER_LEVEL, TOWERS_OWNED, SPECIALS_OWNED, PERK_LEVELS, DAILIES, SEASON_TIERS, CRYSTALS
    }

    /** Eine Familie mit ihren Stufen. */
    public static final class Family {
        public final Kind kind;
        public final String name;
        /** Text mit {@code {n}} als Platzhalter für das Ziel der Stufe. */
        public final String text;
        public final int color;
        public final int[] targets;
        public final int[] rewards;

        Family(Kind kind, String name, String text, int color, int[] targets, int[] rewards) {
            this.kind = kind;
            this.name = name;
            this.text = text;
            this.color = color;
            this.targets = targets;
            this.rewards = rewards;
        }

        public int tiers() {
            return targets.length;
        }

        public String id(int tier) {
            return kind.name().toLowerCase() + "_" + tier;
        }

        public String describe(int tier) {
            return text.replace("{n}", Fmt.compact(targets[tier]));
        }
    }

    public static final Family[] FAMILIES = {
        fam(Kind.KILLS, "ABSCHUSS-SERIE", "Besiege {n} Gegner", 0xFF4D5E,
                new int[] {100, 1000, 10000, 100000, 1000000}, new int[] {10, 25, 60, 150, 400}),
        fam(Kind.WAVES, "WELLENBRECHER", "Besiege {n} Wellen", 0x00E5FF,
                new int[] {50, 250, 1000, 5000, 20000}, new int[] {10, 25, 60, 150, 400}),
        fam(Kind.BOSSES, "TITANENTÖTER", "Besiege {n} Titanen", 0xFF3DCB,
                new int[] {5, 50, 250, 1000}, new int[] {15, 40, 100, 250}),
        fam(Kind.ELITES, "ELITEJÄGER", "Besiege {n} Elite-Gegner", 0xB6FF3B,
                new int[] {50, 500, 5000, 25000}, new int[] {15, 40, 100, 250}),
        fam(Kind.BUILDS, "BAUMEISTER", "Baue {n} Türme", 0xFF8A00,
                new int[] {25, 250, 2500, 10000}, new int[] {10, 30, 80, 200}),
        fam(Kind.UPGRADES, "AUFRÜSTER", "Kaufe {n} Upgrades", 0xFFD60A,
                new int[] {50, 500, 5000, 20000}, new int[] {10, 30, 80, 200}),
        fam(Kind.SPECIALS_BUILT, "SPEZIALIST", "Baue {n} Spezial-Upgrades ein", 0x9B7DFF,
                new int[] {5, 50, 500}, new int[] {20, 60, 150}),
        fam(Kind.WINS, "SIEGER", "Gewinne {n} Level", 0x39FF88,
                new int[] {1, 10, 50, 150}, new int[] {10, 30, 80, 200}),
        fam(Kind.MEDALS, "MEDAILLENSAMMLER", "Sammle {n} Medaillen", 0xE08A3C,
                new int[] {5, 25, 100, 300, 600}, new int[] {15, 40, 100, 250, 500}),
        fam(Kind.PLATINUM, "PLATIN-JÄGER", "Erreiche {n} Platin-Medaillen", 0x6FF3FF,
                new int[] {1, 5, 20, 50}, new int[] {40, 100, 250, 600}),
        fam(Kind.CHAPTERS, "KAPITELMEISTER", "Schließe {n} Kapitel ab (alle 15 Level mit Bronze)", 0x4DA6FF,
                new int[] {1, 3, 6, 10}, new int[] {50, 120, 250, 500}),
        fam(Kind.BEST_ENDLESS, "ENDLOS-LEGENDE", "Erreiche Welle {n} im Endlosmodus", 0xFF5470,
                new int[] {50, 100, 250, 500, 1000}, new int[] {20, 50, 120, 300, 800}),
        fam(Kind.PLAYER_LEVEL, "AUFSTEIGER", "Erreiche Spielerlevel {n}", 0xFFFFFF,
                new int[] {5, 10, 25, 50, 100}, new int[] {10, 30, 100, 250, 600}),
        fam(Kind.TOWERS_OWNED, "WAFFENKAMMER", "Besitze {n} Elite-Türme", 0xFF5A1F,
                new int[] {1, 4, 8}, new int[] {30, 100, 300}),
        fam(Kind.SPECIALS_OWNED, "TÜFTLER", "Besitze {n} Spezial-Upgrades", 0xF4A6FF,
                new int[] {1, 8, 16}, new int[] {30, 100, 300}),
        fam(Kind.PERK_LEVELS, "FORSCHER", "Erforsche {n} Stufen im Shop", 0x3DFF9A,
                new int[] {10, 40, 80}, new int[] {30, 100, 300}),
        fam(Kind.DAILIES, "TREUER SPIELER", "Schließe {n} Tagesaufgaben ab", 0xFFC857,
                new int[] {3, 30, 100, 300}, new int[] {15, 50, 150, 400}),
        fam(Kind.SEASON_TIERS, "SEASON-HELD", "Hole {n} Season-Stufen ab", 0x8DF0FF,
                new int[] {10, 60, 180}, new int[] {30, 100, 300}),
        fam(Kind.CRYSTALS, "KRISTALLSAMMLER", "Verdiene {n} Kristalle", 0xE6F7FF,
                new int[] {500, 5000, 25000, 50000}, new int[] {25, 100, 300, 600}),
    };

    private Achievements() {
    }

    private static Family fam(Kind kind, String name, String text, int color, int[] targets, int[] rewards) {
        return new Family(kind, name, text, color, targets, rewards);
    }

    /** Anzahl aller Stufen über alle Familien. */
    public static int totalTiers() {
        int n = 0;
        for (Family f : FAMILIES) {
            n += f.tiers();
        }
        return n;
    }

    /** Alle Kristalle, die alle Stufen zusammen bringen. */
    public static int totalCrystals() {
        int n = 0;
        for (Family f : FAMILIES) {
            for (int r : f.rewards) {
                n += r;
            }
        }
        return n;
    }
}

package neontd.progress;

/**
 * Der Season-Pass: eine Season dauert einen Monat. Season-XP gibt es für Tages- und Monatsaufgaben und für besiegte
 * Wellen; jede volle Stufe (150 XP) schaltet eine Belohnung frei, die abgeholt werden muss. Alles ist kostenlos.
 */
public final class Season {
    public static final int TIERS = 30;
    public static final int XP_PER_TIER = 150;
    public static final int MAX_XP = TIERS * XP_PER_TIER;
    /** Je zwei besiegte Wellen gibt es 1 Season-XP. */
    public static final int WAVES_PER_XP = 2;

    private static final String[] TITLES = {"", "SEASON-STARTER", "SEASON-VETERAN", "SEASON-MEISTER"};

    private Season() {
    }

    /** Kristalle der Stufe {@code tier} (1–30). */
    public static int crystals(int tier) {
        int base = 30 + 4 * tier;
        if (tier == 10) {
            base += 150;
        } else if (tier == 20) {
            base += 250;
        } else if (tier == TIERS) {
            base += 500;
        }
        return base;
    }

    /** Meilensteine mit einem Titel (Stufe 10, 20, 30), sonst {@code null}. */
    public static String milestone(int tier) {
        return tier == 10 ? TITLES[1] : tier == 20 ? TITLES[2] : tier == TIERS ? TITLES[3] : null;
    }

    /** Alle Kristalle einer voll abgeholten Season. */
    public static int totalCrystals() {
        int sum = 0;
        for (int t = 1; t <= TIERS; t++) {
            sum += crystals(t);
        }
        return sum;
    }

    public static int tierFor(int seasonXp) {
        return Math.min(TIERS, Math.max(0, seasonXp) / XP_PER_TIER);
    }
}

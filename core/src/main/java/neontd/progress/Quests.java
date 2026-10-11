package neontd.progress;

/**
 * Das Aufgaben-Angebot: jeden Tag drei Aufgaben (leicht, mittel, schwer) und jeden Monat sechs große. Die Auswahl
 * ergibt sich rein aus Datum und Monat – alle Geräte sehen dieselben Aufgaben, ohne dass etwas gespeichert werden muss.
 */
public final class Quests {
    public static final int DAILY = 3;
    public static final int MONTHLY = 6;
    /** Zusatzbelohnung, wenn alle drei Tagesaufgaben abgeholt sind. */
    public static final int DAILY_BONUS_CRYSTALS = 30;
    public static final int DAILY_BONUS_SEASON_XP = 40;

    private static final Metric[] EASY_M = {Metric.KILLS, Metric.WAVES, Metric.BUILDS, Metric.UPGRADES, Metric.PLAYS};
    private static final int[] EASY_T = {150, 10, 12, 15, 2};
    private static final Metric[] MID_M = {Metric.KILLS, Metric.WAVES, Metric.UPGRADES, Metric.ELITES, Metric.BUILDS,
        Metric.WINS};
    private static final int[] MID_T = {400, 22, 30, 15, 30, 1};
    private static final Metric[] HARD_M = {Metric.KILLS, Metric.WAVES, Metric.ELITES, Metric.BOSSES,
        Metric.SPECIALS, Metric.ENDLESS_WAVES, Metric.WINS};
    private static final int[] HARD_T = {900, 40, 40, 3, 3, 30, 2};
    private static final int[] DAILY_CRYSTALS = {25, 40, 65};
    private static final int[] DAILY_XP = {100, 200, 350};
    private static final int DAILY_SEASON_XP = 40;

    private static final Metric[] MONTH_M = {Metric.KILLS, Metric.WAVES, Metric.ENDLESS_WAVES, Metric.BOSSES,
        Metric.ELITES, Metric.BUILDS, Metric.UPGRADES, Metric.SPECIALS, Metric.WINS, Metric.PLAYS};
    private static final int[] MONTH_T = {6000, 500, 300, 50, 800, 600, 900, 60, 25, 60};

    private Quests() {
    }

    private static int hash(int a, int b) {
        int h = a * 0x2C1B3C6D + b * 0x297A2D39 + 0x6D2B79F5;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return h & 0x7FFFFFFF;
    }

    /** Die drei Aufgaben eines Tages (Tageszahl seit 1970). */
    public static Quest[] daily(int day) {
        Quest[] q = new Quest[DAILY];
        for (int tier = 0; tier < DAILY; tier++) {
            Metric[] ms = tier == 0 ? EASY_M : tier == 1 ? MID_M : HARD_M;
            int[] ts = tier == 0 ? EASY_T : tier == 1 ? MID_T : HARD_T;
            int pick = hash(day, tier + 1) % ms.length;
            // Verschiedene Metriken an einem Tag: bei Gleichstand mit der vorigen Aufgabe die nächste nehmen.
            for (int i = 0; i < tier; i++) {
                if (q[i].metric == ms[pick]) {
                    pick = (pick + 1) % ms.length;
                    i = -1;
                }
            }
            q[tier] = new Quest("d" + tier + ms[pick].key() + ts[pick], ms[pick], ts[pick], DAILY_CRYSTALS[tier],
                    DAILY_XP[tier], DAILY_SEASON_XP);
        }
        return q;
    }

    /** Die sechs Monatsaufgaben einer Season (Monats-Kennung, siehe {@link Calendar#monthId}). */
    public static Quest[] monthly(int monthId) {
        Quest[] q = new Quest[MONTHLY];
        boolean[] used = new boolean[MONTH_M.length];
        for (int i = 0; i < MONTHLY; i++) {
            int pick = hash(monthId, 100 + i) % MONTH_M.length;
            while (used[pick]) {
                pick = (pick + 1) % MONTH_M.length;
            }
            used[pick] = true;
            // Ziel schwankt von Monat zu Monat leicht (±20 %), damit nicht jeder Monat gleich aussieht.
            double jitter = 0.8 + 0.4 * (hash(monthId, 200 + i) % 1000) / 1000.0;
            int target = Math.max(5, (int) (Math.round(MONTH_T[pick] * jitter / 5.0) * 5));
            int crystals = 100 + 20 * (i % 3);
            q[i] = new Quest("m" + monthId + "_" + MONTH_M[pick].key(), MONTH_M[pick], target, crystals, 500, 200);
        }
        return q;
    }
}

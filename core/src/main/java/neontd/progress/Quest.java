package neontd.progress;

/** Eine Aufgabe (täglich oder monatlich): Ziel in einer {@link Metric} und die Belohnung. */
public final class Quest {
    public final String id;
    public final Metric metric;
    public final int target;
    public final int crystals;
    public final int xp;
    public final int seasonXp;

    public Quest(String id, Metric metric, int target, int crystals, int xp, int seasonXp) {
        this.id = id;
        this.metric = metric;
        this.target = target;
        this.crystals = crystals;
        this.xp = xp;
        this.seasonXp = seasonXp;
    }

    /** Beschreibung wie "Besiege 400 Gegner". */
    public String text() {
        switch (metric) {
            case KILLS: return "Besiege " + target + " Gegner";
            case WAVES: return "Besiege " + target + " Wellen";
            case ENDLESS_WAVES: return "Besiege " + target + " Endlos-Wellen";
            case BOSSES: return "Besiege " + target + (target == 1 ? " Titan" : " Titanen");
            case ELITES: return "Besiege " + target + " Elite-Gegner";
            case BUILDS: return "Baue " + target + " Türme";
            case UPGRADES: return "Kaufe " + target + " Upgrades";
            case SPECIALS: return "Baue " + target + (target == 1 ? " Spezial-Upgrade" : " Spezial-Upgrades") + " ein";
            case WINS: return "Gewinne " + target + (target == 1 ? " Level" : " Level");
            default: return "Starte " + target + (target == 1 ? " Level" : " Level");
        }
    }
}

package neontd.progress;

/** Zählbare Ereignisse, auf die Aufgaben und Errungenschaften schauen. */
public enum Metric {
    KILLS("Gegner besiegen"),
    WAVES("Wellen besiegen"),
    ENDLESS_WAVES("Endlos-Wellen besiegen"),
    BOSSES("Titanen besiegen"),
    ELITES("Elite-Gegner besiegen"),
    BUILDS("Türme bauen"),
    UPGRADES("Upgrades kaufen"),
    SPECIALS("Spezial-Upgrades einbauen"),
    WINS("Level gewinnen"),
    PLAYS("Level starten");

    public final String text;

    Metric(String text) {
        this.text = text;
    }

    /** Zählername im Profil. */
    public String key() {
        return name().toLowerCase();
    }
}

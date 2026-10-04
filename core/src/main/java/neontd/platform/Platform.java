package neontd.platform;

/** Alles, was das Spiel von der Umgebung (Browser, Desktop) braucht. */
public interface Platform {
    /** Dauerhafter Speicher für eigene Level. */
    KeyValueStore store();

    /** Wird vorrangig mit dem Finger bedient (Hinweis für Größen und Hilfetexte). */
    boolean touchPrimary();

    /** Kann die Anwendung beendet werden (nur Desktop)? */
    default boolean canQuit() {
        return false;
    }

    default void quit() {
    }
}

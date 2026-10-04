package neontd.platform;

import java.util.function.Consumer;
import neontd.save.Http;

/** Alles, was das Spiel von der Umgebung (Browser, Desktop) braucht. */
public interface Platform {
    /** Dauerhafter Speicher für eigene Level, Profil und Spielstände. */
    KeyValueStore store();

    /** Wird vorrangig mit dem Finger bedient (Hinweis für Größen und Hilfetexte). */
    boolean touchPrimary();

    /** Kann die Anwendung beendet werden (nur Desktop)? */
    default boolean canQuit() {
        return false;
    }

    default void quit() {
    }

    /** Aktuelle Uhrzeit in Millisekunden seit 1970 (für Zeitstempel der Spielstände). */
    default double nowMillis() {
        return 0;
    }

    /** Netzwerkzugriff für den Cloud-Speicher oder {@code null}, wenn die Plattform keinen anbietet. */
    default Http http() {
        return null;
    }

    /** Fragt Text ab (Name, Token, Code). {@code result} bekommt den Text oder {@code null} bei Abbruch. */
    default void prompt(String title, String initial, Consumer<String> result) {
        result.accept(null);
    }

    /** Kopiert Text in die Zwischenablage; {@code done} meldet, ob es geklappt hat. */
    default void copyText(String text, Consumer<Boolean> done) {
        done.accept(false);
    }

    /** Öffnet eine Internetadresse im Browser (z. B. die GitHub-Seite zum Erstellen eines Tokens). */
    default void openUrl(String url) {
    }

    /** Bittet den Browser, den Speicher dauerhaft zu halten (verhindert das Aufräumen durch das System). */
    default void requestPersistentStorage() {
    }
}

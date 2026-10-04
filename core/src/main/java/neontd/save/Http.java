package neontd.save;

/**
 * Minimale, asynchrone HTTP-Schnittstelle (Browser: {@code fetch}, Desktop: {@code java.net.http}). Die Antwort
 * kommt immer im Haupt-Thread des Spiels an.
 */
public interface Http {
    /** Antwort: {@code status} 0 = kein Netz/Fehler (dann steht die Meldung in {@code body}). */
    interface Callback {
        void done(int status, String body);
    }

    /**
     * @param method  GET, POST, PATCH …
     * @param headers abwechselnd Name, Wert
     * @param body    Anfragetext oder {@code null}
     */
    void request(String method, String url, String[] headers, String body, Callback cb);
}

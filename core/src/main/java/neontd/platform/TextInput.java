package neontd.platform;

/**
 * Ein echtes Texteingabefeld der Plattform über dem Spielbild (im Browser ein {@code <input>}, damit auf dem iPhone
 * die Tastatur, Autokorrektur-Leiste und das Einfügen wie gewohnt funktionieren). Das Spiel zeichnet Rahmen und
 * Hintergrund selbst und legt das Feld genau darüber.
 */
public interface TextInput {
    /** Lage in logischen Pixeln (wie der Viewport), Schriftgröße in Pixeln und Deckkraft 0–1. */
    void place(double x, double y, double w, double h, double fontSize, double alpha);

    String text();

    void setText(String text);

    /** Holt den Fokus (auf dem iPhone öffnet sich die Tastatur nur nach einer Berührung des Felds). */
    void focus();

    /** Gibt den Fokus ab (Tastatur schließt). */
    void blur();

    /** Entfernt das Feld. */
    void dispose();
}

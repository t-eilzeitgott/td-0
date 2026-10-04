package neontd.gfx;

/**
 * Minimale Vektor-Zeichen-API. Genau das, was das Spiel braucht – und was sich auf Canvas2D (Browser, iPhone) und
 * Java2D (Desktop) gleichermaßen umsetzen lässt. Koordinaten sind logische Pixel (CSS-Pixel), y zeigt nach unten.
 *
 * <p>Farben sind {@code 0xAARRGGBB}. Ein Alpha-Byte von 0 gilt als "voll deckend", damit einfache Literale wie
 * {@code 0x00E5FF} funktionieren; für Transparenz dient {@link Colors#withAlpha(int, double)}.
 *
 * <p>Implementierungen müssen nur die abstrakten Methoden liefern; alles Weitere ist hier auf den Pfad-Primitiven
 * aufgebaut (und darf für Geschwindigkeit überschrieben werden).
 */
public interface Gfx {
    int ALIGN_LEFT = 0;
    int ALIGN_CENTER = 1;
    int ALIGN_RIGHT = 2;

    double width();

    double height();

    // ------------------------------------------------------------------------------------------ Zustand

    /** Sichert Transformation, Alpha, Blendmodus und Clip. */
    void save();

    void restore();

    void translate(double x, double y);

    void rotate(double radians);

    void scale(double sx, double sy);

    /** Multipliziert die globale Deckkraft (bis zum nächsten {@link #restore()}). */
    void alpha(double a);

    /** Additives Mischen ("Licht addiert sich") ein- oder ausschalten – für Glühen und Feuerwerk. */
    void additive(boolean on);

    void clipRect(double x, double y, double w, double h);

    // -------------------------------------------------------------------------------------------- Pfade

    void beginPath();

    void moveTo(double x, double y);

    void lineTo(double x, double y);

    /** Kreisbogen im Uhrzeigersinn von a0 nach a1 (Radiant); verbindet mit dem aktuellen Punkt. */
    void arc(double cx, double cy, double r, double a0, double a1);

    void closePath();

    void fill(int color);

    void stroke(double width, int color);

    /** Gestrichelte Linie; phase verschiebt das Muster (für laufende Animationen). */
    void strokeDashed(double width, int color, double dash, double gap, double phase);

    /** Weicher Lichtfleck: in der Mitte {@code color}, nach außen auf transparent auslaufend. */
    void radialGlow(double cx, double cy, double r, int color);

    // ------------------------------------------------------------------------------------------- Text

    /** Text mit vertikal zentrierter Grundlinie bei y. */
    void text(String s, double x, double y, double size, int color, int align, boolean bold);

    double textWidth(String s, double size, boolean bold);

    // ------------------------------------------------------------------------ Bequemlichkeit (Standard)

    default void rect(double x, double y, double w, double h) {
        moveTo(x, y);
        lineTo(x + w, y);
        lineTo(x + w, y + h);
        lineTo(x, y + h);
        closePath();
    }

    default void roundRect(double x, double y, double w, double h, double r) {
        r = Math.max(0, Math.min(r, Math.min(w, h) * 0.5));
        moveTo(x + r, y);
        lineTo(x + w - r, y);
        arc(x + w - r, y + r, r, -Math.PI / 2, 0);
        lineTo(x + w, y + h - r);
        arc(x + w - r, y + h - r, r, 0, Math.PI / 2);
        lineTo(x + r, y + h);
        arc(x + r, y + h - r, r, Math.PI / 2, Math.PI);
        lineTo(x, y + r);
        arc(x + r, y + r, r, Math.PI, Math.PI * 1.5);
        closePath();
    }

    default void circle(double cx, double cy, double r) {
        moveTo(cx + r, cy);
        arc(cx, cy, r, 0, Math.PI * 2);
        closePath();
    }

    /** Regelmäßiges Vieleck als Pfad. */
    default void ngon(double cx, double cy, double r, int sides, double rotation) {
        for (int i = 0; i < sides; i++) {
            double a = rotation + Math.PI * 2 * i / sides;
            double px = cx + Math.cos(a) * r;
            double py = cy + Math.sin(a) * r;
            if (i == 0) {
                moveTo(px, py);
            } else {
                lineTo(px, py);
            }
        }
        closePath();
    }

    default void fillRect(double x, double y, double w, double h, int color) {
        beginPath();
        rect(x, y, w, h);
        fill(color);
    }

    default void strokeRect(double x, double y, double w, double h, double lw, int color) {
        beginPath();
        rect(x, y, w, h);
        stroke(lw, color);
    }

    default void fillRoundRect(double x, double y, double w, double h, double r, int color) {
        beginPath();
        roundRect(x, y, w, h, r);
        fill(color);
    }

    default void strokeRoundRect(double x, double y, double w, double h, double r, double lw, int color) {
        beginPath();
        roundRect(x, y, w, h, r);
        stroke(lw, color);
    }

    default void fillCircle(double cx, double cy, double r, int color) {
        beginPath();
        circle(cx, cy, r);
        fill(color);
    }

    default void strokeCircle(double cx, double cy, double r, double lw, int color) {
        beginPath();
        circle(cx, cy, r);
        stroke(lw, color);
    }

    default void line(double x1, double y1, double x2, double y2, double lw, int color) {
        beginPath();
        moveTo(x1, y1);
        lineTo(x2, y2);
        stroke(lw, color);
    }
}

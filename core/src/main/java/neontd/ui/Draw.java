package neontd.ui;

import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Theme;
import neontd.math.Mathx;

/**
 * Zeichenhelfer für die Oberfläche im cleanen Stil: dunkle Flächen mit feiner Linie, Systemschrift, kein Leuchten.
 * Das Neon bleibt dem Spielfeld, den Türmen, Gegnern und der Wortmarke vorbehalten.
 */
public final class Draw {
    private Draw() {
    }

    /** Dezentes dunkles Panel mit farbigem Rand (ohne Leuchten) – ältere Verwendung. */
    public static void softPanel(Gfx g, double x, double y, double w, double h, double r, int accent) {
        g.fillRoundRect(x, y, w, h, r, Colors.withAlpha(Theme.SURFACE, 0.96));
        g.strokeRoundRect(x, y, w, h, r, 1, Colors.withAlpha(accent, 0.22));
    }

    /** Karte: Fläche mit feiner Linie. {@code lift} 0..1 hellt sie leicht auf (Hover/Druck). */
    public static void card(Gfx g, double x, double y, double w, double h, double r, double lift) {
        g.fillRoundRect(x, y, w, h, r, Colors.lerp(Theme.SURFACE, Theme.SURFACE2, Mathx.clamp01(lift)));
        g.strokeRoundRect(x, y, w, h, r, 1, Theme.LINE);
    }

    /** Karte mit farbigem Hauch (z. B. Welt-Akzent) am linken Rand und im Hintergrund. */
    public static void accentCard(Gfx g, double x, double y, double w, double h, double r, int accent, double lift) {
        card(g, x, y, w, h, r, lift);
        g.fillRoundRect(x, y, w, h, r, Colors.withAlpha(accent, 0.05 + 0.04 * lift));
    }

    /** Erhöhte Fläche (Schaltflächen, Chips). */
    public static void raised(Gfx g, double x, double y, double w, double h, double r, double lift) {
        g.fillRoundRect(x, y, w, h, r, Colors.lerp(Theme.SURFACE2, Theme.SURFACE3, Mathx.clamp01(lift)));
    }

    /** Dünne Trennlinie. */
    public static void divider(Gfx g, double x, double y, double w) {
        g.fillRect(x, y, w, 1, Theme.LINE);
    }

    /** Fortschrittsbalken mit rundem Ende. */
    public static void bar(Gfx g, double x, double y, double w, double h, double f, int color) {
        g.fillRoundRect(x, y, w, h, h / 2, Theme.SURFACE3);
        if (f > 0) {
            g.fillRoundRect(x, y, Math.max(h, w * Mathx.clamp01(f)), h, h / 2, color);
        }
    }

    /** Kleiner Pillen-Chip mit Symbol und Text. @return Breite */
    public static double chip(Gfx g, double x, double y, double h, Icons.Icon icon, String text, int color,
                              boolean filled) {
        double fs = h * 0.44;
        double tw = g.textWidth(text, fs, true);
        double pad = h * 0.4;
        double iw = icon != null ? h * 0.72 : 0;
        double w = pad * 2 + iw + tw;
        g.fillRoundRect(x, y, w, h, h / 2, filled ? Colors.withAlpha(color, 0.16) : Theme.SURFACE2);
        if (icon != null) {
            Icons.draw(g, icon, x + pad + h * 0.26, y + h / 2, h * 0.26, color, 0);
        }
        g.text(text, x + pad + iw, y + h / 2, fs, filled ? Colors.lighten(color, 0.35) : Theme.TEXT,
                Gfx.ALIGN_LEFT, true);
        return w;
    }

    /** Rechtsbündiger Chip. @return Breite */
    public static double chipRight(Gfx g, double right, double y, double h, Icons.Icon icon, String text, int color,
                                   boolean filled) {
        double fs = h * 0.44;
        double tw = g.textWidth(text, fs, true);
        double w = h * 0.8 + (icon != null ? h * 0.72 : 0) + tw;
        chip(g, right - w, y, h, icon, text, color, filled);
        return w;
    }

    /** Sternenreihe: {@code got} von {@code max} Sternen leuchten. */
    public static void stars(Gfx g, double cx, double cy, double size, int got, int max) {
        double step = size * 2.3;
        double x0 = cx - step * (max - 1) / 2;
        for (int i = 0; i < max; i++) {
            boolean on = i < got;
            star(g, x0 + i * step, cy, size, on ? Theme.STAR : Theme.STAR_OFF, on);
        }
    }

    /** Ein fünfzackiger Stern. */
    public static void star(Gfx g, double cx, double cy, double r, int color, boolean filled) {
        g.beginPath();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            double rr = i % 2 == 0 ? r : r * 0.46;
            double px = cx + Math.cos(a) * rr;
            double py = cy + Math.sin(a) * rr;
            if (i == 0) {
                g.moveTo(px, py);
            } else {
                g.lineTo(px, py);
            }
        }
        g.closePath();
        if (filled) {
            g.fill(color);
        } else {
            g.stroke(Math.max(1, r * 0.16), color);
        }
    }

    /** Rundes Symbol-Badge mit getönter Fläche. */
    public static void iconBadge(Gfx g, double cx, double cy, double r, Icons.Icon icon, int color) {
        g.fillCircle(cx, cy, r, Colors.withAlpha(color, 0.16));
        Icons.draw(g, icon, cx, cy, r * 0.5, color, 0);
    }

    /** Text, der bei Bedarf verkleinert wird, damit er in {@code maxW} passt. */
    public static void fit(Gfx g, String s, double x, double y, double size, double maxW, int color, int align,
                           boolean bold) {
        double w = g.textWidth(s, size, bold);
        double k = w > maxW ? maxW / Math.max(1, w) : 1;
        g.text(s, x, y, Math.max(8, size * k), color, align, bold);
    }

    /** Chevron „zurück“ (links) oder „weiter“ (rechts). */
    public static void chevron(Gfx g, double cx, double cy, double s, int color, boolean right) {
        g.beginPath();
        double d = right ? 1 : -1;
        g.moveTo(cx - d * s * 0.5, cy - s);
        g.lineTo(cx + d * s * 0.5, cy);
        g.lineTo(cx - d * s * 0.5, cy + s);
        g.stroke(Math.max(1.5, s * 0.22), color);
    }

    /** Schloss-Symbol in gedämpfter Farbe. */
    public static void lock(Gfx g, double cx, double cy, double s) {
        Icons.draw(g, Icons.Icon.LOCK, cx, cy, s, Theme.MUTED, 0);
    }
}

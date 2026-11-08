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

    /**
     * Schwebende Glasfläche über dem Spielfeld: weicher Schatten darunter, dunkle, leicht durchscheinende Fläche, eine
     * Haarlinie als Rand und ein Lichtsaum an der Oberkante. {@code lift} (0..1) hebt sie an: Schatten tiefer, Fläche
     * heller – so wirkt ein gedrücktes oder gewähltes Teil, als käme es dem Finger entgegen.
     */
    public static void glass(Gfx g, double x, double y, double w, double h, double r, double lift, double u) {
        glass(g, x, y, w, h, r, lift, u, 0.86);
    }

    /** Wie {@link #glass(Gfx, double, double, double, double, double, double, double)} mit eigener Deckkraft der Fläche. */
    public static void glass(Gfx g, double x, double y, double w, double h, double r, double lift, double u,
                             double opacity) {
        r = Math.max(0, Math.min(r, Math.min(w, h) * 0.5));
        double l = Mathx.clamp01(lift);
        double drop = (3 + 4 * l) * u;
        for (int i = 3; i >= 1; i--) {
            double s = i * (2.6 + 1.4 * l) * u;
            g.fillRoundRect(x - s, y - s + drop, w + 2 * s, h + 2 * s, r + s, Colors.withAlpha(0x000000, 0.075 + 0.02 * l));
        }
        g.fillRoundRect(x, y, w, h, r, Colors.withAlpha(Colors.lerp(0x101016, 0x1A1A22, l), Math.min(1, opacity + 0.06 * l)));
        g.strokeRoundRect(x + 0.5, y + 0.5, w - 1, h - 1, r, 1, Colors.withAlpha(0xFFFFFF, 0.075 + 0.05 * l));
        // Lichtsaum oben: eine kurze, helle Kante, die nach den Seiten ausläuft
        double inset = Math.max(r * 0.9, 6 * u);
        if (w > inset * 2 + 4) {
            g.fillRoundRect(x + inset, y + 0.6, w - inset * 2, 1.1, 0.55, Colors.withAlpha(0xFFFFFF, 0.09 + 0.06 * l));
        }
    }

    /**
     * Wie {@link #glass}, dazu ein Zeiger (kleines Dreieck in der Farbe {@code accent}), der auf {@code (px, py)} zeigt –
     * für Karten, die neben dem Turm schweben. {@code side}: 0 = Zeiger oben, 1 = unten, 2 = links, 3 = rechts.
     */
    public static void glassPointer(Gfx g, double x, double y, double w, double h, double r, double px, double py,
                                    int side, double size, double u, int accent) {
        double s = size;
        int fill = Colors.withAlpha(Colors.lerp(0x101016, accent, 0.10), 0.96);
        int edge = Colors.withAlpha(accent, 0.55);
        double ax;
        double ay;
        double bx;
        double by;
        double tx;
        double ty;
        if (side == 0 || side == 1) {
            double cx = Mathx.clamp(px, x + r + s, x + w - r - s);
            double base = side == 0 ? y + 1 : y + h - 1;
            ty = side == 0 ? Math.max(py, y - s * 1.3) : Math.min(py, y + h + s * 1.3);
            tx = Mathx.clamp(px, cx - s * 0.7, cx + s * 0.7);
            ax = cx - s;
            ay = base;
            bx = cx + s;
            by = base;
        } else {
            double cy = Mathx.clamp(py, y + r + s, y + h - r - s);
            double base = side == 2 ? x + 1 : x + w - 1;
            tx = side == 2 ? Math.max(px, x - s * 1.3) : Math.min(px, x + w + s * 1.3);
            ty = Mathx.clamp(py, cy - s * 0.7, cy + s * 0.7);
            ax = base;
            ay = cy - s;
            bx = base;
            by = cy + s;
        }
        // Schatten des Zeigers, dann Fläche, dann Zeiger in derselben Fläche mit feiner Kante
        g.beginPath();
        g.moveTo(ax, ay + 3 * u);
        g.lineTo(tx, ty + 3 * u);
        g.lineTo(bx, by + 3 * u);
        g.closePath();
        g.fill(Colors.withAlpha(0x000000, 0.18));
        glass(g, x, y, w, h, r, 0, u, 0.95);
        g.beginPath();
        g.moveTo(ax, ay);
        g.lineTo(tx, ty);
        g.lineTo(bx, by);
        g.closePath();
        g.fill(fill);
        g.beginPath();
        g.moveTo(ax, ay);
        g.lineTo(tx, ty);
        g.lineTo(bx, by);
        g.stroke(1.2, edge);
        g.radialGlow(tx, ty, s * 1.4, Colors.withAlpha(accent, 0.22));
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

    /** Text in voller Größe; was nicht in {@code maxW} passt, wird mit „…“ abgeschnitten. */
    public static void cut(Gfx g, String s, double x, double y, double size, double maxW, int color, int align,
                           boolean bold) {
        if (g.textWidth(s, size, bold) <= maxW) {
            g.text(s, x, y, size, color, align, bold);
            return;
        }
        int lo = 0;
        int hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (g.textWidth(s.substring(0, mid) + "…", size, bold) <= maxW) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        g.text(lo <= 0 ? "…" : s.substring(0, lo).trim() + "…", x, y, size, color, align, bold);
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

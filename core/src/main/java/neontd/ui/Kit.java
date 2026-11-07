package neontd.ui;

import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Theme;
import neontd.math.Mathx;

/**
 * Das Design-System der Menüs: eine Typo-Skala, ein Raster und wenige Bausteine, die überall gleich aussehen.
 *
 * <p>Grundsatz: Inhalt steht direkt auf Schwarz. Gliederung entsteht durch Größe, Gewicht, Weißraum und feine
 * Haarlinien – nicht durch Kästen. Flächen gibt es nur für Haupttasten und für den Druck-Zustand einer Zeile. Neon
 * bleibt den Spielinhalten (Karten, Türme, Gegner, Wortmarke) vorbehalten.
 *
 * <p>Alle Maße sind in Punkten bei {@code u = 1} angegeben und werden mit {@code u} multipliziert.
 */
public final class Kit {
    private Kit() {
    }

    // ------------------------------------------------------------------------------------------ Typo-Skala

    /** Großer Seitentitel. */
    public static final double T_TITLE = 30;
    /** Held-Titel (Level-Name auf der Startseite). */
    public static final double T_HERO = 34;
    /** Sektionsüberschrift. */
    public static final double T_SECTION = 21;
    /** Zeilentitel. */
    public static final double T_ROW = 17;
    /** Fließtext, Werte in Zeilen. */
    public static final double T_BODY = 15;
    /** Untertitel, Hinweise. */
    public static final double T_CAPTION = 13;
    /** Kicker über Titeln (Großbuchstaben, Akzentfarbe). */
    public static final double T_KICKER = 12;
    /** Große Kennzahl. */
    public static final double T_STAT = 30;

    // ---------------------------------------------------------------------------------------------- Raster

    /** Seitenrand. */
    public static final double MARGIN = 20;
    /** Abstand zwischen Sektionen. */
    public static final double SECTION_GAP = 36;
    /** Höhe einer Listenzeile mit Titel und Untertitel. */
    public static final double ROW = 68;
    /** Höhe einer einzeiligen Listenzeile. */
    public static final double ROW_SMALL = 54;
    /** Höhe der Haupttaste. */
    public static final double BUTTON = 56;

    // ------------------------------------------------------------------------------------------ Bausteine

    /** Kicker: kleine Großbuchstaben in Akzentfarbe, z. B. „WEITER · NEONSTADT“. */
    public static void kicker(Gfx g, String s, double x, double y, double u, int color) {
        g.text(s.toUpperCase(), x, y, T_KICKER * u, color, Gfx.ALIGN_LEFT, true);
    }

    /**
     * Sektionsüberschrift mit optionalem Wert rechts (gedämpft). {@code y} ist die Oberkante.
     *
     * @return verbrauchte Höhe inkl. Abstand zum Inhalt
     */
    public static double section(Gfx g, String title, String trailing, int trailingColor, double x, double y, double w,
                                 double u) {
        double cy = y + 14 * u;
        g.text(title, x, cy, T_SECTION * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        if (trailing != null && !trailing.isEmpty()) {
            g.text(trailing, x + w, cy + 1 * u, T_BODY * u, trailingColor, Gfx.ALIGN_RIGHT, false);
        }
        return 38 * u;
    }

    /** Druck-/Hover-Zustand einer Zeile: eine weiche, randlose Aufhellung über die ganze Breite. */
    public static void rowPress(Gfx g, double x, double y, double w, double h, double lift, double u) {
        if (lift > 0) {
            g.fillRoundRect(x - 10 * u, y + 2 * u, w + 20 * u, h - 4 * u, 14 * u, Colors.withAlpha(0xFFFFFF, 0.05 * lift));
        }
    }

    /** Haarlinie unter einer Zeile (eingerückt ab {@code x}). */
    public static void hairline(Gfx g, double x, double y, double w) {
        g.fillRect(x, y, w, 1, Colors.withAlpha(0xFFFFFF, 0.08));
    }

    /**
     * Standard-Listenzeile: Titel, Untertitel, rechts ein Wert und optional ein Chevron. Links Platz für ein Symbol
     * ({@code lead} = Einrückung des Texts, 0 = ohne Symbol).
     */
    public static void row(Gfx g, double x, double y, double w, double h, double lead, String title, String sub,
                           String value, int valueColor, boolean chevron, boolean divider, double lift, boolean dim,
                           double u) {
        rowPress(g, x, y, w, h, lift, u);
        double tx = x + lead;
        double right = x + w - (chevron ? 22 * u : 0);
        double valueW = 0;
        if (value != null && !value.isEmpty()) {
            valueW = g.textWidth(value, T_BODY * u, true) + 12 * u;
            g.text(value, right, y + h / 2, T_BODY * u, valueColor, Gfx.ALIGN_RIGHT, true);
        }
        int tc = dim ? Theme.MUTED : Theme.TEXT;
        if (sub != null && !sub.isEmpty()) {
            Draw.cut(g, title, tx, y + h / 2 - 10 * u, T_ROW * u, right - valueW - tx, tc, Gfx.ALIGN_LEFT, true);
            Draw.cut(g, sub, tx, y + h / 2 + 12 * u, T_CAPTION * u, right - valueW - tx, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        } else {
            Draw.cut(g, title, tx, y + h / 2, T_ROW * u, right - valueW - tx, tc, Gfx.ALIGN_LEFT, true);
        }
        if (chevron) {
            Draw.chevron(g, x + w - 6 * u, y + h / 2, 5.5 * u, Colors.withAlpha(0xFFFFFF, 0.35), true);
        }
        if (divider) {
            hairline(g, tx, y + h - 1, x + w - tx);
        }
    }

    /** Gefüllte Haupttaste (Pille) mit Symbol und Text. */
    public static void primary(Gfx g, double x, double y, double w, double h, String label, Icons.Icon icon, int color,
                               double lift, double u) {
        g.fillRoundRect(x, y, w, h, h / 2, Colors.lerp(color, 0xFFFFFF, 0.14 * lift));
        double fs = T_ROW * 1.06 * u;
        double tw = g.textWidth(label, fs, true);
        double iw = icon != null ? 22 * u : 0;
        double left = x + w / 2 - (tw + iw) / 2;
        if (icon != null) {
            Icons.draw(g, icon, left + 7 * u, y + h / 2, 7.5 * u, 0x000000, 0);
        }
        g.text(label, left + iw, y + h / 2, fs, 0x000000, Gfx.ALIGN_LEFT, true);
    }

    /** Sekundäre Aktion als reiner Text in Akzentfarbe (rechtsbündig), z. B. „Abholen“. @return Breite */
    public static double link(Gfx g, String label, double right, double cy, int color, double lift, double u) {
        int c = lift > 0 ? Colors.lighten(color, 0.3 * lift) : color;
        g.text(label, right, cy, T_BODY * u, c, Gfx.ALIGN_RIGHT, true);
        return g.textWidth(label, T_BODY * u, true);
    }

    /** Kleine Pille (nur für Zustände wie „Abholen“, „Neu“), getönt, ohne Rand. @return Breite */
    public static double pill(Gfx g, String label, double right, double cy, int color, double lift, double u) {
        double fs = T_CAPTION * u;
        double h = 28 * u;
        double w = g.textWidth(label, fs, true) + 26 * u;
        g.fillRoundRect(right - w, cy - h / 2, w, h, h / 2, Colors.withAlpha(color, 0.18 + 0.12 * lift));
        g.text(label, right - w / 2, cy, fs, Colors.lighten(color, 0.35), Gfx.ALIGN_CENTER, true);
        return w;
    }

    /** Große Kennzahl mit Beschriftung darunter. */
    public static void stat(Gfx g, String value, String label, double x, double y, int color, double u) {
        g.text(value, x, y + 15 * u, T_STAT * u, color, Gfx.ALIGN_LEFT, true);
        g.text(label, x, y + 42 * u, T_CAPTION * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
    }

    /** Dünner Fortschrittsbalken. */
    public static void meter(Gfx g, double x, double y, double w, double f, int color, double u) {
        double h = 4 * u;
        g.fillRoundRect(x, y, w, h, h / 2, Colors.withAlpha(0xFFFFFF, 0.09));
        if (f > 0) {
            g.fillRoundRect(x, y, Math.max(h, w * Mathx.clamp01(f)), h, h / 2, color);
        }
    }

    /** Wert mit Symbol davor (z. B. Kristalle), rechtsbündig. @return Breite */
    public static double iconValueRight(Gfx g, double right, double cy, Icons.Icon icon, String value, int iconColor,
                                        int textColor, double size, double u) {
        double tw = g.textWidth(value, size, true);
        g.text(value, right, cy, size, textColor, Gfx.ALIGN_RIGHT, true);
        double is = size * 0.42;
        Icons.draw(g, icon, right - tw - 6 * u - is, cy, is, iconColor, 0);
        return tw + 6 * u + is * 2;
    }

    /** Wert mit Symbol davor, linksbündig. @return Breite */
    public static double iconValue(Gfx g, double x, double cy, Icons.Icon icon, String value, int iconColor,
                                   int textColor, double size, double u) {
        double is = size * 0.42;
        Icons.draw(g, icon, x + is, cy, is, iconColor, 0);
        g.text(value, x + is * 2 + 6 * u, cy, size, textColor, Gfx.ALIGN_LEFT, true);
        return is * 2 + 6 * u + g.textWidth(value, size, true);
    }

    /** Reiter-Leiste im Unterstrich-Stil. {@code sel} ist animiert (0..n-1, Bruchteile beim Wechsel). */
    public static void tabs(Gfx g, String[] labels, double x, double y, double w, double h, double sel, int accent,
                            double u) {
        int n = labels.length;
        double tw = w / n;
        double fs = T_BODY * 1.04 * u;
        for (int i = 0; i < n; i++) {
            String l = labels[i];
            boolean dot = l.endsWith(" ●");
            if (dot) {
                l = l.substring(0, l.length() - 2);
            }
            double k = 1 - Mathx.clamp01(Math.abs(sel - i));
            int c = Colors.lerp(Theme.MUTED, Theme.TEXT, k);
            double cx = x + tw * i + tw / 2;
            double lw = g.textWidth(l, fs, true);
            double s = lw > tw * 0.9 ? tw * 0.9 / lw : 1;
            g.text(l, cx, y + h / 2, fs * s, c, Gfx.ALIGN_CENTER, true);
            if (dot) {
                g.fillCircle(cx + lw * s / 2 + 8 * u, y + h / 2 - 6 * u, 3.5 * u, Theme.RED);
            }
        }
        g.fillRect(x, y + h - 1, w, 1, Colors.withAlpha(0xFFFFFF, 0.08));
        double ux = x + tw * sel;
        g.fillRoundRect(ux + tw * 0.18, y + h - 3 * u, tw * 0.64, 3 * u, 1.5 * u, accent);
    }

    /** Kreis-Fortschritt (z. B. Spielerlevel) mit Zahl in der Mitte. */
    public static void ring(Gfx g, double cx, double cy, double r, double f, int color, String label, double u) {
        g.strokeCircle(cx, cy, r, 3.5 * u, Colors.withAlpha(0xFFFFFF, 0.1));
        if (f > 0) {
            g.beginPath();
            g.arc(cx, cy, r, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * Mathx.clamp01(f));
            g.stroke(3.5 * u, color);
        }
        g.text(label, cx, cy, r * 0.82, Theme.TEXT, Gfx.ALIGN_CENTER, true);
    }

    /** Akzentpunkt (z. B. Weltfarbe) mit leichtem Schein. */
    public static void dot(Gfx g, double cx, double cy, double r, int color) {
        g.fillCircle(cx, cy, r * 2.2, Colors.withAlpha(color, 0.14));
        g.fillCircle(cx, cy, r, color);
    }

    /** Mehrzeiliger Text (umbrochen auf {@code maxW}). @return Höhe */
    public static double paragraph(Gfx g, String s, double x, double y, double maxW, double size, int color, double u) {
        String[] words = s.split(" ");
        StringBuilder line = new StringBuilder();
        double lh = size * 1.42;
        double yy = y + size * 0.6;
        int lines = 0;
        for (String w : words) {
            String t = line.length() == 0 ? w : line + " " + w;
            if (g.textWidth(t, size, false) > maxW && line.length() > 0) {
                g.text(line.toString(), x, yy, size, color, Gfx.ALIGN_LEFT, false);
                yy += lh;
                lines++;
                line.setLength(0);
                line.append(w);
            } else {
                line.setLength(0);
                line.append(t);
            }
        }
        if (line.length() > 0) {
            g.text(line.toString(), x, yy, size, color, Gfx.ALIGN_LEFT, false);
            lines++;
        }
        return lines * lh;
    }
}

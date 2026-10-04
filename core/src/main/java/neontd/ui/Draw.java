package neontd.ui;

import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Neon;
import neontd.gfx.Theme;

/** Wiederverwendbare Zeichenhelfer für Panels und Beschriftungen. */
public final class Draw {
    private Draw() {
    }

    /** Dunkles Panel mit leuchtendem Rand. */
    public static void panel(Gfx g, double x, double y, double w, double h, double r, int accent, double glow) {
        g.fillRoundRect(x, y, w, h, r, Colors.withAlpha(Theme.PANEL, 0.94));
        Neon.roundRect(g, x, y, w, h, r, 1.6, Colors.withAlpha(accent, 0.85), glow);
    }

    /** Dezentes Panel ohne Leuchten. */
    public static void softPanel(Gfx g, double x, double y, double w, double h, double r, int accent) {
        g.fillRoundRect(x, y, w, h, r, Colors.withAlpha(Theme.PANEL, 0.9));
        g.strokeRoundRect(x, y, w, h, r, 1.2, Colors.withAlpha(accent, 0.45));
    }

    /** Text mit schwachem Schatten, damit er auch über hellen Effekten lesbar bleibt. */
    public static void label(Gfx g, String s, double x, double y, double size, int color, int align, boolean bold) {
        g.text(s, x, y, size, color, align, bold);
    }
}

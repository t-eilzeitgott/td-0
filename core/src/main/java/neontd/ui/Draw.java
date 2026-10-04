package neontd.ui;

import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Theme;

/** Wiederverwendbare Zeichenhelfer für Panels. */
public final class Draw {
    private Draw() {
    }

    /** Dezentes dunkles Panel mit farbigem Rand (ohne Leuchten). */
    public static void softPanel(Gfx g, double x, double y, double w, double h, double r, int accent) {
        g.fillRoundRect(x, y, w, h, r, Colors.withAlpha(Theme.PANEL, 0.9));
        g.strokeRoundRect(x, y, w, h, r, 1.2, Colors.withAlpha(accent, 0.45));
    }
}

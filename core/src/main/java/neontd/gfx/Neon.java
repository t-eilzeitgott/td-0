package neontd.gfx;

/**
 * Neon-Röhren-Optik ohne teure Weichzeichner: Der Pfad wird mehrfach mit wachsender Breite und sinkender
 * Deckkraft gezeichnet (Halo) und zuletzt mit einem hellen Kern. Läuft auf Canvas2D und Java2D identisch und
 * ist auch auf dem iPhone flott. Über {@link #quality} lässt sich der Aufwand bei Bedarf drosseln.
 */
public final class Neon {
    /** 2 = voller Glanz, 1 = reduziert, 0 = nur Linie. Wird von der App bei niedriger Bildrate abgesenkt. */
    public static int quality = 2;

    private Neon() {
    }

    /** Zeichnet den aktuellen Pfad als leuchtende Röhre. */
    public static void stroke(Gfx g, double width, int color, double glow) {
        if (quality >= 1 && glow > 0) {
            g.additive(true);
            if (quality >= 2) {
                g.stroke(width + glow * 2.4, Colors.withAlpha(color, 0.045));
                g.stroke(width + glow * 1.5, Colors.withAlpha(color, 0.085));
            }
            g.stroke(width + glow * 0.75, Colors.withAlpha(color, 0.20));
            g.additive(false);
        }
        g.stroke(width, color);
        if (quality >= 2 && width >= 1.6) {
            g.stroke(Math.max(0.8, width * 0.38), Colors.withAlpha(0xFFFFFF, 0.45));
        }
    }

    public static void circle(Gfx g, double cx, double cy, double r, double width, int color, double glow) {
        g.beginPath();
        g.circle(cx, cy, r);
        stroke(g, width, color, glow);
    }

    public static void line(Gfx g, double x1, double y1, double x2, double y2, double width, int color, double glow) {
        g.beginPath();
        g.moveTo(x1, y1);
        g.lineTo(x2, y2);
        stroke(g, width, color, glow);
    }

    public static void roundRect(Gfx g, double x, double y, double w, double h, double r, double width, int color,
                                 double glow) {
        g.beginPath();
        g.roundRect(x, y, w, h, r);
        stroke(g, width, color, glow);
    }

    public static void ngon(Gfx g, double cx, double cy, double r, int sides, double rot, double width, int color,
                            double glow) {
        g.beginPath();
        g.ngon(cx, cy, r, sides, rot);
        stroke(g, width, color, glow);
    }

    /** Weicher Lichtschein hinter einem Objekt (additiv). */
    public static void halo(Gfx g, double cx, double cy, double r, int color, double intensity) {
        if (quality < 1) {
            return;
        }
        g.additive(true);
        g.radialGlow(cx, cy, r, Colors.withAlpha(color, intensity));
        g.additive(false);
    }
}

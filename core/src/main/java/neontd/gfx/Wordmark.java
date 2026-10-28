package neontd.gfx;

/**
 * Die Wortmarke „NEON TD“ in Leuchtschrift: NEON in Cyan, TD in Magenta. Wird im Intro groß gezündet und sitzt
 * danach klein im Kopf der Startseite – an derselben Stelle, damit der Übergang nahtlos ist.
 */
public final class Wordmark {
    public static final String A = "NEON";
    public static final String B = "TD";

    private Wordmark() {
    }

    /** Gesamtbreite bei Zeichenhöhe h. */
    public static double width(double h) {
        return NeonText.width(A, h) + gap(h) + NeonText.width(B, h);
    }

    private static double gap(double h) {
        return h * 0.55;
    }

    /**
     * Zeichnet die Marke mit linker Kante x und Mitte y.
     *
     * @param lit    0..1 je Buchstabe (6 Werte: N E O N T D), {@code null} = alle an
     * @param glow   Leuchtstärke
     */
    public static void draw(Gfx g, double x, double cy, double h, double glow, double[] lit, double time) {
        double wa = NeonText.width(A, h);
        double wb = NeonText.width(B, h);
        drawPart(g, A, x + wa / 2, cy, h, Theme.CYAN, glow, lit, 0, time);
        drawPart(g, B, x + wa + gap(h) + wb / 2, cy, h, Theme.MAGENTA, glow, lit, A.length(), time);
    }

    private static void drawPart(Gfx g, String text, double cx, double cy, double h, int color, double glow,
                                 double[] lit, int offset, double time) {
        if (lit == null) {
            NeonText.draw(g, text, cx, cy, h, color, glow, 0.12, time);
            return;
        }
        double total = NeonText.width(text, h);
        double x = cx - total / 2;
        for (int i = 0; i < text.length(); i++) {
            String ch = text.substring(i, i + 1);
            double gw = NeonText.width(ch, h);
            double a = lit[Math.min(lit.length - 1, offset + i)];
            if (a > 0.01) {
                g.save();
                g.alpha(a);
                NeonText.draw(g, ch, x + gw / 2, cy, h, color, glow * a, 0, time);
                g.restore();
            }
            x += gw + NeonText.width("AA", h) - 2 * NeonText.width("A", h);
        }
    }
}

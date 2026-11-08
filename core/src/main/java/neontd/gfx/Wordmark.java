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

    /** Kleines „talfahrt“-Zeichen (t-Glyphe mit grünem Strich) samt Schriftzug, zentriert um {@code cx}; {@code h} ist die Zeichenhöhe. */
    public static void byTalfahrt(Gfx g, double cx, double cy, double h, double alpha) {
        if (alpha <= 0.01) {
            return;
        }
        double s = h / 64;
        double textSize = h * 0.78;
        double gap = h * 0.35;
        double tw = g.textWidth("talfahrt", textSize, true);
        double total = h + gap + tw;
        double x0 = cx - total / 2;
        double y0 = cy - h / 2;
        g.save();
        g.alpha(alpha);
        // t: Stamm mit Fuß nach rechts, Querstrich
        g.beginPath();
        g.moveTo(x0 + 20 * s, y0 + 14 * s);
        g.lineTo(x0 + 20 * s, y0 + 38 * s);
        g.arc(x0 + 30 * s, y0 + 38 * s, 10 * s, Math.PI, Math.PI / 2);
        g.moveTo(x0 + 11.5 * s, y0 + 24.5 * s);
        g.lineTo(x0 + 28.5 * s, y0 + 24.5 * s);
        g.stroke(6 * s, 0xFFFFFF);
        // grüner Schrägstrich
        g.beginPath();
        g.moveTo(x0 + 38.5 * s, y0 + 13 * s);
        g.lineTo(x0 + 51.5 * s, y0 + 50 * s);
        g.stroke(6 * s, 0xD7F24B);
        g.text("talfahrt", x0 + h + gap, cy, textSize, 0xFFFFFF, Gfx.ALIGN_LEFT, true);
        g.restore();
    }
}

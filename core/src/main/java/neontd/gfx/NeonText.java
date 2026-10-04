package neontd.gfx;

/**
 * Eigene Strichschrift für Überschriften und Schilder: eckige "Neonröhren"-Buchstaben aus geraden Linien.
 * Sieht auf jedem Gerät gleich aus, skaliert beliebig scharf und lässt sich als Leuchtschrift animieren.
 * Unterstützt A–Z, Ä Ö Ü, 0–9 und einige Satzzeichen (alles in Großbuchstaben).
 */
public final class NeonText {
    private NeonText() {
    }

    /** Glyphen: Polylinien in der Box x 0..1, y 0..1 (oben..unten); '|' trennt Striche. */
    private static final String[] GLYPH_KEYS = {
        "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U",
        "V", "W", "X", "Y", "Z", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "-", ".", "!", "?", ":", "/",
        "+", "%", "x", "'", "&", ","
    };
    private static final String[] GLYPHS = {
        "0,1 0,.3 .3,0 .7,0 1,.3 1,1|0,.6 1,.6",
        "0,0 0,1|0,0 .7,0 1,.2 1,.3 .7,.5 0,.5|.7,.5 1,.7 1,.8 .7,1 0,1",
        "1,.2 .8,0 .2,0 0,.2 0,.8 .2,1 .8,1 1,.8",
        "0,0 .6,0 1,.3 1,.7 .6,1 0,1 0,0",
        "1,0 0,0 0,1 1,1|0,.5 .7,.5",
        "1,0 0,0 0,1|0,.5 .7,.5",
        "1,.2 .8,0 .2,0 0,.2 0,.8 .2,1 .8,1 1,.8 1,.55 .55,.55",
        "0,0 0,1|1,0 1,1|0,.5 1,.5",
        ".5,0 .5,1",
        "1,0 1,.8 .8,1 .2,1 0,.8",
        "0,0 0,1|1,0 0,.58|.3,.4 1,1",
        "0,0 0,1 1,1",
        "0,1 0,0 .5,.55 1,0 1,1",
        "0,1 0,0 1,1 1,0",
        ".2,0 .8,0 1,.2 1,.8 .8,1 .2,1 0,.8 0,.2 .2,0",
        "0,1 0,0 .8,0 1,.2 1,.35 .8,.55 0,.55",
        ".2,0 .8,0 1,.2 1,.8 .8,1 .2,1 0,.8 0,.2 .2,0|.6,.7 1,1.05",
        "0,1 0,0 .8,0 1,.2 1,.35 .8,.55 0,.55|.5,.55 1,1",
        "1,.2 .8,0 .2,0 0,.2 0,.35 .2,.5 .8,.5 1,.65 1,.8 .8,1 .2,1 0,.8",
        "0,0 1,0|.5,0 .5,1",
        "0,0 0,.8 .2,1 .8,1 1,.8 1,0",
        "0,0 .5,1 1,0",
        "0,0 .25,1 .5,.4 .75,1 1,0",
        "0,0 1,1|1,0 0,1",
        "0,0 .5,.5 1,0|.5,.5 .5,1",
        "0,0 1,0 0,1 1,1",
        ".2,0 .8,0 1,.2 1,.8 .8,1 .2,1 0,.8 0,.2 .2,0|.25,.8 .75,.2",
        ".15,.25 .55,0 .55,1|.15,1 .95,1",
        "0,.2 .2,0 .8,0 1,.2 1,.35 0,1 1,1",
        "0,.15 .2,0 .8,0 1,.2 1,.35 .75,.5 1,.65 1,.8 .8,1 .2,1 0,.85|.4,.5 .75,.5",
        ".8,1 .8,0 0,.7 1,.7",
        "1,0 0,0 0,.45 .8,.45 1,.6 1,.85 .8,1 .2,1 0,.85",
        "1,.1 .8,0 .2,0 0,.2 0,.8 .2,1 .8,1 1,.8 1,.6 .8,.45 0,.45",
        "0,0 1,0 .35,1",
        ".2,0 .8,0 1,.15 1,.35 .8,.5 .2,.5 0,.35 0,.15 .2,0|.2,.5 0,.65 0,.85 .2,1 .8,1 1,.85 1,.65 .8,.5",
        "0,.9 .2,1 .8,1 1,.8 1,.2 .8,0 .2,0 0,.2 0,.4 .2,.55 1,.55",
        ".1,.5 .9,.5",
        ".5,.9 .5,1",
        ".5,0 .5,.7|.5,.92 .5,1",
        "0,.2 .2,0 .8,0 1,.2 1,.35 .5,.55 .5,.7|.5,.92 .5,1",
        ".5,.2 .5,.28|.5,.72 .5,.8",
        "0,1 1,0",
        ".5,.2 .5,.8|.1,.5 .9,.5",
        "0,1 1,0|.1,.05 .1,.2|.9,.8 .9,.95",
        "0,.2 1,.8|1,.2 0,.8",
        ".5,0 .5,.25",
        "1,1 .3,.35 .5,0 .85,.1 .9,.35 .3,.7 .2,1 .7,1 1,.75",
        ".6,.9 .5,1.05"
    };

    private static final double[][][] COMPILED = compile();

    private static double[][][] compile() {
        double[][][] all = new double[GLYPHS.length][][];
        for (int i = 0; i < GLYPHS.length; i++) {
            String[] strokes = GLYPHS[i].split("\\|");
            double[][] g = new double[strokes.length][];
            for (int s = 0; s < strokes.length; s++) {
                String[] pts = strokes[s].trim().split(" ");
                double[] arr = new double[pts.length * 2];
                for (int p = 0; p < pts.length; p++) {
                    int comma = pts[p].indexOf(',');
                    arr[p * 2] = Double.parseDouble(pts[p].substring(0, comma));
                    arr[p * 2 + 1] = Double.parseDouble(pts[p].substring(comma + 1));
                }
                g[s] = arr;
            }
            all[i] = g;
        }
        return all;
    }

    private static final double SPACING = 0.28;
    private static final double WIDTH = 0.62;

    private static int glyphIndex(char c) {
        String key = String.valueOf(c).toUpperCase();
        for (int i = 0; i < GLYPH_KEYS.length; i++) {
            if (GLYPH_KEYS[i].equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static double glyphWidth(char c) {
        switch (c) {
            case 'I':
            case '.':
            case '!':
            case ':':
            case ',':
            case '\'':
                return WIDTH * 0.32;
            case '-':
            case '+':
                return WIDTH * 0.8;
            case 'M':
            case 'W':
                return WIDTH * 1.25;
            case '1':
                return WIDTH * 0.7;
            default:
                return WIDTH;
        }
    }

    /** Breite des Textes bei Zeichenhöhe {@code h}. */
    public static double width(String text, double h) {
        double w = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            w += (c == ' ' ? WIDTH * 0.7 : glyphWidth(umlautBase(c))) * h;
            if (i < text.length() - 1) {
                w += SPACING * h * 0.55;
            }
        }
        return w;
    }

    private static char umlautBase(char c) {
        switch (c) {
            case 'Ä':
            case 'ä':
                return 'A';
            case 'Ö':
            case 'ö':
                return 'O';
            case 'Ü':
            case 'ü':
                return 'U';
            default:
                return Character.toUpperCase(c);
        }
    }

    /**
     * Zeichnet den Text als Neonschrift.
     *
     * @param cx,cy  Mitte des Textes
     * @param h      Zeichenhöhe in Pixeln
     * @param flicker 0..1: wie stark einzelne Buchstaben "flackern" (0 = ruhig)
     * @param time   Zeit in Sekunden (nur für das Flackern)
     */
    public static void draw(Gfx g, String text, double cx, double cy, double h, int color, double glow,
                            double flicker, double time) {
        double total = width(text, h);
        double x = cx - total / 2;
        double lw = Math.max(1.6, h * 0.085);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            double gw = (c == ' ' ? WIDTH * 0.7 : glyphWidth(umlautBase(c))) * h;
            if (c != ' ') {
                double a = 1;
                if (flicker > 0) {
                    // Pro Buchstabe ein eigener, seltener Aussetzer – wirkt wie ein defekter Leuchtbuchstabe.
                    double ph = time * (0.7 + (i * 0.37) % 0.6) + i * 1.7;
                    double s = Math.sin(ph * 5.0) * Math.sin(ph * 2.3 + i);
                    if (s > 0.93) {
                        a = 1 - flicker * 0.8;
                    }
                }
                drawGlyph(g, c, x, cy - h / 2, gw, h, lw, Colors.mulAlpha(color, a), glow * a);
            }
            x += gw + SPACING * h * 0.55;
        }
    }

    private static void drawGlyph(Gfx g, char c, double x, double y, double w, double h, double lw, int color,
                                  double glow) {
        char base = umlautBase(c);
        int idx = glyphIndex(base);
        if (idx < 0) {
            return;
        }
        g.beginPath();
        double[][] strokes = COMPILED[idx];
        for (double[] st : strokes) {
            for (int p = 0; p < st.length / 2; p++) {
                double px = x + st[p * 2] * w;
                double py = y + st[p * 2 + 1] * h;
                if (p == 0) {
                    g.moveTo(px, py);
                } else {
                    g.lineTo(px, py);
                }
            }
        }
        if (c == 'Ä' || c == 'Ö' || c == 'Ü' || c == 'ä' || c == 'ö' || c == 'ü') {
            g.moveTo(x + w * 0.25, y - h * 0.2);
            g.lineTo(x + w * 0.25, y - h * 0.14);
            g.moveTo(x + w * 0.75, y - h * 0.2);
            g.lineTo(x + w * 0.75, y - h * 0.14);
        }
        Neon.stroke(g, lw, color, glow);
    }
}

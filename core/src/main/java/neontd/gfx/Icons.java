package neontd.gfx;

/** Klare, einfarbige Linien-Symbole im Neon-Stil. Alle werden in einer Einheitsbox von -1..1 entworfen. */
public final class Icons {
    private Icons() {
    }

    public enum Icon {
        PLAY, PAUSE, FAST, HEART, GEM, BACK, HOME, PLUS, CROSS, CHECK, TRASH, UNDO, PENCIL, NODE, DRAW, UP,
        RANGE, DAMAGE, SPEED, LOCK, FLAG, LOOP, TARGET, SAVE, STAR, EDITOR, WAVES
    }

    private static final double TAU = Math.PI * 2;

    /**
     * Zeichnet ein Symbol.
     *
     * @param s     halbe Kantenlänge in Pixeln
     * @param color Leuchtfarbe
     * @param glow  Stärke des Leuchtens in Pixeln (0 = keins)
     */
    public static void draw(Gfx g, Icon icon, double cx, double cy, double s, int color, double glow) {
        g.save();
        g.translate(cx, cy);
        g.scale(s, s);
        double lw = Math.max(1.5, s * 0.15) / s;
        double gl = glow / s;
        g.beginPath();
        switch (icon) {
            case PLAY:
                g.moveTo(-0.5, -0.75);
                g.lineTo(0.8, 0);
                g.lineTo(-0.5, 0.75);
                g.closePath();
                filledShape(g, color, lw, gl);
                break;
            case PAUSE:
                g.rect(-0.62, -0.7, 0.4, 1.4);
                g.rect(0.22, -0.7, 0.4, 1.4);
                filledShape(g, color, lw, gl);
                break;
            case FAST:
                g.moveTo(-0.85, -0.62);
                g.lineTo(-0.05, 0);
                g.lineTo(-0.85, 0.62);
                g.closePath();
                g.moveTo(0.0, -0.62);
                g.lineTo(0.8, 0);
                g.lineTo(0.0, 0.62);
                g.closePath();
                filledShape(g, color, lw, gl);
                break;
            case HEART:
                heart(g);
                filledShape(g, color, lw, gl);
                break;
            case GEM:
                g.moveTo(0, -0.9);
                g.lineTo(0.78, -0.2);
                g.lineTo(0, 0.92);
                g.lineTo(-0.78, -0.2);
                g.closePath();
                filledShape(g, color, lw, gl);
                g.beginPath();
                g.moveTo(-0.78, -0.2);
                g.lineTo(0.78, -0.2);
                g.moveTo(-0.3, -0.2);
                g.lineTo(0, -0.9);
                g.lineTo(0.3, -0.2);
                g.lineTo(0, 0.92);
                g.lineTo(-0.3, -0.2);
                Neon.stroke(g, lw * 0.7, color, 0);
                break;
            case BACK:
                g.moveTo(0.45, -0.8);
                g.lineTo(-0.45, 0);
                g.lineTo(0.45, 0.8);
                Neon.stroke(g, lw * 1.2, color, gl);
                break;
            case HOME:
                g.moveTo(-0.9, 0.05);
                g.lineTo(0, -0.8);
                g.lineTo(0.9, 0.05);
                g.moveTo(-0.6, -0.15);
                g.lineTo(-0.6, 0.8);
                g.lineTo(0.6, 0.8);
                g.lineTo(0.6, -0.15);
                Neon.stroke(g, lw, color, gl);
                break;
            case PLUS:
                g.moveTo(-0.75, 0);
                g.lineTo(0.75, 0);
                g.moveTo(0, -0.75);
                g.lineTo(0, 0.75);
                Neon.stroke(g, lw * 1.2, color, gl);
                break;
            case CROSS:
                g.moveTo(-0.65, -0.65);
                g.lineTo(0.65, 0.65);
                g.moveTo(0.65, -0.65);
                g.lineTo(-0.65, 0.65);
                Neon.stroke(g, lw * 1.2, color, gl);
                break;
            case CHECK:
                g.moveTo(-0.78, 0.05);
                g.lineTo(-0.25, 0.6);
                g.lineTo(0.8, -0.6);
                Neon.stroke(g, lw * 1.3, color, gl);
                break;
            case TRASH:
                g.moveTo(-0.8, -0.45);
                g.lineTo(0.8, -0.45);
                g.moveTo(-0.28, -0.45);
                g.lineTo(-0.28, -0.75);
                g.lineTo(0.28, -0.75);
                g.lineTo(0.28, -0.45);
                g.moveTo(-0.6, -0.25);
                g.lineTo(-0.48, 0.85);
                g.lineTo(0.48, 0.85);
                g.lineTo(0.6, -0.25);
                g.moveTo(-0.17, -0.05);
                g.lineTo(-0.17, 0.65);
                g.moveTo(0.17, -0.05);
                g.lineTo(0.17, 0.65);
                Neon.stroke(g, lw, color, gl);
                break;
            case UNDO:
                g.moveTo(-0.15, -0.72);
                g.lineTo(-0.7, -0.3);
                g.lineTo(-0.15, 0.1);
                g.moveTo(-0.62, -0.3);
                g.lineTo(0.1, -0.3);
                g.arc(0.1, 0.18, 0.48, -Math.PI / 2, Math.PI * 0.55);
                Neon.stroke(g, lw, color, gl);
                break;
            case PENCIL:
                g.moveTo(-0.8, 0.8);
                g.lineTo(-0.68, 0.3);
                g.lineTo(0.32, -0.7);
                g.lineTo(0.72, -0.3);
                g.lineTo(-0.28, 0.72);
                g.closePath();
                filledShape(g, color, lw, gl);
                g.beginPath();
                g.moveTo(0.1, -0.48);
                g.lineTo(0.52, -0.06);
                Neon.stroke(g, lw * 0.7, color, 0);
                break;
            case NODE:
                g.circle(0, 0, 0.55);
                Neon.stroke(g, lw, color, gl);
                g.fillCircle(0, 0, 0.2, color);
                break;
            case DRAW:
                g.moveTo(-0.85, 0.35);
                for (int i = 1; i <= 24; i++) {
                    double t = i / 24.0;
                    g.lineTo(-0.85 + 1.7 * t, 0.35 - 0.75 * t + Math.sin(t * TAU * 1.5) * 0.28);
                }
                Neon.stroke(g, lw * 1.1, color, gl);
                break;
            case UP:
                g.moveTo(0, -0.85);
                g.lineTo(0.65, -0.05);
                g.lineTo(0.24, -0.05);
                g.lineTo(0.24, 0.8);
                g.lineTo(-0.24, 0.8);
                g.lineTo(-0.24, -0.05);
                g.lineTo(-0.65, -0.05);
                g.closePath();
                filledShape(g, color, lw, gl);
                break;
            case RANGE:
                g.arc(0, 0, 0.5, -Math.PI * 0.85, Math.PI * 0.35);
                g.moveTo(Math.cos(-Math.PI * 0.85) * 0.9, Math.sin(-Math.PI * 0.85) * 0.9);
                g.arc(0, 0, 0.9, -Math.PI * 0.85, Math.PI * 0.35);
                Neon.stroke(g, lw, color, gl);
                g.fillCircle(0, 0, 0.17, color);
                break;
            case DAMAGE:
                for (int i = 0; i < 16; i++) {
                    double a = -Math.PI / 2 + TAU * i / 16;
                    double r = (i % 2 == 0) ? 0.95 : 0.42;
                    if (i == 0) {
                        g.moveTo(Math.cos(a) * r, Math.sin(a) * r);
                    } else {
                        g.lineTo(Math.cos(a) * r, Math.sin(a) * r);
                    }
                }
                g.closePath();
                filledShape(g, color, lw, gl);
                break;
            case SPEED:
                g.moveTo(0.2, -0.95);
                g.lineTo(-0.6, 0.15);
                g.lineTo(-0.08, 0.15);
                g.lineTo(-0.25, 0.95);
                g.lineTo(0.6, -0.22);
                g.lineTo(0.06, -0.22);
                g.closePath();
                filledShape(g, color, lw, gl);
                break;
            case LOCK:
                g.roundRect(-0.6, -0.1, 1.2, 0.95, 0.15);
                filledShape(g, color, lw, gl);
                g.beginPath();
                g.moveTo(-0.38, -0.1);
                g.lineTo(-0.38, -0.4);
                g.arc(0, -0.4, 0.38, Math.PI, Math.PI * 2);
                g.lineTo(0.38, -0.1);
                Neon.stroke(g, lw, color, gl);
                break;
            case FLAG:
                g.moveTo(-0.55, 0.9);
                g.lineTo(-0.55, -0.85);
                g.moveTo(-0.55, -0.8);
                g.lineTo(0.75, -0.4);
                g.lineTo(-0.55, 0.05);
                Neon.stroke(g, lw, color, gl);
                break;
            case LOOP:
                g.moveTo(Math.cos(-0.5) * 0.65, Math.sin(-0.5) * 0.65);
                g.arc(0, 0, 0.65, -0.5, Math.PI - 0.5 - 0.35);
                g.moveTo(Math.cos(Math.PI - 0.5) * 0.65, Math.sin(Math.PI - 0.5) * 0.65);
                g.arc(0, 0, 0.65, Math.PI - 0.5, TAU - 0.5 - 0.35);
                Neon.stroke(g, lw, color, gl);
                g.beginPath();
                double a1 = -0.5;
                g.moveTo(Math.cos(a1) * 0.65 - 0.28, Math.sin(a1) * 0.65 - 0.34);
                g.lineTo(Math.cos(a1) * 0.65 + 0.18, Math.sin(a1) * 0.65 - 0.02);
                g.lineTo(Math.cos(a1) * 0.65 - 0.34, Math.sin(a1) * 0.65 + 0.2);
                double a2 = Math.PI - 0.5;
                g.moveTo(Math.cos(a2) * 0.65 + 0.28, Math.sin(a2) * 0.65 + 0.34);
                g.lineTo(Math.cos(a2) * 0.65 - 0.18, Math.sin(a2) * 0.65 + 0.02);
                g.lineTo(Math.cos(a2) * 0.65 + 0.34, Math.sin(a2) * 0.65 - 0.2);
                Neon.stroke(g, lw, color, 0);
                break;
            case TARGET:
                g.circle(0, 0, 0.55);
                g.moveTo(0, -0.95);
                g.lineTo(0, -0.3);
                g.moveTo(0, 0.95);
                g.lineTo(0, 0.3);
                g.moveTo(-0.95, 0);
                g.lineTo(-0.3, 0);
                g.moveTo(0.95, 0);
                g.lineTo(0.3, 0);
                Neon.stroke(g, lw, color, gl);
                break;
            case SAVE:
                g.moveTo(0, -0.85);
                g.lineTo(0, 0.3);
                g.moveTo(-0.5, -0.15);
                g.lineTo(0, 0.4);
                g.lineTo(0.5, -0.15);
                g.moveTo(-0.8, 0.45);
                g.lineTo(-0.8, 0.85);
                g.lineTo(0.8, 0.85);
                g.lineTo(0.8, 0.45);
                Neon.stroke(g, lw, color, gl);
                break;
            case STAR:
                for (int i = 0; i < 10; i++) {
                    double a = -Math.PI / 2 + TAU * i / 10;
                    double r = (i % 2 == 0) ? 0.95 : 0.4;
                    if (i == 0) {
                        g.moveTo(Math.cos(a) * r, Math.sin(a) * r);
                    } else {
                        g.lineTo(Math.cos(a) * r, Math.sin(a) * r);
                    }
                }
                g.closePath();
                filledShape(g, color, lw, gl);
                break;
            case EDITOR:
                // Pfad mit drei Punkten
                g.moveTo(-0.8, 0.5);
                g.lineTo(-0.25, -0.45);
                g.lineTo(0.35, 0.3);
                g.lineTo(0.8, -0.5);
                Neon.stroke(g, lw, color, gl);
                g.fillCircle(-0.8, 0.5, 0.17, color);
                g.fillCircle(-0.25, -0.45, 0.17, color);
                g.fillCircle(0.35, 0.3, 0.17, color);
                g.fillCircle(0.8, -0.5, 0.17, color);
                break;
            case WAVES:
                for (int k = 0; k < 3; k++) {
                    double oy = -0.55 + k * 0.55;
                    g.moveTo(-0.85, oy);
                    for (int i = 1; i <= 16; i++) {
                        double t = i / 16.0;
                        g.lineTo(-0.85 + 1.7 * t, oy + Math.sin(t * TAU) * 0.18);
                    }
                }
                Neon.stroke(g, lw, color, gl);
                break;
            default:
                break;
        }
        g.restore();
    }

    private static void filledShape(Gfx g, int color, double lw, double glow) {
        g.fill(Colors.withAlpha(color, 0.22));
        Neon.stroke(g, lw, color, glow);
    }

    private static void heart(Gfx g) {
        int n = 36;
        for (int i = 0; i <= n; i++) {
            double t = TAU * i / n;
            double x = 16 * Math.pow(Math.sin(t), 3);
            double y = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
            double px = x / 17.0;
            double py = -(y - 1.5) / 17.0;
            if (i == 0) {
                g.moveTo(px, py);
            } else {
                g.lineTo(px, py);
            }
        }
        g.closePath();
    }
}

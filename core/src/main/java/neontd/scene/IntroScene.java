package neontd.scene;

import neontd.app.App;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Theme;
import neontd.gfx.Wordmark;
import neontd.math.Mathx;
import neontd.ui.Easing;
import neontd.ui.Viewport;

/**
 * App-Intro: Die Wortmarke zündet Buchstabe für Buchstabe wie echte Leuchtröhren (mit kurzem Flackern), hält einen
 * Moment und fliegt dann an ihren Platz im Kopf der Startseite – dort steht sie bereits, der Übergang ist nahtlos.
 * Ein Tipp überspringt das Intro.
 */
public final class IntroScene extends Scene {
    /** Zeitpunkte, zu denen die sechs Buchstaben zünden. */
    private static final double[] IGNITE = {0.15, 0.30, 0.45, 0.60, 0.95, 1.08};
    private static final double HOLD_UNTIL = 1.75;
    private static final double FLY = 0.55;

    private double time;
    /** Aufwärmen: Erst wenn die Bilder gleichmäßig kommen, läuft die Uhr – sonst würde das Zünden ruckeln. */
    private boolean warm;
    private int smoothFrames;
    private double waited;
    private boolean skipping;
    private double skipAt;
    private boolean done;
    private final double[] lit = new double[6];

    public IntroScene(App app) {
        super(app);
    }

    @Override
    public void update(double dt) {
        if (!warm) {
            // Die ersten Bilder nach dem Laden sind oft unregelmäßig (Kompilieren, erstes Zeichnen). Gestartet wird
            // nach sechs gleichmäßigen Bildern in Folge, spätestens nach 0,9 Sekunden.
            waited += dt;
            smoothFrames = dt > 0 && dt < 1.0 / 40 ? smoothFrames + 1 : 0;
            if (smoothFrames >= 6 || waited > 0.9 || skipping) {
                warm = true;
            }
            return;
        }
        // Ein einzelnes langsames Bild bremst die Animation kurz, statt sie springen zu lassen.
        time += Math.min(dt, 1.0 / 30);
        double t = time;
        if (skipping) {
            t = skipAt + (time - skipAt) * 4;
        }
        for (int i = 0; i < lit.length; i++) {
            double since = t - IGNITE[i];
            if (since <= 0) {
                lit[i] = 0;
            } else if (since < 0.22) {
                // Zünden: zwei schnelle Aussetzer, dann an
                double k = since / 0.22;
                lit[i] = (k < 0.25) ? 0.4 : (k < 0.4 ? 0.05 : (k < 0.6 ? 1 : (k < 0.72 ? 0.3 : 1)));
            } else {
                lit[i] = 1;
            }
        }
        if (t >= HOLD_UNTIL + FLY && !done) {
            done = true;
            // Erster Start im Handy-Browser: erst installieren, dann den Namen wählen; sonst auf die Startseite.
            app.goTo(app.firstScene());
        }
    }

    /** Das Intro im Ablauf-Zeitmaß (beim Überspringen beschleunigt). */
    private double clock() {
        return skipping ? skipAt + (time - skipAt) * 4 : time;
    }

    @Override
    public void render(Gfx g) {
        Viewport vp = app.vp;
        g.fillRect(0, 0, vp.w, vp.h, Theme.BG);
        double t = clock();
        double bigH = Math.min(78 * vp.u, vp.safeW() * 0.78 / (Wordmark.width(1)));
        double bigW = Wordmark.width(bigH);
        double bx = vp.w / 2 - bigW / 2;
        double by = vp.insetT + vp.safeH() * 0.44;
        double[] target = MenuScene.wordmarkBox(vp);
        double fly = Mathx.clamp01((t - HOLD_UNTIL) / FLY);
        double e = Easing.inOutCubic(fly);
        double h = Mathx.lerp(bigH, target[2], e);
        double x = Mathx.lerp(bx, target[0], e);
        double y = Mathx.lerp(by, target[1], e);
        double breathe = 0.5 + 0.5 * Math.sin(t * 2.4);
        double glow = Mathx.lerp(10 + 6 * breathe, 4, e);
        // Weicher Lichtschein hinter der großen Marke
        if (fly < 1) {
            double a = (1 - e) * 0.35 * Math.min(1, t / 0.8);
            g.save();
            g.additive(true);
            g.radialGlow(vp.w / 2, by, bigW * 0.75, Colors.withAlpha(Theme.CYAN, a * 0.5));
            g.restore();
        }
        boolean allLit = true;
        for (double l : lit) {
            allLit &= l >= 1;
        }
        if (fly >= 1 && allLit) {
            // Am Ziel exakt so wie im Kopf der nächsten Seite (gleicher Satz, gleiche Uhr) – der Wechsel ist unsichtbar.
            Wordmark.draw(g, x, y, h, 4, null, app.time);
        } else {
            Wordmark.draw(g, x, y, h, glow, lit, time);
        }
        // Untertitel, erscheint nach dem Zünden und blendet beim Flug aus
        double sub = Mathx.clamp01((t - 1.2) / 0.4) * (1 - e);
        if (sub > 0.01) {
            g.save();
            g.alpha(sub);
            g.text("TOWER DEFENSE", vp.w / 2, by + bigH * 1.05, Math.max(11, bigH * 0.22), Theme.MUTED,
                    Gfx.ALIGN_CENTER, true);
            g.restore();
        }
    }

    @Override
    public void pointerDown(double x, double y, boolean touch) {
        skip();
    }

    @Override
    public boolean key(String code, boolean ctrl) {
        skip();
        return true;
    }

    private void skip() {
        if (!skipping) {
            skipping = true;
            skipAt = time;
        }
    }
}

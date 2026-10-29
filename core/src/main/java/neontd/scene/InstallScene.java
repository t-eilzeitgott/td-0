package neontd.scene;

import java.util.ArrayList;
import java.util.List;
import neontd.app.App;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Icons.Icon;
import neontd.gfx.Theme;
import neontd.gfx.Wordmark;
import neontd.math.Mathx;
import neontd.platform.InstallInfo;
import neontd.ui.Button;
import neontd.ui.Draw;
import neontd.ui.Easing;
import neontd.ui.Ui;
import neontd.ui.Viewport;

/**
 * „Erst aufs Handy, dann los.“ – vor dem ersten Spielen im Handy-Browser: Android bekommt einen Knopf für den
 * Installations-Dialog (oder die zwei Handgriffe im Menü), iOS eine kurze Anleitung über „Teilen“. Wichtig, weil die
 * Home-Bildschirm-App auf dem iPhone einen eigenen Speicher hat: Name und Fortschritt aus dem Browser wären dort
 * nicht da. Wer nicht will, spielt mit einem Tipp im Browser weiter (das merkt sich das Gerät).
 */
public final class InstallScene extends Scene {
    private static final String SKIP_KEY = "neontd.installSkip";

    private final boolean firstStart;
    private final Ui ui = new Ui();
    private final Button install;
    private final Button skip;
    private final Button back;
    private InstallInfo info;
    private boolean busy;
    private double poll;
    private double time;
    private double reveal = -1;
    private double layoutW = -1;
    private double stableH;
    private double x;
    private double w;
    private double titleY;
    private double cardY;

    /** Soll vor dem ersten Spielen zur Installation geraten werden? Nur im Handy-Browser, nicht als App. */
    public static boolean shouldGate(App app) {
        InstallInfo i = app.platform.installInfo();
        return i != null && !i.standalone && i.kind != InstallInfo.Kind.DESKTOP && !"1".equals(app.platform.store().get(SKIP_KEY));
    }

    /** Lohnt sich der Eintrag „Als App installieren“ (Einstellungen)? */
    public static boolean offered(App app) {
        InstallInfo i = app.platform.installInfo();
        return i != null && !i.standalone && i.kind != InstallInfo.Kind.DESKTOP;
    }

    public InstallScene(App app, boolean firstStart) {
        super(app);
        this.firstStart = firstStart;
        info = app.platform.installInfo();
        install = ui.add(new Button("App installieren", Icon.PLUS, Theme.ACCENT, this::runPrompt));
        install.style = Button.Style.PRIMARY;
        skip = ui.add(new Button(firstStart ? "Ohne Installation im Browser weiter" : "Zurück", null, Theme.MUTED, this::leave));
        skip.painter = (g, b) -> {
            double hv = Math.max(b.hover.value, b.press.value);
            g.text(b.label, 0, 0, 14 * app.vp.u, Colors.lerp(Theme.MUTED, Theme.TEXT, hv), Gfx.ALIGN_CENTER, true);
        };
        back = ui.add(new Button("", Icon.BACK, Theme.TEXT, this::leave));
        back.painter = (g, b) -> {
            double r = Math.min(b.w, b.h) / 2;
            g.fillCircle(0, 0, r, Colors.lerp(Theme.SURFACE2, Theme.SURFACE3, Math.max(b.hover.value, b.press.value)));
            Draw.chevron(g, 1, 0, r * 0.3, Theme.TEXT, false);
        };
        back.visible = !firstStart;
        refreshButtons();
    }

    private void leave() {
        if (firstStart) {
            app.platform.store().put(SKIP_KEY, "1");
            app.goTo(app.afterInstall());
        } else {
            app.goTo(new SettingsScene(app));
        }
    }

    private void runPrompt() {
        if (busy || info == null || !info.canPrompt) {
            return;
        }
        busy = true;
        app.platform.promptInstall(ok -> {
            busy = false;
            info = app.platform.installInfo();
            refreshButtons();
        });
    }

    private void refreshButtons() {
        install.visible = info != null && info.canPrompt && !info.installed;
    }

    // -------------------------------------------------------------------------------------------- Layout

    @Override
    public void layout() {
        Viewport vp = app.vp;
        double u = vp.u;
        double m = 16 * u;
        w = Math.min(vp.safeW() - 2 * m, 440 * u);
        x = vp.insetL + (vp.safeW() - w) / 2;
        if (vp.w != layoutW) {
            layoutW = vp.w;
            stableH = vp.safeH();
        } else {
            stableH = Math.max(stableH, vp.safeH());
        }
        titleY = vp.insetT + Math.max(96 * u, Math.min(stableH * 0.16, 150 * u));
        cardY = titleY + 150 * u;
        install.bounds(x, cardY, w, 52 * u);
        double bottom = vp.insetT + stableH;
        skip.bounds(x, bottom - 56 * u, w, 44 * u);
        back.bounds(x, vp.insetT + 16 * u, 40 * u, 40 * u);
    }

    @Override
    public void update(double dt) {
        time += dt;
        if (reveal < 0 && !app.transitioning()) {
            reveal = 0;
        } else if (reveal >= 0) {
            reveal += Math.min(dt, 1.0 / 30);
        }
        ui.update(dt);
        poll += dt;
        if (poll > 0.5) {
            poll = 0;
            InstallInfo now = app.platform.installInfo();
            if (now != null) {
                info = now;
                refreshButtons();
                if (now.standalone && firstStart) {
                    app.goTo(app.afterInstall());
                }
            }
        }
    }

    private double part(double delay) {
        return reveal < 0 ? 0 : Easing.outCubic(Mathx.clamp01((reveal - delay) / 0.5));
    }

    // ------------------------------------------------------------------------------------------ Zeichnen

    @Override
    public void render(Gfx g) {
        Viewport vp = app.vp;
        double u = vp.u;
        g.fillRect(0, 0, vp.w, vp.h, Theme.BG);
        if (firstStart) {
            double[] box = MenuScene.wordmarkBox(vp);
            Wordmark.draw(g, box[0], box[1], box[2], 4, null, app.time); // gemeinsame Uhr: Flackern läuft über Szenenwechsel durch
        }
        double y = titleY;
        double r0 = part(0);
        g.save();
        g.alpha(r0);
        g.translate(0, (1 - r0) * 10 * u);
        g.text("APP INSTALLIEREN", x, y, 11 * u, Theme.ACCENT, Gfx.ALIGN_LEFT, true);
        g.text("Erst aufs Handy,", x, y + 34 * u, 30 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        g.text("dann los.", x, y + 70 * u, 30 * u, Theme.ACCENT, Gfx.ALIGN_LEFT, true);
        g.restore();
        double r1 = part(0.06);
        g.save();
        g.alpha(r1);
        g.translate(0, (1 - r1) * 10 * u);
        double ly = y + 102 * u;
        for (String line : wrapPlain(g, "Leg Neon TD auf deinen Home-Bildschirm: Vollbild, auch offline – und Name, "
                + "Fortschritt und Arena-Läufe bleiben in der App.", 14 * u, w)) {
            g.text(line, x, ly, 14 * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
            ly += 20 * u;
        }
        g.restore();

        double cy = cardY;
        if (install.visible) {
            double rb = part(0.14);
            g.save();
            g.alpha(rb);
            g.translate(0, (1 - rb) * 10 * u);
            install.render(g);
            g.text("Kostenlos · ohne App-Store · in Sekunden", x + w / 2, cy + 52 * u + 22 * u, 12 * u, Theme.MUTED,
                    Gfx.ALIGN_CENTER, false);
            g.restore();
        } else if (info != null && info.installed) {
            notice(g, cy, Icon.CHECK, Theme.POS, "Installiert!",
                    "Öffne Neon TD jetzt über das Symbol auf deinem Startbildschirm.", part(0.14));
        } else if (info != null && info.kind == InstallInfo.Kind.IOS && info.iosNoShare) {
            notice(g, cy, Icon.CROSS, Theme.MUTED, "Hier geht das leider nicht",
                    "Dieser Browser kann keine Apps ablegen. Tippe auf ··· und wähle „In Safari öffnen“.", part(0.14));
        } else {
            steps(g, cy, u);
        }

        double rs = part(0.4);
        g.save();
        g.alpha(rs);
        skip.render(g);
        back.render(g);
        g.restore();
    }

    private void notice(Gfx g, double y, Icon icon, int color, String head, String body, double r) {
        double u = app.vp.u;
        List<String> lines = wrapPlain(g, body, 14 * u, w - 76 * u);
        double h = 34 * u + lines.size() * 20 * u + 14 * u;
        g.save();
        g.alpha(r);
        g.translate(0, (1 - r) * 10 * u);
        g.fillRoundRect(x, y, w, h, 18 * u, Theme.SURFACE);
        g.strokeRoundRect(x, y, w, h, 18 * u, 1, Theme.LINE);
        Draw.iconBadge(g, x + 30 * u, y + 30 * u, 14 * u, icon, color);
        g.text(head, x + 56 * u, y + 24 * u, 15 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        double ly = y + 46 * u;
        for (String l : lines) {
            g.text(l, x + 56 * u, ly, 14 * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
            ly += 20 * u;
        }
        g.restore();
    }

    /** Die Handgriffe als nummerierte Liste (iOS: Teilen → Zum Home-Bildschirm, Android: Menü → Installieren). */
    private void steps(Gfx g, double y, double u) {
        boolean ios = info != null && info.kind == InstallInfo.Kind.IOS;
        boolean other = info != null && info.iosOtherBrowser;
        String[] steps = ios
                ? new String[] {
                    "Tippe " + (other ? "oben in der Adressleiste" : "unten") + " auf [share] **Teilen**"
                            + (other ? "." : " – im neuen Safari erst auf **···**."),
                    "Wähle **Zum Home-Bildschirm** – eventuell etwas nach unten scrollen.",
                    "Tippe auf **Hinzufügen** und öffne Neon TD dann vom Home-Bildschirm.",
                }
                : new String[] {
                    "Öffne das Browser-Menü (**drei Punkte** oben rechts).",
                    "Wähle **App installieren** oder **Zum Startbildschirm hinzufügen**.",
                    "Bestätigen und Neon TD vom Startbildschirm öffnen.",
                };
        double pad = 16 * u;
        double fs = 15 * u;
        double lh = 21 * u;
        double tw = w - pad * 2 - 40 * u;
        List<List<Tok>> wrapped = new ArrayList<>();
        double h = pad;
        for (String s : steps) {
            List<Tok> lines = wrapRich(g, s, fs, tw);
            wrapped.add(lines);
            h += Math.max(26 * u, countLines(lines) * lh) + 14 * u;
        }
        h += pad - 14 * u;
        double rc = part(0.12);
        g.save();
        g.alpha(rc);
        g.translate(0, (1 - rc) * 10 * u);
        g.fillRoundRect(x, y, w, h, 18 * u, Theme.SURFACE);
        g.strokeRoundRect(x, y, w, h, 18 * u, 1, Theme.LINE);
        g.restore();
        double ry = y + pad;
        for (int i = 0; i < steps.length; i++) {
            double r = part(0.18 + 0.07 * i);
            List<Tok> toks = wrapped.get(i);
            int n = countLines(toks);
            g.save();
            g.alpha(r);
            g.translate(0, (1 - r) * 8 * u);
            double cx = x + pad + 13 * u;
            g.fillCircle(cx, ry + 13 * u, 13 * u, Colors.withAlpha(Theme.ACCENT, 0.16));
            g.text(Integer.toString(i + 1), cx, ry + 13 * u, 14 * u, Theme.ACCENT, Gfx.ALIGN_CENTER, true);
            double tx0 = x + pad + 40 * u;
            double baseY = ry + 13 * u; // erste Zeile mittig zur Nummer, weitere darunter
            for (Tok t : toks) {
                double ty = baseY + t.line * lh;
                double tx = tx0 + t.x;
                if (t.icon) {
                    shareIcon(g, tx + fs * 0.45, ty, fs * 0.5, Theme.ACCENT);
                } else {
                    g.text(t.text, tx, ty, fs, t.bold ? Theme.TEXT : Colors.lerp(Theme.TEXT, Theme.MUTED, 0.25),
                            Gfx.ALIGN_LEFT, t.bold);
                }
            }
            g.restore();
            ry += Math.max(26 * u, n * lh) + 14 * u;
        }
    }

    /** Das Teilen-Symbol von iOS: Kasten mit Pfeil nach oben. */
    private static void shareIcon(Gfx g, double cx, double cy, double s, int color) {
        double lw = Math.max(1.4, s * 0.16);
        g.line(cx, cy - s * 0.95, cx, cy + s * 0.25, lw, color);
        g.line(cx - s * 0.42, cy - s * 0.55, cx, cy - s * 0.95, lw, color);
        g.line(cx + s * 0.42, cy - s * 0.55, cx, cy - s * 0.95, lw, color);
        g.line(cx - s * 0.55, cy - s * 0.2, cx - s * 0.75, cy - s * 0.2, lw, color);
        g.line(cx - s * 0.75, cy - s * 0.2, cx - s * 0.75, cy + s * 0.95, lw, color);
        g.line(cx - s * 0.75, cy + s * 0.95, cx + s * 0.75, cy + s * 0.95, lw, color);
        g.line(cx + s * 0.75, cy + s * 0.95, cx + s * 0.75, cy - s * 0.2, lw, color);
        g.line(cx + s * 0.75, cy - s * 0.2, cx + s * 0.55, cy - s * 0.2, lw, color);
    }

    // --------------------------------------------------------------------------------------- Textsatz

    /** Ein Wort (oder Symbol) mit Lage in seiner Zeile. */
    private static final class Tok {
        String text;
        boolean bold;
        boolean icon;
        int line;
        double x;
    }

    private static int countLines(List<Tok> toks) {
        int n = 0;
        for (Tok t : toks) {
            n = Math.max(n, t.line + 1);
        }
        return Math.max(1, n);
    }

    /** Umbruch mit **fett** und dem Platzhalter [share] für das Teilen-Symbol. */
    private static List<Tok> wrapRich(Gfx g, String s, double fs, double maxW) {
        List<Tok> out = new ArrayList<>();
        boolean bold = false;
        double space = g.textWidth(" ", fs, false);
        double cx = 0;
        int line = 0;
        for (String raw : s.split(" ")) {
            if (raw.isEmpty()) {
                continue;
            }
            // Fettschrift kann mitten im Wort an- und ausgehen ("**Teilen**.")
            List<Tok> word = new ArrayList<>();
            String rest = raw;
            while (!rest.isEmpty()) {
                int k = rest.indexOf("**");
                String piece = k < 0 ? rest : rest.substring(0, k);
                if (!piece.isEmpty()) {
                    Tok t = new Tok();
                    t.icon = piece.equals("[share]");
                    t.text = t.icon ? "" : piece;
                    t.bold = bold;
                    word.add(t);
                }
                if (k < 0) {
                    break;
                }
                bold = !bold;
                rest = rest.substring(k + 2);
            }
            double ww = 0;
            for (Tok t : word) {
                ww += t.icon ? fs * 0.95 : g.textWidth(t.text, fs, t.bold);
            }
            if (cx > 0 && cx + ww > maxW) {
                line++;
                cx = 0;
            }
            for (Tok t : word) {
                t.line = line;
                t.x = cx;
                cx += t.icon ? fs * 0.95 : g.textWidth(t.text, fs, t.bold);
                out.add(t);
            }
            cx += space;
        }
        return out;
    }

    private static List<String> wrapPlain(Gfx g, String s, double fs, double maxW) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = cur.length() == 0 ? word : cur + " " + word;
            if (cur.length() > 0 && g.textWidth(test, fs, false) > maxW) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (cur.length() > 0) {
            lines.add(cur.toString());
        }
        return lines;
    }

    // ------------------------------------------------------------------------------------------ Eingabe

    @Override
    public void pointerDown(double px, double py, boolean touch) {
        if (reveal >= 0.2) {
            ui.pointerDown(px, py);
        }
    }

    @Override
    public void pointerMove(double px, double py, boolean pressed, boolean touch) {
        ui.pointerMove(px, py, !touch);
    }

    @Override
    public void pointerUp(double px, double py, boolean touch) {
        ui.pointerUp(px, py);
    }

    @Override
    public void pointerCancel() {
        ui.cancel();
    }

    @Override
    public boolean back() {
        if (!firstStart) {
            leave();
        }
        return true;
    }

    /** Für Tests und Screenshots. */
    public double[] skipAnchor() {
        return new double[] {skip.cx(), skip.cy()};
    }

    public double[] installAnchor() {
        return install.visible ? new double[] {install.cx(), install.cy()} : null;
    }
}

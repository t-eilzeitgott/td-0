package neontd.scene;

import neontd.app.App;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Icons.Icon;
import neontd.gfx.Neon;
import neontd.gfx.NeonText;
import neontd.gfx.Theme;
import neontd.math.Mathx;
import neontd.progress.Progress;
import neontd.render.TowerArt;
import neontd.save.GistSync;
import neontd.save.SaveBundle;
import neontd.sim.TowerType;
import neontd.ui.Button;
import neontd.ui.Draw;
import neontd.ui.Fmt;
import neontd.ui.Ui;
import neontd.ui.Viewport;

/**
 * Profil: Name, Level mit XP-Balken, Statistiken, Turm-Freischaltungen und Belohnungen sowie der Cloud-Speicher
 * (privater GitHub-Gist) und Export/Import per Code. Der Inhalt scrollt, damit er auch auf dem Handy passt.
 */
public final class ProfileScene extends Scene {
    public static final String TOKEN_URL = "https://github.com/settings/tokens/new?scopes=gist&description=Neon%20TD";

    private final Ui ui = new Ui();
    private final Button back;
    private final Button nameBtn;
    private final Button connectBtn;
    private final Button helpBtn;
    private final Button syncBtn;
    private final Button disconnectBtn;
    private final Button copyBtn;
    private final Button pasteBtn;

    private double time;
    private double scroll;
    private double scrollMax;
    private double velocity;
    private double lastY;
    private double lastT;
    private double downY;
    private boolean pressing;
    private boolean scrolling;
    private String toast;
    private int toastColor = Theme.GREEN;
    private double toastTime;

    // Layout (Inhaltskoordinaten, y relativ zur Oberkante des Inhalts)
    private double top;
    private double bottom;
    private double cx;
    private double cw;
    private double yCard;
    private double hCard;
    private double yStats;
    private double hStats;
    private double yTowers;
    private double hTowers;
    private double yCloud;
    private double hCloud;
    private double yBackup;
    private double hBackup;
    private double contentH;
    private double u;

    public ProfileScene(App app) {
        super(app);
        back = ui.add(new Button("", Icon.BACK, Theme.CYAN, () -> app.goTo(new MenuScene(app))));
        nameBtn = ui.add(new Button("", null, Theme.MAGENTA, this::editName));
        nameBtn.painter = (g, b) -> { };
        connectBtn = ui.add(new Button("MIT GITHUB VERBINDEN", Icon.SAVE, Theme.GREEN, this::connect));
        helpBtn = ui.add(new Button("TOKEN ERSTELLEN (GITHUB ÖFFNEN)", null, Theme.CYAN,
                () -> app.platform.openUrl(TOKEN_URL)));
        syncBtn = ui.add(new Button("JETZT ABGLEICHEN", Icon.LOOP, Theme.GREEN, () -> app.cloud.sync()));
        disconnectBtn = ui.add(new Button("TRENNEN", Icon.CROSS, Theme.RED, () -> {
            app.cloud.disconnect();
            say("Getrennt – dein Spielstand bleibt auf diesem Gerät.", Theme.YELLOW);
        }));
        copyBtn = ui.add(new Button("CODE KOPIEREN", Icon.SAVE, Theme.CYAN, this::exportCode));
        pasteBtn = ui.add(new Button("CODE EINFÜGEN", Icon.PLUS, Theme.MAGENTA, this::importCode));
        for (Button b : new Button[] {connectBtn, helpBtn, syncBtn, disconnectBtn, copyBtn, pasteBtn}) {
            b.fontScale = 0.72;
        }
    }

    // ------------------------------------------------------------------------------------------ Aktionen

    private void say(String msg, int color) {
        toast = msg;
        toastColor = color;
        toastTime = 3.2;
    }

    private void editName() {
        app.platform.prompt("Dein Name (höchstens " + Progress.MAX_NAME + " Zeichen)", app.progress.name, text -> {
            if (text == null) {
                return;
            }
            app.progress.name = Progress.cleanName(text);
            app.commitProgress();
            say(app.progress.name.isEmpty() ? "Name entfernt" : "Name gespeichert", Theme.GREEN);
        });
    }

    private void connect() {
        if (!app.cloud.available()) {
            say("Cloud-Speicher ist auf diesem Gerät nicht verfügbar.", Theme.RED);
            return;
        }
        app.platform.prompt("GitHub-Token einfügen (nur Berechtigung „gist“)", "", text -> {
            if (text != null && !text.trim().isEmpty()) {
                app.cloud.connect(text);
            }
        });
    }

    private void exportCode() {
        String code = SaveBundle.collect(app.saves, app.levels).toCode();
        app.platform.copyText(code, ok -> {
            if (ok) {
                say("Code kopiert (" + code.length() + " Zeichen). Auf dem anderen Gerät einfügen.", Theme.GREEN);
            } else {
                // Notlösung: im Eingabefeld anzeigen, damit man ihn selbst kopieren kann
                app.platform.prompt("Dein Code – markieren und kopieren", code, ignore -> { });
            }
        });
    }

    private void importCode() {
        app.platform.prompt("Code einfügen", "", text -> {
            if (text == null || text.trim().isEmpty()) {
                return;
            }
            SaveBundle imported = SaveBundle.fromCode(text);
            if (imported == null) {
                say("Das ist kein gültiger Neon-TD-Code.", Theme.RED);
                return;
            }
            SaveBundle merged = SaveBundle.merge(SaveBundle.collect(app.saves, app.levels), imported);
            merged.applyTo(app.saves, app.levels);
            app.progress.copyFrom(merged.profile);
            app.cloud.markDirty();
            say("Übernommen: Level " + app.progress.level() + ", " + app.levels.loadAll().size() + " eigene Level.",
                    Theme.GREEN);
        });
    }

    // ------------------------------------------------------------------------------------------ Layout

    @Override
    public void layout() {
        Viewport vp = app.vp;
        u = vp.u;
        double m = 14 * u;
        double bs = 46 * u;
        back.bounds(vp.insetL + m, vp.insetT + m, bs, bs);
        top = vp.insetT + m + bs + m;
        bottom = vp.h - vp.insetB - m * 0.5;
        cw = Math.min(vp.safeW() - 2 * m, 600 * u);
        cx = vp.insetL + (vp.safeW() - cw) / 2;
        double gap = 14 * u;
        double y = 0;
        yCard = y;
        hCard = 150 * u;
        y += hCard + gap;
        yStats = y;
        hStats = 2 * 56 * u + 10 * u + 20 * u;
        y += hStats + gap;
        yTowers = y;
        hTowers = 44 * u + TowerType.values().length * 40 * u + 22 * u + 36 * u;
        y += hTowers + gap;
        yCloud = y;
        hCloud = cloudHeight();
        y += hCloud + gap;
        yBackup = y;
        hBackup = 64 * u + 46 * u + 40 * u;
        y += hBackup + gap;
        contentH = y + 20 * u;
        scrollMax = Math.max(0, contentH - (bottom - top));
        scroll = Mathx.clamp(scroll, 0, scrollMax);
        placeButtons();
    }

    private double cloudHeight() {
        boolean connected = app.cloud.connected();
        return connected ? 44 * u + 54 * u + 58 * u + 24 * u : 44 * u + 4 * 22 * u + 2 * 50 * u + 30 * u;
    }

    private void placeButtons() {
        double off = top - scroll;
        double pad = 16 * u;
        double bh = 46 * u;
        // Name antippen: die ganze Kopfzeile der Karte
        nameBtn.bounds(cx + 96 * u, off + yCard + 12 * u, cw - 96 * u - 12 * u, 44 * u);
        boolean connected = app.cloud.connected();
        double bw = cw - 2 * pad;
        connectBtn.visible = !connected;
        helpBtn.visible = !connected;
        syncBtn.visible = connected;
        disconnectBtn.visible = connected;
        if (!connected) {
            double y = off + yCloud + 44 * u + 4 * 22 * u + 10 * u;
            connectBtn.bounds(cx + pad, y, bw, bh);
            helpBtn.bounds(cx + pad, y + bh + 8 * u, bw, bh * 0.82);
        } else {
            double y = off + yCloud + 44 * u + 54 * u;
            double w1 = bw * 0.62;
            syncBtn.bounds(cx + pad, y, w1, bh);
            disconnectBtn.bounds(cx + pad + w1 + 8 * u, y, bw - w1 - 8 * u, bh);
        }
        double y2 = off + yBackup + 64 * u;
        double half = (bw - 8 * u) / 2;
        copyBtn.bounds(cx + pad, y2, half, bh);
        pasteBtn.bounds(cx + pad + half + 8 * u, y2, half, bh);
        nameBtn.visible = true;
        copyBtn.visible = true;
        pasteBtn.visible = true;
        // Außerhalb des sichtbaren Bereichs nicht antippbar
        for (Button b : new Button[] {nameBtn, connectBtn, helpBtn, syncBtn, disconnectBtn, copyBtn, pasteBtn}) {
            boolean in = b.y + b.h > top && b.y < bottom;
            if (!in) {
                b.visible = false;
            }
        }
    }

    // ------------------------------------------------------------------------------------------ Update

    @Override
    public void update(double dt) {
        time += dt;
        double newH = cloudHeight();
        if (Math.abs(newH - hCloud) > 0.5) {
            layout();
        }
        if (!scrolling && !pressing && Math.abs(velocity) > 5) {
            scroll = Mathx.clamp(scroll - velocity * dt, 0, scrollMax);
            velocity *= Math.exp(-4.5 * dt);
            if (scroll <= 0 || scroll >= scrollMax) {
                velocity = 0;
            }
        }
        placeButtons();
        ui.update(dt);
        boolean working = app.cloud.status == GistSync.Status.WORKING;
        syncBtn.enabled = !working;
        connectBtn.enabled = !working;
        if (toastTime > 0) {
            toastTime -= dt;
        }
    }

    // ----------------------------------------------------------------------------------------- Zeichnen

    @Override
    public void render(Gfx g) {
        Viewport vp = app.vp;
        g.fillRect(0, 0, vp.w, vp.h, Theme.BLACK);
        drawBackdrop(g, vp);

        g.save();
        g.clipRect(0, top - 4 * u, vp.w, bottom - top + 8 * u);
        g.translate(0, top - scroll);
        drawCard(g);
        drawStats(g);
        drawTowers(g);
        drawCloud(g);
        drawBackup(g);
        g.restore();

        // Buttons (liegen im Bildschirmraum, daher außerhalb der Verschiebung, aber im selben Ausschnitt)
        g.save();
        g.clipRect(0, top - 4 * u, vp.w, bottom - top + 8 * u);
        for (Button b : new Button[] {nameBtn, connectBtn, helpBtn, syncBtn, disconnectBtn, copyBtn, pasteBtn}) {
            b.render(g);
        }
        g.restore();
        drawScrollBar(g);

        NeonText.draw(g, "PROFIL", vp.w / 2, back.cy(), Math.min(34 * u, vp.safeW() * 0.5 / 5), Theme.MAGENTA, 8, 0.4, time);
        back.render(g);

        if (toastTime > 0 && toast != null) {
            double a = Mathx.clamp01(toastTime / 0.4);
            double fs = Math.max(11, 14 * u);
            double w = Math.min(vp.safeW() - 20 * u, g.textWidth(toast, fs, true) + 34 * u);
            double h = 34 * u;
            double y = vp.h - vp.insetB - h - 16 * u;
            g.save();
            g.alpha(a);
            g.fillRoundRect(vp.w / 2 - w / 2, y, w, h, h / 2, Colors.withAlpha(0x000000, 0.92));
            Neon.roundRect(g, vp.w / 2 - w / 2, y, w, h, h / 2, 1.6, toastColor, 5);
            double shrink = Math.min(1, (w - 20 * u) / Math.max(1, g.textWidth(toast, fs, true)));
            g.text(toast, vp.w / 2, y + h / 2, fs * shrink, Theme.TEXT, Gfx.ALIGN_CENTER, true);
            g.restore();
        }
    }

    private void drawBackdrop(Gfx g, Viewport vp) {
        g.beginPath();
        double step = 56 * vp.u;
        double off = (time * 6) % step;
        for (double x = -off; x < vp.w; x += step) {
            g.moveTo(x, 0);
            g.lineTo(x, vp.h);
        }
        for (double y = -off; y < vp.h; y += step) {
            g.moveTo(0, y);
            g.lineTo(vp.w, y);
        }
        g.stroke(1, Colors.withAlpha(Theme.GRID, 0.55));
    }

    private void drawScrollBar(Gfx g) {
        if (scrollMax <= 0) {
            return;
        }
        double trackH = bottom - top;
        double barH = Math.max(30 * u, trackH * trackH / (trackH + scrollMax));
        double y = top + (trackH - barH) * (scroll / scrollMax);
        g.fillRoundRect(app.vp.w - app.vp.insetR - 6 * u, y, 3 * u, barH, 2 * u, Colors.withAlpha(Theme.MAGENTA, 0.45));
    }

    private void panel(Gfx g, double y, double h, int accent, String title) {
        double r = 16 * u;
        g.fillRoundRect(cx, y, cw, h, r, Colors.withAlpha(Theme.PANEL, 0.95));
        Neon.roundRect(g, cx, y, cw, h, r, 1.6, Colors.withAlpha(accent, 0.8), 4);
        if (title != null) {
            g.text(title, cx + 16 * u, y + 22 * u, Math.max(11, 14 * u), accent, Gfx.ALIGN_LEFT, true);
        }
    }

    private void drawCard(Gfx g) {
        Progress p = app.progress;
        panel(g, yCard, hCard, Theme.MAGENTA, null);
        // Level-Plakette
        double r = 38 * u;
        double bx = cx + 16 * u + r;
        double by = yCard + 16 * u + r;
        Neon.circle(g, bx, by, r, 2.6, Theme.MAGENTA, 9);
        g.text(Integer.toString(p.level()), bx, by - 2 * u, r * 0.95, Theme.TEXT, Gfx.ALIGN_CENTER, true);
        g.text("LEVEL", bx, by + r * 0.62, Math.max(8, r * 0.26), Theme.TEXT_DIM, Gfx.ALIGN_CENTER, true);
        // Name (antippen zum Ändern) und Titel
        double nx = cx + 96 * u;
        double fs = Math.max(14, 24 * u);
        g.text(p.displayName(), nx, yCard + 30 * u, fs, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        Icons.draw(g, Icon.PENCIL, cx + cw - 28 * u, yCard + 30 * u, 9 * u, Theme.TEXT_DIM, 0);
        g.text(p.title(), nx, yCard + 58 * u, Math.max(11, 14 * u), Theme.MAGENTA, Gfx.ALIGN_LEFT, true);
        // XP-Balken
        double bw = cw - 96 * u - 20 * u;
        double barY = yCard + 92 * u;
        g.fillRoundRect(nx, barY - 5 * u, bw, 10 * u, 5 * u, Colors.withAlpha(Theme.MAGENTA, 0.2));
        double f = p.levelFraction();
        if (f > 0) {
            g.fillRoundRect(nx, barY - 5 * u, Math.max(10 * u, bw * f), 10 * u, 5 * u, Theme.MAGENTA);
        }
        String xp = p.level() >= Progress.MAX_LEVEL ? "Höchststufe" : Fmt.compact(p.xpIntoLevel()) + " / " + Fmt.compact(p.xpSpan()) + " XP";
        g.text(xp, nx, barY + 20 * u, Math.max(10, 12 * u), Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        int next = p.nextUnlockLevel();
        String hint = next > 0 ? "Nächster Turm ab Level " + next : "Alle Türme frei";
        g.text(hint, cx + cw - 16 * u, barY + 20 * u, Math.max(10, 12 * u), Theme.GREEN, Gfx.ALIGN_RIGHT, false);
        g.text(app.cloud.connected() ? "☁ " + app.cloud.user() : "Nur auf diesem Gerät gespeichert", cx + 16 * u,
                yCard + hCard - 16 * u, Math.max(9, 11 * u), Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
    }

    private void drawStats(Gfx g) {
        Progress p = app.progress;
        panel(g, yStats, hStats, Theme.CYAN, "STATISTIK");
        String[] labels = {"Wellen besiegt", "Abschüsse", "Siege", "Beste Endlos-Welle"};
        int best = 0;
        for (int v : p.bestEndless.values()) {
            best = Math.max(best, v);
        }
        String[] vals = {Fmt.compact(p.wavesCleared), Fmt.compact(p.kills), p.gamesWon + " / " + p.gamesPlayed,
            best > 0 ? Integer.toString(best) : "–"};
        double cell = (cw - 32 * u) / 2;
        for (int i = 0; i < 4; i++) {
            double x = cx + 16 * u + (i % 2) * cell;
            double y = yStats + 34 * u + (i / 2) * 56 * u;
            g.text(vals[i], x, y + 14 * u, Math.max(14, 22 * u), Theme.TEXT, Gfx.ALIGN_LEFT, true);
            g.text(labels[i], x, y + 38 * u, Math.max(9, 11 * u), Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        }
    }

    private void drawTowers(Gfx g) {
        Progress p = app.progress;
        panel(g, yTowers, hTowers, Theme.GREEN, "TÜRME & BELOHNUNGEN");
        double y0 = yTowers + 44 * u;
        double rowH = 40 * u;
        TowerType[] types = TowerType.values();
        // nach Freischalt-Level sortiert anzeigen
        TowerType[] order = types.clone();
        for (int i = 0; i < order.length; i++) {
            for (int j = i + 1; j < order.length; j++) {
                if (order[j].unlockLevel < order[i].unlockLevel) {
                    TowerType t = order[i];
                    order[i] = order[j];
                    order[j] = t;
                }
            }
        }
        for (int i = 0; i < order.length; i++) {
            TowerType t = order[i];
            boolean open = p.isUnlocked(t);
            double y = y0 + i * rowH;
            double ix = cx + 16 * u + 16 * u;
            g.save();
            g.alpha(open ? 1 : 0.35);
            TowerArt.drawIcon(g, t, ix, y + rowH / 2 - 2 * u, 14 * u, time);
            g.restore();
            g.text(t.label, ix + 28 * u, y + rowH / 2 - 8 * u, Math.max(11, 15 * u), open ? Theme.TEXT : Theme.TEXT_DIM,
                    Gfx.ALIGN_LEFT, true);
            g.text(t.tagline, ix + 28 * u, y + rowH / 2 + 9 * u, Math.max(8, 10 * u), Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
            if (open) {
                Icons.draw(g, Icon.CHECK, cx + cw - 30 * u, y + rowH / 2 - 2 * u, 9 * u, Theme.GREEN, 3);
            } else {
                String need = "LEVEL " + t.unlockLevel;
                double ns = Math.max(10, 12 * u);
                g.text(need, cx + cw - 16 * u, y + rowH / 2 - 2 * u, ns, Theme.TEXT_DIM, Gfx.ALIGN_RIGHT, true);
                Icons.draw(g, Icon.LOCK, cx + cw - 16 * u - g.textWidth(need, ns, true) - 14 * u, y + rowH / 2 - 2 * u,
                        9 * u, Theme.TEXT_DIM, 0);
            }
        }
        double y = y0 + order.length * rowH + 8 * u;
        g.text("Startgeld  +" + p.startMoneyBonus() + "   ·   Start-Leben  +" + p.startLivesBonus(), cx + 16 * u,
                y + 8 * u, Math.max(10, 13 * u), Theme.YELLOW, Gfx.ALIGN_LEFT, true);
        g.text("+10 Startgeld je Level (bis +300), +1 Leben je 5 Level (bis +5)", cx + 16 * u, y + 28 * u,
                Math.max(8, 10 * u), Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
    }

    private void drawCloud(Gfx g) {
        boolean connected = app.cloud.connected();
        panel(g, yCloud, hCloud, Theme.GREEN, "CLOUD-SPEICHER (GITHUB)");
        double x = cx + 16 * u;
        double fs = Math.max(10, 12 * u);
        if (!connected) {
            String[] lines = {
                "Sichert Profil, eigene Level und laufende Spiele in einem",
                "privaten GitHub-Gist – und bringt sie auf jedes Gerät, auch",
                "auf die Home-Bildschirm-App am iPhone (die hat einen",
                "eigenen Speicher). Das Token bleibt nur auf diesem Gerät."
            };
            for (int i = 0; i < lines.length; i++) {
                g.text(lines[i], x, yCloud + 50 * u + i * 22 * u, fs, Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
            }
        } else {
            g.text("Verbunden als @" + app.cloud.user(), x, yCloud + 50 * u, Math.max(12, 15 * u), Theme.TEXT, Gfx.ALIGN_LEFT, true);
            String last = app.cloud.lastOk() > 0 ? "Zuletzt abgeglichen: " + ago(app.saves.now() - app.cloud.lastOk())
                    : "Noch nicht abgeglichen";
            g.text(last, x, yCloud + 50 * u + 20 * u, fs, Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        }
        int c = app.cloud.status == GistSync.Status.ERROR ? Theme.RED
                : (app.cloud.status == GistSync.Status.OK ? Theme.GREEN : Theme.YELLOW);
        String msg = app.cloud.message;
        if (!msg.isEmpty()) {
            double max = cw - 32 * u;
            double size = Math.min(fs, max / Math.max(1, msg.length() * 0.56));
            g.text(msg, x, yCloud + hCloud - 18 * u, Math.max(8, size), c, Gfx.ALIGN_LEFT, true);
        }
    }

    private static String ago(double ms) {
        double s = Math.max(0, ms / 1000);
        if (s < 60) {
            return "gerade eben";
        }
        if (s < 3600) {
            return "vor " + (int) (s / 60) + " Min";
        }
        if (s < 86400) {
            return "vor " + (int) (s / 3600) + " Std";
        }
        return "vor " + (int) (s / 86400) + " Tagen";
    }

    private void drawBackup(Gfx g) {
        panel(g, yBackup, hBackup, Theme.CYAN, "SICHERUNG PER CODE (OHNE KONTO)");
        g.text("Kopiere deinen Stand als Text und füge ihn auf einem anderen Gerät ein.", cx + 16 * u,
                yBackup + 46 * u, Math.max(9, 11 * u), Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        g.text("Der Code enthält Profil und eigene Level, nicht das laufende Spiel.", cx + 16 * u, yBackup + hBackup - 14 * u,
                Math.max(8, 10 * u), Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
    }

    /**
     * Bildschirmposition einer Schaltfläche – für Screenshot-Werkzeug und Browser-Test. Namen: {@code back},
     * {@code name}, {@code connect}, {@code sync}, {@code disconnect}, {@code copy}, {@code paste}.
     */
    public double[] anchor(String which) {
        Button b;
        switch (which) {
            case "back":
                b = back;
                break;
            case "name":
                b = nameBtn;
                break;
            case "connect":
                b = connectBtn;
                break;
            case "sync":
                b = syncBtn;
                break;
            case "disconnect":
                b = disconnectBtn;
                break;
            case "copy":
                b = copyBtn;
                break;
            case "paste":
                b = pasteBtn;
                break;
            default:
                return null;
        }
        return new double[] {b.cx(), b.cy()};
    }

    /** Scrollt den Inhalt (nur für Tests). */
    public void scrollTo(double y) {
        scroll = Mathx.clamp(y, 0, scrollMax);
        placeButtons();
    }

    // ------------------------------------------------------------------------------------------ Eingabe

    @Override
    public void pointerDown(double x, double y, boolean touch) {
        pressing = true;
        scrolling = false;
        velocity = 0;
        downY = y;
        lastY = y;
        lastT = time;
        ui.pointerDown(x, y);
    }

    @Override
    public void pointerMove(double x, double y, boolean pressed, boolean touch) {
        ui.pointerMove(x, y, !touch);
        if (!pressed) {
            return;
        }
        if (!scrolling && Math.abs(y - downY) > 9 * u) {
            scrolling = true;
            ui.cancel();
        }
        if (scrolling) {
            double dy = y - lastY;
            scroll = Mathx.clamp(scroll - dy, 0, scrollMax);
            double dt = Math.max(1.0 / 240, time - lastT);
            velocity = Mathx.lerp(velocity, dy / dt, 0.4);
            lastY = y;
            lastT = time;
        }
    }

    @Override
    public void pointerUp(double x, double y, boolean touch) {
        pressing = false;
        if (!scrolling) {
            ui.pointerUp(x, y);
        } else {
            ui.cancel();
        }
        scrolling = false;
        if (touch) {
            ui.pointerMove(-100, -100, false);
        }
    }

    @Override
    public void pointerCancel() {
        pressing = false;
        scrolling = false;
        ui.cancel();
    }

    @Override
    public void wheel(double dy) {
        scroll = Mathx.clamp(scroll + dy, 0, scrollMax);
    }

    @Override
    public boolean back() {
        return false;
    }
}

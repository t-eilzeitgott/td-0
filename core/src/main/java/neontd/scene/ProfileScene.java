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
import neontd.ui.Kit;
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
        back = ui.add(new Button("", Icon.BACK, Theme.TEXT, () -> app.goTo(new MenuScene(app))));
        back.painter = (g, b) -> {
            double r = Math.min(b.w, b.h) / 2;
            double k = Math.max(b.hover.value, b.press.value);
            if (k > 0) {
                g.fillCircle(0, 0, r, Colors.withAlpha(0xFFFFFF, 0.08 * k));
            }
            Draw.chevron(g, -1, 0, r * 0.36, Theme.TEXT, false);
        };
        nameBtn = ui.add(new Button("", null, Theme.MAGENTA, this::editName));
        nameBtn.painter = (g, b) -> {
            double k = Math.max(b.hover.value, b.press.value);
            if (k > 0) {
                g.fillRoundRect(-b.w / 2, -b.h / 2, b.w, b.h, 14 * u, Colors.withAlpha(0xFFFFFF, 0.05 * k));
            }
        };
        connectBtn = ui.add(new Button("Mit GitHub verbinden", Icon.SAVE, Theme.ACCENT, this::connect));
        connectBtn.style = Button.Style.PRIMARY;
        helpBtn = ui.add(new Button("Token erstellen (GitHub öffnen)", null, Theme.ACCENT,
                () -> app.platform.openUrl(TOKEN_URL)));
        helpBtn.style = Button.Style.TEXT;
        syncBtn = ui.add(new Button("Jetzt abgleichen", Icon.LOOP, Theme.ACCENT, () -> app.cloud.sync()));
        syncBtn.style = Button.Style.PRIMARY;
        disconnectBtn = ui.add(new Button("Trennen", Icon.CROSS, Theme.RED, () -> {
            app.cloud.disconnect();
            say("Getrennt – dein Spielstand bleibt auf diesem Gerät.", Theme.YELLOW);
        }));
        disconnectBtn.style = Button.Style.TEXT;
        copyBtn = ui.add(new Button("Code kopieren", Icon.SAVE, Theme.ACCENT, this::exportCode));
        copyBtn.style = Button.Style.TEXT;
        pasteBtn = ui.add(new Button("Code einfügen", Icon.PLUS, Theme.ACCENT, this::importCode));
        pasteBtn.style = Button.Style.TEXT;
        for (Button b : new Button[] {connectBtn, syncBtn}) {
            b.fontScale = 1.05;
        }
        for (Button b : new Button[] {helpBtn, disconnectBtn, copyBtn, pasteBtn}) {
            b.fontScale = 0.95;
        }
    }

    // ------------------------------------------------------------------------------------------ Aktionen

    private void say(String msg, int color) {
        toast = msg;
        toastColor = color;
        toastTime = 3.2;
    }

    private void editName() {
        // Der Name ist weltweit eindeutig (Arena, Bestenlisten) – geprüft wird auf der eigenen Seite.
        app.goTo(new NameScene(app, false));
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
        double m = Kit.MARGIN * u;
        double bs = 44 * u;
        cw = Math.min(vp.safeW() - 2 * m, 640 * u);
        cx = vp.insetL + (vp.safeW() - cw) / 2;
        back.bounds(cx - 12 * u, vp.insetT + 10 * u, bs, bs);
        top = vp.insetT + 10 * u + bs + 14 * u;
        bottom = vp.h - vp.insetB - 8 * u;
        double gap = Kit.SECTION_GAP * u;
        double y = 6 * u;
        yCard = y;
        hCard = 170 * u;
        y += hCard + gap;
        yStats = y;
        hStats = 38 * u + 2 * 74 * u;
        y += hStats + gap;
        yTowers = y;
        hTowers = 38 * u + TowerType.values().length * TOWER_ROW * u + 92 * u;
        y += hTowers + gap;
        yCloud = y;
        hCloud = cloudHeight();
        y += hCloud + gap;
        yBackup = y;
        hBackup = 38 * u + 52 * u + 52 * u + 40 * u;
        y += hBackup;
        contentH = y + 30 * u;
        scrollMax = Math.max(0, contentH - (bottom - top));
        scroll = Mathx.clamp(scroll, 0, scrollMax);
        placeButtons();
    }

    private static final double TOWER_ROW = 60;

    private double cloudHeight() {
        boolean connected = app.cloud.connected();
        return connected ? 38 * u + 56 * u + Kit.BUTTON * u + 36 * u
                : 38 * u + 5 * 21 * u + 18 * u + Kit.BUTTON * u + 8 * u + 48 * u + 30 * u;
    }

    private void placeButtons() {
        double off = top - scroll;
        double bh = Kit.BUTTON * u;
        // Name antippen: Name und Titel neben dem Level-Ring
        nameBtn.bounds(cx + 96 * u, off + yCard, cw - 96 * u, 70 * u);
        boolean connected = app.cloud.connected();
        double bw = cw;
        connectBtn.visible = !connected;
        helpBtn.visible = !connected;
        syncBtn.visible = connected;
        disconnectBtn.visible = connected;
        if (!connected) {
            double y = off + yCloud + 38 * u + 5 * 21 * u + 18 * u;
            connectBtn.bounds(cx, y, bw, bh);
            helpBtn.bounds(cx, y + bh + 8 * u, bw, 48 * u);
        } else {
            double y = off + yCloud + 38 * u + 56 * u;
            double w1 = bw * 0.64;
            syncBtn.bounds(cx, y, w1, bh);
            disconnectBtn.bounds(cx + w1 + 8 * u, y, bw - w1 - 8 * u, bh);
        }
        double y2 = off + yBackup + 38 * u + 52 * u;
        double half = (bw - 8 * u) / 2;
        copyBtn.bounds(cx, y2, half, 52 * u);
        pasteBtn.bounds(cx + half + 8 * u, y2, half, 52 * u);
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

        double headBottom = top - 4 * u;
        g.fillRect(0, 0, vp.w, headBottom, Theme.BG);
        for (int i = 0; i < 6; i++) {
            g.fillRect(0, headBottom + i * 2 * u, vp.w, 2 * u, Colors.withAlpha(0x000000, 0.85 * (1 - i / 6.0)));
        }
        g.text("Profil", back.x + back.w + 2 * u, back.cy() + 1 * u, Kit.T_TITLE * 0.9 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        Kit.iconValueRight(g, cx + cw, back.cy(), Icon.CRYSTAL, Fmt.compact(app.progress.crystals()), Theme.CRYSTAL,
                Theme.TEXT, 18 * u, u);
        back.render(g);

        if (toastTime > 0 && toast != null) {
            double a = Mathx.clamp01(toastTime / 0.4);
            double fs = Math.max(11, 16 * u);
            double w = Math.min(vp.safeW() - 24 * u, g.textWidth(toast, fs, true) + 60 * u);
            double h = 44 * u;
            double y = vp.h - vp.insetB - h - 16 * u;
            g.save();
            g.alpha(a);
            g.fillRoundRect(vp.w / 2 - w / 2, y, w, h, h / 2, Theme.SURFACE3);
            g.fillCircle(vp.w / 2 - w / 2 + h * 0.5, y + h / 2, h * 0.12, toastColor);
            double shrink = Math.min(1, (w - 20 * u - h * 0.5) / Math.max(1, g.textWidth(toast, fs, true)));
            g.text(toast, vp.w / 2 + h * 0.25, y + h / 2, fs * shrink, Theme.TEXT, Gfx.ALIGN_CENTER, true);
            g.restore();
        }
    }

    private void drawBackdrop(Gfx g, Viewport vp) {
    }

    private void drawScrollBar(Gfx g) {
        if (scrollMax <= 0) {
            return;
        }
        double trackH = bottom - top;
        double barH = Math.max(30 * u, trackH * trackH / (trackH + scrollMax));
        double y = top + (trackH - barH) * (scroll / scrollMax);
        g.fillRoundRect(app.vp.w - app.vp.insetR - 5 * u, y, 2.5 * u, barH, 1.5 * u, Colors.withAlpha(0xFFFFFF, 0.22));
    }

    private void drawCard(Gfx g) {
        Progress p = app.progress;
        double r = 40 * u;
        double bx = cx + r;
        double by = yCard + 6 * u + r;
        Kit.ring(g, bx, by - 4 * u, r, Math.max(0.01, p.levelFraction()), Theme.MAGENTA, Integer.toString(p.level()), u);
        g.text("LEVEL", bx, by + r * 0.42, Kit.T_KICKER * 0.8 * u, Theme.MUTED, Gfx.ALIGN_CENTER, true);
        // Name (antippen zum Ändern) und Titel
        double nx = cx + 96 * u;
        Draw.cut(g, p.displayName(), nx, yCard + 24 * u, 26 * u, cw - 96 * u - 34 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        Icons.draw(g, Icon.PENCIL, cx + cw - 10 * u, yCard + 24 * u, 8 * u, Theme.MUTED, 0);
        g.text(p.title(), nx, yCard + 52 * u, Kit.T_BODY * u, Colors.lighten(Theme.MAGENTA, 0.15), Gfx.ALIGN_LEFT, true);
        String badge = p.seasonBadge();
        if (badge != null) {
            double tw = g.textWidth(p.title(), Kit.T_BODY * u, true);
            Icons.draw(g, Icon.TROPHY, nx + tw + 18 * u, yCard + 52 * u, 8 * u, Theme.YELLOW, 0);
            g.text(badge, nx + tw + 32 * u, yCard + 52 * u, Kit.T_CAPTION * u, Theme.YELLOW, Gfx.ALIGN_LEFT, true);
        }
        // XP-Balken über die ganze Breite
        double barY = yCard + 112 * u;
        String xp = p.level() >= Progress.MAX_LEVEL ? "Höchststufe" : Fmt.compact(p.xpIntoLevel()) + " / " + Fmt.compact(p.xpSpan()) + " XP";
        g.text(xp, cx, barY - 14 * u, Kit.T_CAPTION * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        int next = p.nextUnlockLevel();
        String hint = next > 0 ? "Nächster Turm ab Level " + next : "Alle Türme frei";
        g.text(hint, cx + cw, barY - 14 * u, Kit.T_CAPTION * u, Theme.GREEN, Gfx.ALIGN_RIGHT, false);
        Kit.meter(g, cx, barY, cw, p.levelFraction(), Theme.MAGENTA, u);
        String store = app.cloud.connected() ? "In der Cloud gesichert als @" + app.cloud.user() : "Nur auf diesem Gerät gespeichert";
        Icons.draw(g, app.cloud.connected() ? Icon.CHECK : Icon.SAVE, cx + 7 * u, yCard + hCard - 22 * u, 6 * u,
                app.cloud.connected() ? Theme.GREEN : Theme.MUTED, 0);
        g.text(store, cx + 22 * u, yCard + hCard - 22 * u, Kit.T_CAPTION * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
    }

    private void drawStats(Gfx g) {
        Progress p = app.progress;
        Kit.section(g, "Statistik", null, 0, cx, yStats, cw, u);
        String[] labels = {"Wellen besiegt", "Abschüsse", "Siege / Spiele", "Medaillen von " + 4 * neontd.level.Levels.builtins().size()};
        String[] vals = {Fmt.compact(p.wavesCleared), Fmt.compact(p.kills), p.gamesWon + " / " + p.gamesPlayed,
            Integer.toString(p.totalMedals())};
        double cell = cw / 2;
        for (int i = 0; i < 4; i++) {
            double x = cx + (i % 2) * cell;
            double y = yStats + 38 * u + (i / 2) * 74 * u;
            Kit.stat(g, vals[i], labels[i], x, y, Theme.TEXT, u);
        }
    }

    private void drawTowers(Gfx g) {
        Progress p = app.progress;
        int open = 0;
        for (TowerType t : TowerType.values()) {
            if (p.isUnlocked(t)) {
                open++;
            }
        }
        Kit.section(g, "Türme", open + " / " + TowerType.values().length + " frei", Theme.MUTED, cx, yTowers, cw, u);
        double y0 = yTowers + 38 * u;
        double rowH = TOWER_ROW * u;
        TowerType[] order = TowerType.values().clone();
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
            boolean isOpen = p.isUnlocked(t);
            double y = y0 + i * rowH;
            double ix = cx + 18 * u;
            g.save();
            g.alpha(isOpen ? 1 : 0.4);
            TowerArt.drawIcon(g, t, ix, y + rowH / 2, 17 * u, time);
            g.restore();
            double tx = cx + 52 * u;
            double right = cx + cw;
            double vw;
            if (isOpen) {
                Icons.draw(g, Icon.CHECK, right - 8 * u, y + rowH / 2, 8 * u, Theme.GREEN, 0);
                vw = 24 * u;
            } else {
                String need = t.tier == 2 && p.level() >= t.unlockLevel ? "Im Shop" : "Level " + t.unlockLevel;
                double ns = Kit.T_BODY * u;
                g.text(need, right, y + rowH / 2, ns, Theme.MUTED, Gfx.ALIGN_RIGHT, true);
                double nw = g.textWidth(need, ns, true);
                Draw.lock(g, right - nw - 14 * u, y + rowH / 2, 6 * u);
                vw = nw + 30 * u;
            }
            Draw.cut(g, t.label.charAt(0) + t.label.substring(1).toLowerCase(), tx, y + rowH / 2 - 10 * u, Kit.T_ROW * u,
                    right - vw - tx, isOpen ? Theme.TEXT : Theme.MUTED, Gfx.ALIGN_LEFT, true);
            Draw.cut(g, t.tagline, tx, y + rowH / 2 + 12 * u, Kit.T_CAPTION * u, right - vw - tx, Theme.MUTED, Gfx.ALIGN_LEFT,
                    false);
            if (i < order.length - 1) {
                Kit.hairline(g, tx, y + rowH - 1, right - tx);
            }
        }
        double y = y0 + order.length * rowH + 22 * u;
        double w1 = Kit.iconValue(g, cx, y, Icon.GEM, "+" + p.startMoneyBonus() + " Startgeld", Theme.MONEY, Theme.TEXT,
                Kit.T_BODY * u, u);
        Kit.iconValue(g, cx + w1 + 24 * u, y, Icon.HEART, "+" + p.startLivesBonus() + " Start-Leben", Theme.LIFE, Theme.TEXT,
                Kit.T_BODY * u, u);
        Kit.paragraph(g, "+10 Startgeld je Level (bis +300), +1 Leben je 5 Level (bis +5), mehr durch Forschung im Shop.",
                cx, y + 16 * u, cw, Kit.T_CAPTION * u, Theme.MUTED, u);
    }

    private void drawCloud(Gfx g) {
        boolean connected = app.cloud.connected();
        int c = app.cloud.status == GistSync.Status.ERROR ? Theme.RED
                : (app.cloud.status == GistSync.Status.OK ? Theme.GREEN : Theme.YELLOW);
        Kit.section(g, "Cloud-Speicher", "GitHub", Theme.MUTED, cx, yCloud, cw, u);
        double x = cx;
        double y = yCloud + 38 * u;
        if (!connected) {
            Kit.paragraph(g, "Sichert Profil, eigene Level und laufende Spiele in einem privaten GitHub-Gist – und bringt "
                    + "sie auf jedes Gerät, auch auf die Home-Bildschirm-App am iPhone. Das Token bleibt nur auf diesem Gerät.",
                    x, y, cw, Kit.T_BODY * 0.95 * u, Theme.MUTED, u);
        } else {
            g.text("Verbunden als @" + app.cloud.user(), x, y + 10 * u, Kit.T_ROW * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
            String last = app.cloud.lastOk() > 0 ? "Zuletzt abgeglichen " + ago(app.saves.now() - app.cloud.lastOk())
                    : "Noch nicht abgeglichen";
            g.text(last, x, y + 34 * u, Kit.T_CAPTION * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        }
        String msg = app.cloud.message;
        if (!msg.isEmpty()) {
            Draw.fit(g, msg, x, yCloud + hCloud - 12 * u, Kit.T_CAPTION * u, cw, c, Gfx.ALIGN_LEFT, true);
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
        Kit.section(g, "Sicherung per Code", "ohne Konto", Theme.MUTED, cx, yBackup, cw, u);
        Kit.paragraph(g, "Kopiere deinen Stand als Text und füge ihn auf einem anderen Gerät ein.", cx, yBackup + 38 * u, cw,
                Kit.T_BODY * 0.95 * u, Theme.MUTED, u);
        g.text("Enthält Profil und eigene Level, nicht das laufende Spiel.", cx, yBackup + hBackup - 14 * u,
                Kit.T_CAPTION * u, Colors.withAlpha(0xFFFFFF, 0.4), Gfx.ALIGN_LEFT, false);
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
    public boolean swipeBack() {
        return true;
    }

    @Override
    public boolean back() {
        return false;
    }
}

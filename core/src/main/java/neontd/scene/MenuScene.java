package neontd.scene;

import neontd.app.App;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Neon;
import neontd.gfx.NeonText;
import neontd.gfx.Theme;
import neontd.ui.Button;
import neontd.ui.Easing;
import neontd.ui.Smooth;
import neontd.ui.Ui;
import neontd.ui.Viewport;

/** Hauptmenü: Leuchtschild-Titel vor einer laufenden Demo, zwei große Schaltflächen. */
public final class MenuScene extends Scene {
    private final Ui ui = new Ui();
    private final DemoWorld demo = new DemoWorld();
    private final Smooth titleIn = new Smooth(0, 3.2);
    private final Button play;
    private final Button editor;
    private final Button quit;
    private final Button profile;
    private final Button shop;
    private final Button missions;
    private final Button trophies;
    private final Button settings;
    private final Button crystals;
    private double time;
    private double titleY;
    private double titleH;

    public MenuScene(App app) {
        super(app);
        play = ui.add(new Button("SPIELEN", Icons.Icon.PLAY, Theme.CYAN, () -> app.goTo(new LevelSelectScene(app))));
        editor = ui.add(new Button("LEVEL-EDITOR", Icons.Icon.EDITOR, Theme.MAGENTA,
                () -> app.goTo(new EditorScene(app, null))));
        quit = ui.add(new Button("", Icons.Icon.CROSS, Theme.TEXT_DIM, () -> app.platform.quit()));
        profile = ui.add(new Button("", null, Theme.MAGENTA, () -> app.goTo(new ProfileScene(app))));
        profile.painter = this::paintProfile;
        profile.appearAfter(0.6);
        shop = ui.add(new Button("SHOP", Icons.Icon.CRYSTAL, Theme.CRYSTAL, () -> app.goTo(new ShopScene(app))));
        missions = ui.add(new Button("MISSIONEN", Icons.Icon.FLAG, Theme.GREEN, () -> app.goTo(new MissionsScene(app))));
        trophies = ui.add(new Button("ERFOLGE", Icons.Icon.TROPHY, Theme.YELLOW,
                () -> app.goTo(new AchievementsScene(app))));
        settings = ui.add(new Button("EINSTELLUNGEN", Icons.Icon.GEAR, Theme.TEXT_DIM,
                () -> app.goTo(new SettingsScene(app))));
        shop.painter = (g, b) -> paintTile(g, b, false);
        missions.painter = (g, b) -> paintTile(g, b, app.progress.claimable() > 0);
        trophies.painter = (g, b) -> paintTile(g, b, false);
        settings.painter = (g, b) -> paintTile(g, b, false);
        shop.appearAfter(0.62);
        missions.appearAfter(0.68);
        trophies.appearAfter(0.74);
        settings.appearAfter(0.8);
        crystals = ui.add(new Button("", Icons.Icon.CRYSTAL, Theme.CRYSTAL, () -> app.goTo(new ShopScene(app))));
        crystals.painter = this::paintCrystals;
        crystals.appearAfter(0.7);
        play.neonFont = true;
        editor.neonFont = true;
        quit.visible = app.platform.canQuit();
        play.appearAfter(0.35);
        editor.appearAfter(0.5);
        quit.appearAfter(0.7);
        titleIn.target = 1;
    }

    @Override
    public void layout() {
        Viewport vp = app.vp;
        double u = vp.u;
        double cx = vp.w / 2;
        boolean land = vp.landscape();
        titleH = Math.min(84 * u, vp.safeW() * 0.86 / 5.2);
        titleY = vp.safeY() + vp.safeH() * (land ? 0.27 : 0.25);
        double bw = Math.min(vp.safeW() * 0.86, 380 * u);
        boolean shortScreen = vp.safeH() < 560;
        double bh = shortScreen ? Math.min(62 * u, vp.safeH() * 0.13) : 62 * u;
        double gap = shortScreen ? 10 * u : 16 * u;
        double top = vp.safeY() + vp.safeH() * (land ? (shortScreen ? 0.45 : 0.50) : 0.46);
        play.bounds(cx - bw / 2, top, bw, bh);
        editor.bounds(cx - bw / 2, top + bh + gap, bw, bh);
        // Zeile mit vier Kacheln: Shop, Missionen, Erfolge, Einstellungen
        double th = shortScreen ? Math.min(54 * u, vp.safeH() * 0.115) : 58 * u;
        double tg = 8 * u;
        double tw = (bw - 3 * tg) / 4;
        double ty = top + 2 * (bh + gap);
        Button[] row = {shop, missions, trophies, settings};
        for (int i = 0; i < row.length; i++) {
            row[i].bounds(cx - bw / 2 + i * (tw + tg), ty, tw, th);
        }
        double qs = 44 * u;
        quit.bounds(vp.w - vp.insetR - qs - 16 * u, vp.safeY() + 14 * u, qs, qs);
        double cwid = Math.min(120 * u, vp.safeW() * 0.3);
        double cxr = vp.w - vp.insetR - 16 * u - (quit.visible ? qs + 10 * u : 0);
        crystals.bounds(cxr - cwid, vp.safeY() + 14 * u + (qs - 38 * u) / 2, cwid, 38 * u);
        double pw = Math.min(vp.safeW() * 0.56, 230 * u);
        profile.bounds(vp.insetL + 16 * u, vp.safeY() + 14 * u, pw, 46 * u);
    }

    /** Quadratische Kachel mit Symbol und kleiner Beschriftung; roter Punkt, wenn etwas abzuholen ist. */
    private void paintTile(Gfx g, Button b, boolean badge) {
        double w = b.w;
        double h = b.h;
        double hv = b.hover.value;
        double r = Math.min(w, h) * 0.22;
        g.fillRoundRect(-w / 2, -h / 2, w, h, r, Colors.withAlpha(Theme.PANEL, 0.92));
        g.fillRoundRect(-w / 2, -h / 2, w, h, r, Colors.withAlpha(b.color, 0.05 + 0.1 * hv + 0.2 * b.press.value));
        Neon.roundRect(g, -w / 2, -h / 2, w, h, r, 1.8, Colors.withAlpha(b.color, 0.9), 3 + 6 * hv);
        double ir = Math.min(h * 0.26, w * 0.2);
        Icons.draw(g, b.icon, 0, -h * 0.14, ir, b.color, 4);
        double fs = Math.max(8, Math.min(h * 0.18, w * 0.15));
        double lw = g.textWidth(b.label, fs, true);
        double k = lw > w * 0.92 ? w * 0.92 / lw : 1;
        g.text(b.label, 0, h * 0.3, fs * k, Theme.TEXT, Gfx.ALIGN_CENTER, true);
        if (badge) {
            double pr = Math.max(4, h * 0.08);
            g.fillCircle(w / 2 - pr * 1.6, -h / 2 + pr * 1.6, pr, Theme.RED);
            Neon.circle(g, w / 2 - pr * 1.6, -h / 2 + pr * 1.6, pr + 1.5, 1.4, Colors.withAlpha(Theme.RED, 0.6), 4);
        }
    }

    /** Kristall-Guthaben oben rechts – antippen öffnet den Shop. */
    private void paintCrystals(Gfx g, Button b) {
        double w = b.w;
        double h = b.h;
        double hv = b.hover.value;
        g.fillRoundRect(-w / 2, -h / 2, w, h, h / 2, Colors.withAlpha(Theme.PANEL, 0.9));
        Neon.roundRect(g, -w / 2, -h / 2, w, h, h / 2, 1.8, Colors.withAlpha(Theme.CRYSTAL, 0.9), 3 + 6 * hv);
        Icons.draw(g, Icons.Icon.CRYSTAL, -w / 2 + h * 0.55, 0, h * 0.3, Theme.CRYSTAL, 3);
        String t = neontd.ui.Fmt.compact(app.progress.crystals());
        double fs = h * 0.42;
        double lw = g.textWidth(t, fs, true);
        double avail = w - h * 1.1;
        g.text(t, -w / 2 + h * 0.98, 0, fs * Math.min(1, avail / Math.max(1, lw)), Theme.TEXT, Gfx.ALIGN_LEFT, true);
    }

    /** Kleine Profilkarte oben links: Level, Name, XP-Balken – antippen öffnet das Profil. */
    private void paintProfile(Gfx g, Button b) {
        double w = b.w;
        double h = b.h;
        double hv = b.hover.value;
        double r = h * 0.3;
        neontd.progress.Progress p = app.progress;
        g.fillRoundRect(-w / 2, -h / 2, w, h, r, Colors.withAlpha(Theme.PANEL, 0.9));
        Neon.roundRect(g, -w / 2, -h / 2, w, h, r, 1.8, Colors.withAlpha(Theme.MAGENTA, 0.85), 3 + 6 * hv);
        double cr = h * 0.32;
        double cx = -w / 2 + h * 0.2 + cr;
        Neon.circle(g, cx, -h * 0.04, cr, 1.8, Theme.MAGENTA, 5);
        g.text(Integer.toString(p.level()), cx, -h * 0.04, cr * 1.05, Theme.TEXT, Gfx.ALIGN_CENTER, true);
        double tx = cx + cr + h * 0.2;
        double fs = Math.min(h * 0.34, (w / 2 - tx - h * 0.2) * 2 / Math.max(6, p.displayName().length() * 0.62));
        g.text(p.displayName(), tx, -h * 0.17, Math.max(9, fs), Theme.TEXT, Gfx.ALIGN_LEFT, true);
        g.text(p.title(), tx, h * 0.1, Math.max(8, h * 0.22), Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        double bw = w - (tx + w / 2) - h * 0.25;
        double by = h * 0.34;
        g.fillRoundRect(tx, by - 2, bw, 4, 2, Colors.withAlpha(Theme.MAGENTA, 0.22));
        double f = p.levelFraction();
        if (f > 0) {
            g.fillRoundRect(tx, by - 2, Math.max(4, bw * f), 4, 2, Theme.MAGENTA);
        }
    }

    @Override
    public void update(double dt) {
        time += dt;
        titleIn.update(dt);
        demo.update(dt);
        ui.update(dt);
    }

    @Override
    public void render(Gfx g) {
        Viewport vp = app.vp;
        g.fillRect(0, 0, vp.w, vp.h, Theme.BLACK);
        g.save();
        g.alpha(0.5);
        demo.render(g, vp.w, vp.h);
        g.restore();

        // Dunkler, weich auslaufender Schleier hinter dem Titel, damit Schrift und Schaltflächen gut lesbar sind.
        // Bewusst aus wenigen geschachtelten Rechtecken statt eines Vollbild-Farbverlaufs (billig auf jedem Gerät).
        double cx = vp.w / 2;
        double top = titleY - titleH * 0.95;
        double bottom = shop.y + shop.h + 36 * vp.u;
        double halfW = Math.min(vp.w * 0.5, Math.max(play.w * 0.5 + 50 * vp.u, titleH * 3.4));
        for (int i = 0; i < 5; i++) {
            double grow = (4 - i) * 22 * vp.u;
            g.fillRoundRect(cx - halfW - grow, top - grow, (halfW + grow) * 2, bottom - top + grow * 2,
                    40 * vp.u + grow, Colors.withAlpha(0x000000, 0.2));
        }

        double tin = Easing.outCubic(Math.min(1, titleIn.value));
        double breathe = 0.5 + 0.5 * Math.sin(time * 1.6);
        g.save();
        g.alpha(tin);
        g.translate(cx, titleY);
        double sc = 0.9 + 0.1 * tin;
        g.scale(sc, sc);
        NeonText.draw(g, "NEON TD", 0, 0, titleH, Theme.CYAN, 10 + 6 * breathe, 0.75, time);
        NeonText.draw(g, "TOWER DEFENSE", 0, titleH * 0.82, titleH * 0.26, Colors.withAlpha(Theme.MAGENTA, 0.95), 5, 0, time);
        g.restore();

        ui.render(g);

        g.text("v0.1", vp.w - vp.insetR - 12 * vp.u, vp.h - vp.insetB - 12 * vp.u, 11 * vp.u, Theme.TEXT_DIM,
                Gfx.ALIGN_RIGHT, false);
    }

    @Override
    public void pointerDown(double x, double y, boolean touch) {
        ui.pointerDown(x, y);
    }

    @Override
    public void pointerMove(double x, double y, boolean pressed, boolean touch) {
        ui.pointerMove(x, y, !touch);
    }

    @Override
    public void pointerUp(double x, double y, boolean touch) {
        ui.pointerUp(x, y);
        if (touch) {
            ui.pointerMove(-100, -100, false);
        }
    }

    @Override
    public void pointerCancel() {
        ui.cancel();
    }

    @Override
    public boolean key(String code, boolean ctrl) {
        if (code.equals("Enter") || code.equals("Space")) {
            play.onClick.run();
            return true;
        }
        if (code.equals("KeyE")) {
            editor.onClick.run();
            return true;
        }
        return false;
    }
}

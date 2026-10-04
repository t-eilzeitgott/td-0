package neontd.scene;

import neontd.app.App;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
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
    private double time;
    private double titleY;
    private double titleH;

    public MenuScene(App app) {
        super(app);
        play = ui.add(new Button("SPIELEN", Icons.Icon.PLAY, Theme.CYAN, () -> app.goTo(new LevelSelectScene(app))));
        editor = ui.add(new Button("LEVEL-EDITOR", Icons.Icon.EDITOR, Theme.MAGENTA,
                () -> app.goTo(new EditorScene(app, null))));
        quit = ui.add(new Button("", Icons.Icon.CROSS, Theme.TEXT_DIM, () -> app.platform.quit()));
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
        double bh = 62 * u;
        double gap = 16 * u;
        double top = vp.safeY() + vp.safeH() * (land ? 0.50 : 0.46);
        play.bounds(cx - bw / 2, top, bw, bh);
        editor.bounds(cx - bw / 2, top + bh + gap, bw, bh);
        double qs = 44 * u;
        quit.bounds(vp.w - vp.insetR - qs - 16 * u, vp.safeY() + 14 * u, qs, qs);
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
        double bottom = editor.y + editor.h + 36 * vp.u;
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

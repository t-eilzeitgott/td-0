package neontd.scene;

import neontd.app.App;
import neontd.fx.Effects;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Icons.Icon;
import neontd.gfx.Neon;
import neontd.gfx.NeonText;
import neontd.gfx.Theme;
import neontd.level.LevelDef;
import neontd.math.Mathx;
import neontd.physics.FixedTimestep;
import neontd.render.EnemyArt;
import neontd.render.GameFx;
import neontd.render.HpLabel;
import neontd.render.TowerArt;
import neontd.render.WorldView;
import neontd.sim.EnemyType;
import neontd.sim.Tower;
import neontd.sim.TowerType;
import neontd.sim.UpgradeTrack;
import neontd.sim.WaveDef;
import neontd.sim.World;
import neontd.ui.Button;
import neontd.ui.Draw;
import neontd.ui.Easing;
import neontd.ui.Smooth;
import neontd.ui.Ui;
import neontd.ui.Viewport;

/**
 * Die eigentliche Spielszene: Karte mit Simulation, HUD, Turm-Shop (Ziehen oder Tippen), Upgrade-Panel,
 * Wellenstart, Tempo, Pause sowie Sieg- und Niederlage-Anzeige. Das Layout passt sich Quer- und Hochformat an.
 */
public final class GameScene extends Scene {
    private enum Overlay { NONE, PAUSE, WON, LOST }

    private static final TowerType[] TYPES = TowerType.values();
    private static final double[] SPEEDS = {1, 2, 3};

    private final LevelDef level;
    /** Wenn gesetzt, kehrt "Zurück" zum Editor mit diesem Level zurück (Testspiel). */
    private final LevelDef editorReturn;

    private final Effects fx = new Effects();
    private final Listener listener = new Listener(fx);
    private final World world;
    private final FixedTimestep stepper = new FixedTimestep(World.STEP, 6);

    private final Ui ui = new Ui();
    private final Ui overlayUi = new Ui();
    private final Button[] cards = new Button[TYPES.length];
    private Button startBtn;
    private Button speedBtn;
    private Button pauseBtn;
    private Button autoBtn;
    private final Button[] trackBtns = new Button[3];
    private Button sellBtn;
    private Button modeBtn;

    // Layout
    private double mapX;
    private double mapY;
    private double mapScale = 1;
    private double hudX;
    private double hudY;
    private double hudW;
    private double hudH;
    private double panelX;
    private double panelY;
    private double panelW;
    private double panelH;
    private double shopX;
    private double shopY;
    private double shopW;
    private double shopH;
    private double upX;
    private double upY;
    private double upW;
    private double upH;
    private boolean portrait;

    // Spielzustand
    private int speedIndex;
    private Overlay overlay = Overlay.NONE;
    private double overlayIn;
    private double endDelay;
    private Tower selected;
    private Tower hoverTower;
    private TowerType placing;
    private double ghostX;
    private double ghostY;
    private boolean ghostOnMap;
    private boolean ghostValid;
    private boolean touchMode;
    private boolean cardDown;
    private boolean dragging;
    private TowerType dragType;
    private double dragX0;
    private double dragY0;
    private boolean mapPressed;
    private double sellArmed;
    private double time;
    private double celebrate;
    private int labelWave = -1;
    private Button pressedPanel;

    // Anzeige
    private final Smooth moneyShown = new Smooth(0, 9);
    private final Smooth livesPulse = new Smooth(0, 6);
    private double moneyDeny;
    private String bannerTitle;
    private String bannerSub;
    private int bannerColor;
    private double bannerTime;
    private double bannerTotal = 2.4;
    private boolean introBanner;
    private String toast;
    private int toastColor;
    private double toastTime;

    public GameScene(App app, LevelDef level) {
        this(app, level, null);
    }

    public GameScene(App app, LevelDef level, LevelDef editorReturn) {
        super(app);
        this.level = level;
        this.editorReturn = editorReturn;
        this.world = new World(level, listener);
        moneyShown.snap(world.money);
        buildUi();
    }

    // ------------------------------------------------------------------------------------------ Aufbau

    private void buildUi() {
        for (int i = 0; i < TYPES.length; i++) {
            final TowerType type = TYPES[i];
            final int idx = i;
            Button b = new Button("", null, type.color, null);
            b.painter = (g, btn) -> paintCard(g, btn, type, idx);
            b.appearAfter(0.15 + 0.07 * i);
            cards[i] = b;
        }
        startBtn = ui.add(new Button("START", Icon.PLAY, Theme.GREEN, this::startWave));
        speedBtn = ui.add(new Button("1x", null, Theme.CYAN, this::cycleSpeed));
        pauseBtn = ui.add(new Button("", Icon.PAUSE, Theme.VIOLET, () -> setOverlay(Overlay.PAUSE)));
        autoBtn = ui.add(new Button("", Icon.LOOP, Theme.YELLOW, () -> {
            world.autoStart = !world.autoStart;
            showToast(world.autoStart ? "Automatischer Wellenstart AN" : "Automatischer Wellenstart AUS",
                    Theme.YELLOW);
            if (world.autoStart && world.activeWaves() == 0) {
                startWave();
            }
        }));
        startBtn.fontScale = 0.9;
        speedBtn.fontScale = 0.9;
        for (int i = 0; i < 3; i++) {
            final UpgradeTrack track = UpgradeTrack.values()[i];
            Button b = new Button("", null, track.color, () -> buyUpgrade(track));
            b.painter = (g, btn) -> paintTrackRow(g, btn, track);
            trackBtns[i] = b;
        }
        sellBtn = new Button("VERKAUFEN", Icon.GEM, Theme.MONEY, this::sellSelected);
        sellBtn.painter = this::paintSell;
        modeBtn = new Button("", Icon.TARGET, Theme.TEXT_DIM, () -> {
            if (selected != null) {
                selected.mode = selected.mode.next();
            }
        });
        modeBtn.painter = this::paintMode;
    }

    @Override
    public void onEnter() {
        showBanner(level.name.toUpperCase(), "Platziere Türme und starte die erste Welle", Theme.CYAN, 3.2);
        introBanner = true;
    }

    /** Das Begrüßungs-Banner verschwindet, sobald der Spieler loslegt, damit es nichts verdeckt. */
    private void dismissIntroBanner() {
        if (introBanner) {
            introBanner = false;
            bannerTime = Math.min(bannerTime, 0.45);
        }
    }

    // ---------------------------------------------------------------------------------------- Layout

    @Override
    public void layout() {
        Viewport vp = app.vp;
        double u = vp.u;
        double m = 8 * u;
        portrait = !vp.landscape();
        double ctrlH = 52 * u;
        double mapAvX;
        double mapAvY;
        double mapAvW;
        double mapAvH;
        double ctrlX;
        double ctrlY;
        double ctrlW;
        if (!portrait) {
            double pw = Mathx.clamp(vp.safeW() * 0.26, 210 * Math.min(1, u * 1.1), 400);
            double px = vp.w - vp.insetR - m - pw;
            double py = vp.insetT + m;
            double ph = vp.safeH() - 2 * m;
            hudX = px;
            hudY = py;
            hudW = pw;
            hudH = 2 * 34 * u + 6 * u;
            ctrlX = px;
            ctrlW = pw;
            ctrlY = py + ph - ctrlH;
            panelX = px;
            panelW = pw;
            panelY = hudY + hudH + m;
            panelH = ctrlY - m - panelY;
            shopX = panelX;
            shopY = panelY;
            shopW = panelW;
            shopH = panelH;
            upX = panelX;
            upY = panelY;
            upW = panelW;
            upH = panelH;
            mapAvX = vp.insetL + m;
            mapAvY = vp.insetT + m;
            mapAvW = px - m - mapAvX;
            mapAvH = ph;
        } else {
            hudX = vp.insetL + m;
            hudY = vp.insetT + m;
            hudW = vp.safeW() - 2 * m;
            hudH = 40 * u;
            mapAvX = hudX;
            mapAvW = hudW;
            mapAvY = hudY + hudH + m;
            mapAvH = mapAvW * world.height / world.width;
            ctrlX = hudX;
            ctrlW = hudW;
            ctrlY = vp.h - vp.insetB - m - ctrlH;
            panelX = hudX;
            panelW = hudW;
            panelY = mapAvY + mapAvH + m;
            panelH = ctrlY - m - panelY;
            shopX = panelX;
            shopY = panelY;
            shopW = panelW;
            shopH = Math.min(panelH, 100 * u);
            upX = panelX;
            upW = panelW;
            upY = shopY + shopH + m;
            upH = panelY + panelH - upY;
        }
        mapScale = Math.min(mapAvW / world.width, mapAvH / world.height);
        mapX = mapAvX + (mapAvW - world.width * mapScale) / 2;
        mapY = mapAvY + (mapAvH - world.height * mapScale) / 2;

        // Steuerleiste
        double gap = 6 * u;
        double wStart = ctrlW * 0.38;
        double wRest = (ctrlW - wStart - gap * 3) / 3;
        startBtn.bounds(ctrlX, ctrlY, wStart, ctrlH);
        speedBtn.bounds(ctrlX + wStart + gap, ctrlY, wRest, ctrlH);
        autoBtn.bounds(ctrlX + wStart + gap * 2 + wRest, ctrlY, wRest, ctrlH);
        pauseBtn.bounds(ctrlX + wStart + gap * 3 + wRest * 2, ctrlY, wRest, ctrlH);

        // Shop
        if (!portrait) {
            double ch = Math.min(64 * u, (shopH - gap * (TYPES.length - 1)) / TYPES.length);
            for (int i = 0; i < TYPES.length; i++) {
                cards[i].bounds(shopX, shopY + i * (ch + gap), shopW, ch);
            }
        } else {
            double cw = (shopW - gap * (TYPES.length - 1)) / TYPES.length;
            for (int i = 0; i < TYPES.length; i++) {
                cards[i].bounds(shopX + i * (cw + gap), shopY, cw, shopH);
            }
        }
        layoutUpgradePanel();
        layoutOverlay();
    }

    private void layoutUpgradePanel() {
        double u = app.vp.u;
        double gap = 6 * u;
        double rows = 5.4;
        double rowH = Math.min(58 * u, (upH - gap * 4) / rows);
        double y = upY;
        sellBtn.bounds(upX + upW * 0.52, y, upW * 0.48, rowH * 0.8);
        y += rowH * 0.8 + gap;
        for (int i = 0; i < 3; i++) {
            trackBtns[i].bounds(upX, y, upW, rowH);
            y += rowH + gap;
        }
        modeBtn.bounds(upX, y, upW, rowH * 0.78);
    }

    private double toWorldX(double sx) {
        return (sx - mapX) / mapScale;
    }

    private double toWorldY(double sy) {
        return (sy - mapY) / mapScale;
    }

    private boolean onMap(double sx, double sy) {
        return sx >= mapX && sy >= mapY && sx <= mapX + world.width * mapScale
                && sy <= mapY + world.height * mapScale;
    }

    // ----------------------------------------------------------------------------------------- Aktionen

    private void startWave() {
        if (overlay != Overlay.NONE) {
            return;
        }
        dismissIntroBanner();
        if (!world.startNextWave()) {
            showToast("Keine weitere Welle", Theme.TEXT_DIM);
        }
    }

    private void cycleSpeed() {
        speedIndex = (speedIndex + 1) % SPEEDS.length;
        speedBtn.label = (int) SPEEDS[speedIndex] + "x";
    }

    private void setOverlay(Overlay o) {
        overlay = o;
        overlayIn = 0;
        overlayUi.clear();
        cancelPlacing();
        if (o == Overlay.NONE) {
            return;
        }
        String back = editorReturn != null ? "ZURÜCK ZUM EDITOR" : "HAUPTMENÜ";
        Runnable leave = () -> {
            if (editorReturn != null) {
                app.goTo(new EditorScene(app, editorReturn));
            } else {
                app.goTo(new MenuScene(app));
            }
        };
        Runnable again = () -> app.goTo(new GameScene(app, level, editorReturn));
        Button a;
        Button b;
        Button c = null;
        if (o == Overlay.PAUSE) {
            a = new Button("WEITER", Icon.PLAY, Theme.GREEN, () -> setOverlay(Overlay.NONE));
            b = new Button("NEU STARTEN", Icon.LOOP, Theme.YELLOW, again);
            c = new Button(back, Icon.HOME, Theme.MAGENTA, leave);
        } else {
            a = new Button("NOCHMAL", Icon.LOOP, o == Overlay.WON ? Theme.GREEN : Theme.YELLOW, again);
            b = new Button(back, Icon.HOME, Theme.MAGENTA, leave);
        }
        a.neonFont = true;
        b.neonFont = true;
        overlayUi.add(a).appearAfter(0.25);
        overlayUi.add(b).appearAfter(0.35);
        if (c != null) {
            c.neonFont = true;
            overlayUi.add(c).appearAfter(0.45);
        }
        layoutOverlay();
    }

    private void layoutOverlay() {
        double u = app.vp.u;
        double bw = Math.min(app.vp.safeW() * 0.84, 360 * u);
        double bh = 56 * u;
        double top = app.vp.h / 2 - 20 * u;
        for (int i = 0; i < overlayUi.buttons.size(); i++) {
            overlayUi.buttons.get(i).bounds(app.vp.w / 2 - bw / 2, top + i * (bh + 12 * u), bw, bh);
        }
    }

    private void select(Tower t) {
        selected = t;
        sellArmed = 0;
        if (t != null) {
            cancelPlacing();
        }
        layoutUpgradePanel();
    }

    private void cancelPlacing() {
        placing = null;
        dragging = false;
        cardDown = false;
        ghostOnMap = false;
    }

    private void buyUpgrade(UpgradeTrack track) {
        if (selected == null) {
            return;
        }
        int cost = world.upgradeCost(selected, track);
        if (cost < 0) {
            return;
        }
        if (!world.upgrade(selected, track)) {
            deny("Zu wenig Geld");
        }
    }

    private void sellSelected() {
        if (selected == null) {
            return;
        }
        if (sellArmed <= 0) {
            sellArmed = 2.2;
            return;
        }
        Tower t = selected;
        select(null);
        world.sell(t);
    }

    private void deny(String msg) {
        moneyDeny = 1;
        showToast(msg, Theme.RED);
    }

    private void showToast(String msg, int color) {
        toast = msg;
        toastColor = color;
        toastTime = 1.8;
    }

    private void showBanner(String title, String sub, int color, double seconds) {
        bannerTitle = title;
        bannerSub = sub;
        bannerColor = color;
        bannerTime = seconds;
        bannerTotal = seconds;
        introBanner = false;
    }

    // ---------------------------------------------------------------------------------------- Update

    @Override
    public void update(double dt) {
        time += dt;
        ui.update(dt);
        overlayUi.update(dt);
        for (Button c : cards) {
            c.update(dt);
        }
        for (Button b : trackBtns) {
            b.update(dt);
        }
        sellBtn.update(dt);
        modeBtn.update(dt);
        if (overlay != Overlay.NONE) {
            overlayIn = Math.min(1, overlayIn + dt * 3);
        }

        if (overlay == Overlay.NONE || overlay == Overlay.WON || overlay == Overlay.LOST) {
            boolean running = overlay == Overlay.NONE;
            if (running) {
                int steps = stepper.advance(dt * SPEEDS[speedIndex]);
                for (int i = 0; i < steps; i++) {
                    world.step();
                }
            }
            for (int i = 0; i < world.projectiles.size(); i++) {
                listener.trail(world.projectiles.get(i), world.projectiles.get(i).x, world.projectiles.get(i).y);
            }
        }
        listener.update(dt);

        if (world.state != World.State.RUNNING && overlay == Overlay.NONE) {
            endDelay += dt;
            if (endDelay > (world.state == World.State.WON ? 1.2 : 1.0)) {
                setOverlay(world.state == World.State.WON ? Overlay.WON : Overlay.LOST);
            }
        }
        if (world.state == World.State.WON) {
            celebrate -= dt;
            if (celebrate <= 0) {
                celebrate = 0.22;
                double wx = fx.particles.rng().range(120, world.width - 120);
                double wy = fx.particles.rng().range(80, world.height - 160);
                fx.firework(wx, wy, Colors.hsv(fx.particles.rng().nextDouble(), 0.85, 1), 1.6 + fx.particles.rng().nextDouble());
            }
        }

        // Anzeigen
        moneyShown.target = world.money;
        moneyShown.update(dt);
        livesPulse.target = 0;
        livesPulse.update(dt);
        moneyDeny = Math.max(0, moneyDeny - dt * 2.5);
        if (bannerTime > 0) {
            bannerTime -= dt;
        }
        if (toastTime > 0) {
            toastTime -= dt;
        }
        if (sellArmed > 0) {
            sellArmed -= dt;
        }
        if (selected != null && !world.towers.contains(selected)) {
            select(null);
        }
        startBtn.enabled = world.canStartWave() && overlay == Overlay.NONE;
        if (labelWave != world.waveIndex) {
            labelWave = world.waveIndex;
            startBtn.label = world.waveIndex == 0 ? "START" : "WELLE " + (world.waveIndex + 1);
        }
        autoBtn.selected = world.autoStart;
        speedBtn.selected = speedIndex > 0;
    }

    // ----------------------------------------------------------------------------------------- Zeichnen

    @Override
    public void render(Gfx g) {
        Viewport vp = app.vp;
        double u = vp.u;
        g.fillRect(0, 0, vp.w, vp.h, Theme.BLACK);
        double alpha = stepper.alpha();

        // --- Karte ---
        g.save();
        double sx = fx.shake > 0 ? Math.sin(time * 91) * fx.shake : 0;
        double sy = fx.shake > 0 ? Math.cos(time * 77) * fx.shake : 0;
        g.translate(mapX, mapY);
        g.scale(mapScale, mapScale);
        g.clipRect(0, 0, world.width, world.height);
        g.translate(sx, sy);
        WorldView.drawBackground(g, world.width, world.height);
        WorldView.drawPaths(g, world.paths, time, listener.baseFlash);
        if (selected != null) {
            WorldView.drawRange(g, selected.x, selected.y, selected.range, selected.type.color, time, true);
        } else if (hoverTower != null && !touchMode) {
            WorldView.drawRange(g, hoverTower.x, hoverTower.y, hoverTower.range, hoverTower.type.color, time, true);
        }
        WorldView.drawTowers(g, world, time);
        if (selected != null) {
            double pulse = 0.5 + 0.5 * Math.sin(time * 5);
            Neon.circle(g, selected.x, selected.y, 47 + 2 * pulse, 1.6, Colors.withAlpha(0xFFFFFF, 0.7), 4);
        }
        WorldView.drawEnemies(g, world, alpha, time);
        WorldView.drawProjectiles(g, world, alpha);
        WorldView.drawEffects(g, fx);
        drawGhost(g);
        g.restore();
        g.save();
        g.translate(mapX, mapY);
        g.scale(mapScale, mapScale);
        WorldView.drawFrame(g, world.width, world.height);
        g.restore();

        // Wellen-Banner über der Karte
        drawBanner(g);

        // --- HUD, Panel, Steuerung ---
        drawHud(g, u);
        if (selected != null) {
            drawUpgradePanel(g, u);
            if (portrait) {
                for (Button c : cards) {
                    c.render(g);
                }
            }
        } else {
            for (Button c : cards) {
                c.render(g);
            }
            if (portrait) {
                drawInfoPanel(g, u);
            }
        }
        ui.render(g);
        drawToast(g, u);

        if (overlay != Overlay.NONE) {
            drawOverlay(g, u);
        }
    }

    private void drawGhost(Gfx g) {
        if (placing == null || !ghostOnMap) {
            return;
        }
        double pulse = 0.5 + 0.5 * Math.sin(time * 6);
        WorldView.drawRange(g, ghostX, ghostY, placing.range, placing.color, time, ghostValid);
        g.save();
        g.alpha(ghostValid ? 0.85 : 0.55);
        int tint = ghostValid ? placing.color : Theme.RED;
        TowerArt.drawIcon(g, placing, ghostX, ghostY, World.TOWER_RADIUS, time);
        g.restore();
        if (!ghostValid) {
            Neon.circle(g, ghostX, ghostY, World.TOWER_RADIUS + 3 + pulse * 2, 2.2, tint, 6);
            Icons.draw(g, Icon.CROSS, ghostX, ghostY, 11, Theme.RED, 3);
        }
    }

    private void drawBanner(Gfx g) {
        if (bannerTime <= 0 || bannerTitle == null) {
            return;
        }
        double total = bannerTotal;
        double t = Math.min(total, bannerTime);
        double inT = Mathx.clamp01((total - t) / 0.3);
        double outT = Mathx.clamp01(t / 0.5);
        double a = Math.min(inT, outT);
        double sc = 0.9 + 0.1 * Easing.outBack(inT);
        double mw = world.width * mapScale;
        double cx = mapX + mw / 2;
        double cy = mapY + world.height * mapScale * 0.36;
        double h = Math.min(54 * app.vp.u, mw * 0.11);
        g.save();
        g.alpha(a);
        g.translate(cx, cy);
        g.scale(sc, sc);
        NeonText.draw(g, bannerTitle, 0, 0, h, bannerColor, 10, 0.5, time);
        if (bannerSub != null && !bannerSub.isEmpty()) {
            g.text(bannerSub, 0, h * 0.95, Math.max(12, h * 0.34), Theme.TEXT, Gfx.ALIGN_CENTER, true);
        }
        g.restore();
    }

    private void drawToast(Gfx g, double u) {
        if (toastTime <= 0 || toast == null) {
            return;
        }
        double a = Mathx.clamp01(toastTime / 0.4);
        double w = g.textWidth(toast, 15 * u, true) + 34 * u;
        double h = 34 * u;
        double cx = mapX + world.width * mapScale / 2;
        double y = mapY + world.height * mapScale - h - 14 * u;
        g.save();
        g.alpha(a);
        g.fillRoundRect(cx - w / 2, y, w, h, h / 2, Colors.withAlpha(0x000000, 0.88));
        Neon.roundRect(g, cx - w / 2, y, w, h, h / 2, 1.6, toastColor, 5);
        g.text(toast, cx, y + h / 2, 15 * u, Theme.TEXT, Gfx.ALIGN_CENTER, true);
        g.restore();
    }

    // ------------------------------------------------------------------------------------------- HUD

    private void chip(Gfx g, double x, double y, double w, double h, Icon icon, String text, int color,
                      double flash) {
        int c = Colors.lerp(color, Theme.RED, flash);
        Draw.softPanel(g, x, y, w, h, h * 0.3, c);
        double is = h * 0.30;
        double tx = x + h * 0.25;
        if (icon != null) {
            Icons.draw(g, icon, x + h * 0.2 + is, y + h / 2, is, c, 3);
            tx = x + h * 0.2 + is * 2 + h * 0.12;
        }
        g.text(text, tx, y + h / 2, h * 0.5, Theme.TEXT, Gfx.ALIGN_LEFT, true);
    }

    private void drawHud(Gfx g, double u) {
        String lives = Integer.toString(world.lives);
        String money = Integer.toString((int) Math.round(moneyShown.value));
        String wave = "WELLE " + Math.min(world.waveIndex, world.totalWaves()) + "/" + world.totalWaves();
        if (!portrait) {
            double chipH = 34 * u;
            double gap = 6 * u;
            double w1 = hudW * 0.38;
            chip(g, hudX, hudY, w1, chipH, Icon.HEART, lives, Theme.LIFE, livesPulse.value);
            chip(g, hudX + w1 + gap, hudY, hudW - w1 - gap, chipH, Icon.GEM, money, Theme.MONEY, moneyDeny);
            double y2 = hudY + chipH + gap;
            Draw.softPanel(g, hudX, y2, hudW, chipH, chipH * 0.3, Theme.CYAN);
            g.text(wave, hudX + chipH * 0.35, y2 + chipH / 2, chipH * 0.46, Theme.TEXT, Gfx.ALIGN_LEFT, true);
            int left = world.enemiesRemaining();
            g.text(left + " Gegner", hudX + hudW - chipH * 0.3, y2 + chipH / 2, chipH * 0.38, Theme.TEXT_DIM,
                    Gfx.ALIGN_RIGHT, false);
        } else {
            double gap = 6 * u;
            double w1 = hudW * 0.24;
            double w2 = hudW * 0.30;
            double w3 = hudW - w1 - w2 - gap * 2;
            chip(g, hudX, hudY, w1, hudH, Icon.HEART, lives, Theme.LIFE, livesPulse.value);
            chip(g, hudX + w1 + gap, hudY, w2, hudH, Icon.GEM, money, Theme.MONEY, moneyDeny);
            Draw.softPanel(g, hudX + w1 + w2 + gap * 2, hudY, w3, hudH, hudH * 0.3, Theme.CYAN);
            g.text(wave, hudX + w1 + w2 + gap * 2 + w3 / 2, hudY + hudH * 0.36, hudH * 0.36, Theme.TEXT,
                    Gfx.ALIGN_CENTER, true);
            g.text(world.enemiesRemaining() + " Gegner", hudX + w1 + w2 + gap * 2 + w3 / 2, hudY + hudH * 0.74,
                    hudH * 0.26, Theme.TEXT_DIM, Gfx.ALIGN_CENTER, false);
        }
    }

    // ---------------------------------------------------------------------------------- Shop & Panel

    private void paintCard(Gfx g, Button b, TowerType type, int index) {
        double w = b.w;
        double h = b.h;
        boolean can = world.money >= type.cost;
        boolean active = placing == type;
        double hv = Math.max(b.hover.value, active ? 1 : 0);
        int c = type.color;
        double r = Math.min(w, h) * 0.2;
        g.fillRoundRect(-w / 2, -h / 2, w, h, r, Colors.withAlpha(Theme.PANEL, 0.94));
        g.fillRoundRect(-w / 2, -h / 2, w, h, r, Colors.withAlpha(c, 0.04 + 0.13 * hv + 0.18 * b.press.value));
        Neon.roundRect(g, -w / 2, -h / 2, w, h, r, 1.8, Colors.withAlpha(c, can ? 0.95 : 0.4), 2 + 7 * hv);
        g.save();
        g.alpha(can ? 1 : 0.5);
        boolean horizontal = w > h * 1.5;
        String cost = Integer.toString(type.cost);
        if (horizontal) {
            double ir = h * 0.33;
            TowerArt.drawIcon(g, type, -w / 2 + h * 0.52, 0, ir, app.time);
            double tx = -w / 2 + h * 1.02;
            g.text(type.label, tx, -h * 0.17, Math.max(11, h * 0.27), Theme.TEXT, Gfx.ALIGN_LEFT, true);
            Icons.draw(g, Icon.GEM, tx + h * 0.1, h * 0.2, h * 0.13, Theme.MONEY, 2);
            g.text(cost, tx + h * 0.28, h * 0.2, Math.max(11, h * 0.26), can ? Theme.MONEY : Theme.RED,
                    Gfx.ALIGN_LEFT, true);
        } else {
            double ir = Math.min(w * 0.34, h * 0.27);
            TowerArt.drawIcon(g, type, 0, -h * 0.18, ir, app.time);
            Icons.draw(g, Icon.GEM, -w * 0.2, h * 0.31, Math.min(w * 0.09, h * 0.09), Theme.MONEY, 2);
            g.text(cost, -w * 0.08, h * 0.31, Math.min(w * 0.22, h * 0.19), can ? Theme.MONEY : Theme.RED,
                    Gfx.ALIGN_LEFT, true);
        }
        g.restore();
        if (!app.platform.touchPrimary()) {
            g.text(Integer.toString(index + 1), -w / 2 + 7, -h / 2 + 9, 10, Colors.withAlpha(Theme.TEXT, 0.55),
                    Gfx.ALIGN_LEFT, true);
        }
    }

    private void drawUpgradePanel(Gfx g, double u) {
        if (selected == null) {
            return;
        }
        Tower t = selected;
        // Kopfzeile: Symbol, Name, Gesamtstufe
        double hx = upX;
        double hy = upY;
        double hh = sellBtn.h;
        Draw.softPanel(g, hx, hy, upW * 0.5 - 6 * u, hh, hh * 0.25, t.type.color);
        TowerArt.drawIcon(g, t.type, hx + hh * 0.55, hy + hh / 2, hh * 0.34, app.time);
        g.text(t.type.label, hx + hh * 1.0, hy + hh * 0.34, Math.max(11, hh * 0.3), Theme.TEXT, Gfx.ALIGN_LEFT, true);
        g.text("Stufe " + t.totalLevels() + "/15", hx + hh * 1.0, hy + hh * 0.7, Math.max(10, hh * 0.24),
                Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        sellBtn.render(g);
        for (Button b : trackBtns) {
            b.render(g);
        }
        modeBtn.render(g);
    }

    // ------------------------------------------------------------------------- Info-Panel (Hochformat)

    /** Im Hochformat ist unter dem Shop Platz: Werte des gewählten Turms oder Vorschau der nächsten Welle. */
    private void drawInfoPanel(Gfx g, double u) {
        if (upH < 70 * u) {
            return;
        }
        if (placing != null) {
            drawTowerInfo(g, placing, u);
        } else {
            drawWavePreview(g, u);
        }
    }

    private void drawTowerInfo(Gfx g, TowerType t, double u) {
        double x = upX;
        double y = upY;
        double w = upW;
        double h = Math.min(upH, 150 * u);
        Draw.softPanel(g, x, y, w, h, 14 * u, t.color);
        double pad = 12 * u;
        TowerArt.drawIcon(g, t, x + pad + 26 * u, y + pad + 26 * u, 26 * u, app.time);
        g.text(t.label, x + pad * 2 + 52 * u, y + pad + 12 * u, 17 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        g.text(t.tagline, x + pad * 2 + 52 * u, y + pad + 36 * u, 12 * u, Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        double cw = (w - pad * 2) / 3;
        double cy = y + pad + 52 * u + (h - pad * 2 - 52 * u) / 2 + 4 * u;
        UpgradeTrack[] tracks = UpgradeTrack.values();
        String[] vals = {
            Integer.toString((int) Math.round(t.range)),
            Integer.toString(t.damage),
            (Math.round(10.0 / t.interval) / 10.0) + "/s"
        };
        Icon[] icons = {Icon.RANGE, Icon.DAMAGE, Icon.SPEED};
        for (int i = 0; i < 3; i++) {
            double cx = x + pad + cw * i + cw / 2;
            Icons.draw(g, icons[i], cx - 22 * u, cy, 11 * u, tracks[i].color, 3);
            g.text(vals[i], cx - 6 * u, cy, 16 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        }
        g.text("Auf die Karte tippen oder ziehen", x + w / 2, y + h - 12 * u, 11 * u, Theme.TEXT_DIM,
                Gfx.ALIGN_CENTER, false);
    }

    private void drawWavePreview(Gfx g, double u) {
        double x = upX;
        double y = upY;
        double w = upW;
        double h = Math.min(upH, 230 * u);
        Draw.softPanel(g, x, y, w, h, 14 * u, Theme.CYAN);
        double pad = 12 * u;
        WaveDef next = world.nextWave();
        if (next == null) {
            g.text("ALLE WELLEN GESTARTET", x + w / 2, y + h / 2, 14 * u, Theme.TEXT_DIM, Gfx.ALIGN_CENTER, true);
            return;
        }
        g.text("NÄCHSTE WELLE " + (world.waveIndex + 1), x + pad, y + pad + 8 * u, 13 * u, Theme.TEXT,
                Gfx.ALIGN_LEFT, true);
        g.text("Bonus +" + next.bonus, x + w - pad, y + pad + 8 * u, 12 * u, Theme.MONEY, Gfx.ALIGN_RIGHT, true);
        int rows = Math.min(next.groups.size(), 5);
        double top = y + pad + 28 * u;
        double rowH = Math.min(36 * u, (y + h - pad - top) / Math.max(1, rows));
        for (int i = 0; i < rows; i++) {
            WaveDef.Group gr = next.groups.get(i);
            double cy = top + rowH * i + rowH / 2;
            int c = Theme.enemyColor(gr.hp);
            EnemyArt.drawIcon(g, gr.type, x + pad + 16 * u, cy, Math.min(13 * u, rowH * 0.4), c);
            g.text(gr.count + "× " + gr.type.label, x + pad + 40 * u, cy, 14 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
            g.text("HP " + HpLabel.format(gr.hp), x + w - pad, cy, 13 * u, c, Gfx.ALIGN_RIGHT, true);
        }
    }

    private String statText(Tower t, UpgradeTrack track, boolean next) {
        int lvl = t.level(track) + (next ? 1 : 0);
        switch (track) {
            case RANGE:
                return Integer.toString((int) Math.round(t.type.range * track.mult[lvl]));
            case DAMAGE:
                return Integer.toString(Math.max(1, (int) Math.round(t.type.damage * track.mult[lvl])));
            case SPEED:
            default: {
                double perSec = UpgradeTrack.SPEED.mult[lvl] / t.type.interval;
                return (Math.round(perSec * 10) / 10.0) + "/s";
            }
        }
    }

    private void paintTrackRow(Gfx g, Button b, UpgradeTrack track) {
        if (selected == null) {
            return;
        }
        Tower t = selected;
        double w = b.w;
        double h = b.h;
        int lvl = t.level(track);
        boolean maxed = lvl >= UpgradeTrack.MAX_LEVEL;
        int cost = world.upgradeCost(t, track);
        boolean can = !maxed && world.money >= cost;
        double hv = b.hover.value;
        int c = track.color;
        double r = h * 0.22;
        g.fillRoundRect(-w / 2, -h / 2, w, h, r, Colors.withAlpha(Theme.PANEL, 0.94));
        g.fillRoundRect(-w / 2, -h / 2, w, h, r, Colors.withAlpha(c, 0.04 + 0.10 * hv + 0.18 * b.press.value));
        Neon.roundRect(g, -w / 2, -h / 2, w, h, r, 1.6, Colors.withAlpha(c, maxed ? 0.45 : 0.9), 2 + 6 * hv);

        Icons.draw(g, trackIcon(track), -w / 2 + h * 0.52, 0, h * 0.27, c, 3);
        double tx = -w / 2 + h * 1.0;
        double labelSize = Math.max(10, h * 0.23);
        g.text(track.label, tx, -h * 0.25, labelSize, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        // Stufenpunkte
        double pip = h * 0.09;
        for (int i = 0; i < UpgradeTrack.MAX_LEVEL; i++) {
            double px = tx + pip + i * pip * 2.9;
            if (i < lvl) {
                g.fillCircle(px, h * 0.02, pip, c);
            } else {
                g.strokeCircle(px, h * 0.02, pip, 1.2, Colors.withAlpha(c, 0.45));
            }
        }
        String cur = statText(t, track, false);
        String txt = maxed ? cur : cur + " > " + statText(t, track, true);
        g.text(txt, tx, h * 0.31, Math.max(9, h * 0.2), Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);

        // Preis
        double bw = Math.min(w * 0.32, h * 1.5);
        double bh = h * 0.62;
        double bx = w / 2 - bw - h * 0.12;
        if (maxed) {
            g.text("MAX", bx + bw / 2, 0, h * 0.28, Theme.TEXT_DIM, Gfx.ALIGN_CENTER, true);
        } else {
            int bc = can ? Theme.GREEN : Theme.RED;
            g.fillRoundRect(bx, -bh / 2, bw, bh, bh * 0.3, Colors.withAlpha(bc, can ? 0.14 : 0.07));
            Neon.roundRect(g, bx, -bh / 2, bw, bh, bh * 0.3, 1.4, Colors.withAlpha(bc, can ? 0.95 : 0.5), can ? 4 : 0);
            Icons.draw(g, Icon.GEM, bx + bw * 0.2, 0, bh * 0.2, can ? Theme.MONEY : Colors.withAlpha(Theme.MONEY, 0.5), 0);
            g.text(Integer.toString(cost), bx + bw * 0.4, 0, Math.min(h * 0.3, bw * 0.3), can ? Theme.TEXT : Colors.withAlpha(Theme.RED, 0.9),
                    Gfx.ALIGN_LEFT, true);
        }
    }

    private static Icon trackIcon(UpgradeTrack t) {
        switch (t) {
            case RANGE:
                return Icon.RANGE;
            case DAMAGE:
                return Icon.DAMAGE;
            case SPEED:
            default:
                return Icon.SPEED;
        }
    }

    private void paintSell(Gfx g, Button b) {
        if (selected == null) {
            return;
        }
        boolean armed = sellArmed > 0;
        double w = b.w;
        double h = b.h;
        int c = armed ? Theme.RED : Theme.MONEY;
        double hv = Math.max(b.hover.value, armed ? 1 : 0);
        g.fillRoundRect(-w / 2, -h / 2, w, h, h * 0.25, Colors.withAlpha(Theme.PANEL, 0.94));
        g.fillRoundRect(-w / 2, -h / 2, w, h, h * 0.25, Colors.withAlpha(c, 0.06 + 0.12 * hv + 0.2 * b.press.value));
        Neon.roundRect(g, -w / 2, -h / 2, w, h, h * 0.25, 1.6, c, 3 + 5 * hv);
        if (armed) {
            g.text("SICHER?", 0, -h * 0.02, Math.max(11, h * 0.34), Theme.TEXT, Gfx.ALIGN_CENTER, true);
        } else {
            g.text("VERKAUFEN", 0, -h * 0.17, Math.max(10, h * 0.26), Theme.TEXT, Gfx.ALIGN_CENTER, true);
            Icons.draw(g, Icon.GEM, -h * 0.34, h * 0.22, h * 0.13, Theme.MONEY, 2);
            g.text(Integer.toString(world.sellValue(selected)), -h * 0.14, h * 0.22, Math.max(10, h * 0.27),
                    Theme.MONEY, Gfx.ALIGN_LEFT, true);
        }
    }

    private void paintMode(Gfx g, Button b) {
        if (selected == null) {
            return;
        }
        double w = b.w;
        double h = b.h;
        double hv = b.hover.value;
        g.fillRoundRect(-w / 2, -h / 2, w, h, h * 0.25, Colors.withAlpha(Theme.PANEL, 0.94));
        Neon.roundRect(g, -w / 2, -h / 2, w, h, h * 0.25, 1.4, Colors.withAlpha(Theme.TEXT_DIM, 0.8 + 0.2 * hv), 2 + 4 * hv);
        Icons.draw(g, Icon.TARGET, -w / 2 + h * 0.55, 0, h * 0.27, Theme.TEXT_DIM, 0);
        g.text("ZIEL", -w / 2 + h * 1.0, -h * 0.02, Math.max(9, h * 0.26), Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        g.text(selected.mode.label, w / 2 - h * 0.35, -h * 0.02, Math.max(10, h * 0.32), Theme.TEXT, Gfx.ALIGN_RIGHT, true);
    }

    // ---------------------------------------------------------------------------------------- Overlay

    private void drawOverlay(Gfx g, double u) {
        Viewport vp = app.vp;
        double a = Easing.outCubic(overlayIn);
        g.save();
        g.alpha(a);
        g.fillRect(0, 0, vp.w, vp.h, Colors.withAlpha(0x000000, overlay == Overlay.PAUSE ? 0.78 : 0.7));
        double cx = vp.w / 2;
        double ty = vp.h / 2 - 150 * u;
        double th = Math.min(64 * u, vp.safeW() * 0.84 / 6.2);
        String title;
        int color;
        switch (overlay) {
            case WON:
                title = "SIEG!";
                color = Theme.GREEN;
                break;
            case LOST:
                title = "VERLOREN";
                color = Theme.RED;
                break;
            default:
                title = "PAUSE";
                color = Theme.CYAN;
                break;
        }
        double sc = 0.85 + 0.15 * Easing.outBack(overlayIn);
        g.save();
        g.translate(cx, ty);
        g.scale(sc, sc);
        NeonText.draw(g, title, 0, 0, th, color, 12, overlay == Overlay.LOST ? 0.8 : 0.4, time);
        g.restore();
        if (overlay != Overlay.PAUSE) {
            String stats = "Welle " + Math.min(world.waveIndex, world.totalWaves()) + "/" + world.totalWaves()
                    + "   ·   " + world.kills + " Gegner besiegt   ·   " + world.lives + " Leben";
            g.text(stats, cx, ty + th * 1.0, Math.max(13, 16 * u), Theme.TEXT, Gfx.ALIGN_CENTER, true);
        } else {
            g.text(level.name, cx, ty + th * 0.95, Math.max(13, 16 * u), Theme.TEXT_DIM, Gfx.ALIGN_CENTER, true);
        }
        overlayUi.render(g);
        g.restore();
    }

    // ----------------------------------------------------------------------------------------- Eingabe

    private int cardAt(double x, double y) {
        if (selected != null && !portrait) {
            return -1;
        }
        for (int i = 0; i < cards.length; i++) {
            if (cards[i].contains(x, y) && cards[i].appear.value > 0.3) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void pointerDown(double x, double y, boolean touch) {
        touchMode = touch;
        if (overlay != Overlay.NONE) {
            overlayUi.pointerDown(x, y);
            return;
        }
        if (selected != null) {
            for (Button b : new Button[] {sellBtn, trackBtns[0], trackBtns[1], trackBtns[2], modeBtn}) {
                if (b.contains(x, y)) {
                    b.press.target = 1;
                    pressedPanel = b;
                    return;
                }
            }
        }
        if (ui.pointerDown(x, y)) {
            return;
        }
        int ci = cardAt(x, y);
        if (ci >= 0) {
            cardDown = true;
            dragging = false;
            dragType = TYPES[ci];
            dragX0 = x;
            dragY0 = y;
            cards[ci].press.target = 1;
            return;
        }
        if (onMap(x, y)) {
            mapPressed = true;
            double wx = toWorldX(x);
            double wy = toWorldY(y);
            if (placing != null) {
                updateGhost(x, y, 0);
                return;
            }
            Tower hit = world.towerAt(wx, wy, 18 / mapScale);
            select(hit == selected ? null : hit);
            if (hit != null && hit == selected) {
                sellArmed = 0;
            }
        } else if (placing == null && selected != null && !panelContains(x, y)) {
            select(null);
        }
    }

    private boolean panelContains(double x, double y) {
        return x >= upX && x <= upX + upW && y >= upY && y <= upY + upH;
    }

    private void updateGhost(double sx, double sy, double offsetY) {
        dismissIntroBanner();
        ghostOnMap = onMap(sx, sy - offsetY);
        ghostX = toWorldX(sx);
        ghostY = toWorldY(sy - offsetY);
        if (placing != null) {
            ghostValid = ghostOnMap && world.checkPlacement(ghostX, ghostY) == World.PlaceCheck.OK;
        }
    }

    @Override
    public void pointerMove(double x, double y, boolean pressed, boolean touch) {
        if (!pressed) {
            touchMode = touch;
        }
        if (overlay != Overlay.NONE) {
            overlayUi.pointerMove(x, y, !touch);
            return;
        }
        ui.pointerMove(x, y, !touch);
        for (Button c : cards) {
            c.hover.target = (!touch && c.contains(x, y)) ? 1 : 0;
        }
        for (Button b : new Button[] {sellBtn, trackBtns[0], trackBtns[1], trackBtns[2], modeBtn}) {
            b.hover.target = (!touch && selected != null && b.contains(x, y)) ? 1 : 0;
            if (pressedPanel == b && !b.contains(x, y)) {
                b.press.target = 0;
            }
        }
        double u = app.vp.u;
        if (cardDown && pressed) {
            if (!dragging && Mathx.dist(x, y, dragX0, dragY0) > 12 * u) {
                if (world.money >= dragType.cost) {
                    dragging = true;
                    placing = dragType;
                    select(null);
                } else {
                    cardDown = false;
                    deny("Zu wenig Geld für " + dragType.label);
                }
            }
            if (dragging) {
                updateGhost(x, y, touch ? 56 * u : 0);
            }
            return;
        }
        if (placing != null) {
            updateGhost(x, y, 0);
        } else if (!touch && !pressed) {
            hoverTower = onMap(x, y) ? world.towerAt(toWorldX(x), toWorldY(y), 6) : null;
        }
    }

    @Override
    public void pointerUp(double x, double y, boolean touch) {
        if (overlay != Overlay.NONE) {
            overlayUi.pointerUp(x, y);
            return;
        }
        if (pressedPanel != null) {
            Button b = pressedPanel;
            pressedPanel = null;
            b.press.target = 0;
            if (b.contains(x, y) && selected != null && b.onClick != null) {
                b.onClick.run();
            }
            return;
        }
        if (ui.pointerUp(x, y)) {
            return;
        }
        if (cardDown) {
            cardDown = false;
            for (Button c : cards) {
                c.press.target = 0;
            }
            if (dragging) {
                dragging = false;
                double offset = touch ? 56 * app.vp.u : 0;
                updateGhost(x, y, offset);
                if (ghostOnMap && ghostValid) {
                    placeTower(dragType, ghostX, ghostY);
                } else if (ghostOnMap) {
                    deny("Hier kann nicht gebaut werden");
                }
                placing = null;
                ghostOnMap = false;
            } else {
                // Antippen: Platzierungsmodus ein-/ausschalten
                if (placing == dragType) {
                    cancelPlacing();
                } else if (world.money >= dragType.cost) {
                    placing = dragType;
                    select(null);
                    ghostOnMap = false;
                    showToast(dragType.label + ": " + dragType.tagline + " – tippe auf die Karte", dragType.color);
                } else {
                    deny("Zu wenig Geld für " + dragType.label);
                }
            }
            return;
        }
        if (mapPressed) {
            mapPressed = false;
            if (placing != null && onMap(x, y)) {
                updateGhost(x, y, 0);
                if (ghostValid) {
                    placeTower(placing, ghostX, ghostY);
                    placing = null;
                    ghostOnMap = false;
                } else {
                    deny(world.checkPlacement(ghostX, ghostY) == World.PlaceCheck.ON_PATH
                            ? "Nicht auf der Gegnerspur" : "Hier kann nicht gebaut werden");
                }
            }
        }
    }

    private void placeTower(TowerType type, double wx, double wy) {
        Tower t = world.placeTower(type, wx, wy);
        if (t == null && world.money < type.cost) {
            deny("Zu wenig Geld");
        }
    }

    @Override
    public void pointerCancel() {
        ui.cancel();
        overlayUi.cancel();
        cardDown = false;
        dragging = false;
        mapPressed = false;
        pressedPanel = null;
        if (placing != null && ghostOnMap) {
            ghostOnMap = false;
        }
    }

    @Override
    public boolean key(String code, boolean ctrl) {
        if (overlay != Overlay.NONE) {
            return false;
        }
        switch (code) {
            case "Space":
            case "Enter":
                startWave();
                return true;
            case "KeyP":
                setOverlay(Overlay.PAUSE);
                return true;
            case "KeyF":
                cycleSpeed();
                return true;
            case "KeyQ":
                buyUpgrade(UpgradeTrack.RANGE);
                return true;
            case "KeyW":
                buyUpgrade(UpgradeTrack.DAMAGE);
                return true;
            case "KeyE":
                buyUpgrade(UpgradeTrack.SPEED);
                return true;
            case "Tab":
                if (selected != null) {
                    selected.mode = selected.mode.next();
                }
                return true;
            case "Delete":
            case "KeyX":
                sellSelected();
                return true;
            case "RightClick":
                if (placing != null) {
                    cancelPlacing();
                } else {
                    select(null);
                }
                return true;
            default:
                break;
        }
        if (code.startsWith("Digit") && code.length() == 6) {
            int i = code.charAt(5) - '1';
            if (i >= 0 && i < TYPES.length) {
                if (placing == TYPES[i]) {
                    cancelPlacing();
                } else if (world.money >= TYPES[i].cost) {
                    placing = TYPES[i];
                    select(null);
                } else {
                    deny("Zu wenig Geld für " + TYPES[i].label);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean back() {
        if (overlay == Overlay.PAUSE) {
            setOverlay(Overlay.NONE);
        } else if (overlay != Overlay.NONE) {
            return true;
        } else if (placing != null) {
            cancelPlacing();
        } else if (selected != null) {
            select(null);
        } else {
            setOverlay(Overlay.PAUSE);
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------- Ereignisse

    /** Ergänzt die Standard-Effekte um Spielfluss-Reaktionen (Banner, Verlust, Ende). */
    private final class Listener extends GameFx {
        Listener(Effects fx) {
            super(fx);
        }

        @Override
        public void onWaveStarted(int index, WaveDef wave) {
            boolean boss = false;
            for (WaveDef.Group g : wave.groups) {
                if (g.type == EnemyType.BOSS) {
                    boss = true;
                }
            }
            showBanner("WELLE " + (index + 1), boss ? "TITAN!" : wave.totalEnemies() + " Gegner",
                    boss ? Theme.RED : Theme.CYAN, 2.4);
        }

        @Override
        public void onWaveCleared(int index, int bonus) {
            showToast("Welle geschafft!  +" + bonus, Theme.GREEN);
        }

        @Override
        public void onEnemyLeaked(neontd.sim.Enemy e, int livesLost) {
            super.onEnemyLeaked(e, livesLost);
            livesPulse.snap(1);
        }

        @Override
        public void onGameEnded(boolean won) {
            endDelay = 0;
            if (!won) {
                fx.shake = 14;
            }
        }
    }
}

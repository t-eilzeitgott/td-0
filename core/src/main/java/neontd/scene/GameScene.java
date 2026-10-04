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
import neontd.gfx.UprightGfx;
import neontd.level.LevelDef;
import neontd.math.Mathx;
import neontd.physics.FixedTimestep;
import neontd.progress.Medals;
import neontd.progress.Progress;
import neontd.render.EnemyArt;
import neontd.render.GameFx;
import neontd.render.HpLabel;
import neontd.render.TowerArt;
import neontd.render.WorldView;
import neontd.save.RunSave;
import neontd.sim.EnemyType;
import neontd.sim.Tower;
import neontd.sim.TowerType;
import neontd.sim.UpgradeTrack;
import neontd.sim.WaveDef;
import neontd.sim.World;
import neontd.sim.WorldSnapshot;
import neontd.ui.Button;
import neontd.ui.Draw;
import neontd.ui.Easing;
import neontd.ui.Fmt;
import neontd.ui.Smooth;
import neontd.ui.Ui;
import neontd.ui.Viewport;

/**
 * Die eigentliche Spielszene: Karte mit Simulation, HUD, Turm-Shop (Ziehen oder Tippen), Upgrade-Panel,
 * Wellenstart, Tempo, Pause sowie Sieg- und Niederlage-Anzeige.
 *
 * <p>Das Layout kommt aus {@link GameLayout}: Auf Laptop und Tablet eine feste Seitenleiste bzw. ein gestapeltes
 * Hochformat, auf dem Handy eine schmale, <b>einklappbare</b> Leiste – im Hochformat mit um 90° gedrehter Karte, damit
 * sie die Höhe füllt. Mit Profil: gesperrte Türme, XP und Level, Endlosmodus, Speichern und Fortsetzen.
 */
public final class GameScene extends Scene {
    private enum Overlay { NONE, PAUSE, WON, LOST }

    private static final TowerType[] TYPES = TowerType.values();
    private static final double[] SPEEDS = {1, 2, 3};
    private static final double[] SPEEDS_ENDLESS = {1, 2, 3, 5};
    private static final String RAIL_KEY = "neontd.ui.rail";
    /** Abstand der automatischen Zwischenspeicherung (Sekunden). */
    private static final double AUTOSAVE = 3;

    private final LevelDef level;
    /** Wenn gesetzt, kehrt "Zurück" zum Editor mit diesem Level zurück (Testspiel). */
    private final LevelDef editorReturn;
    /** Nur im normalen Spiel zählen XP, Rekorde und Speicherstände – das Testspiel aus dem Editor bleibt folgenlos. */
    private final boolean progressOn;
    private final Progress progress;
    private final RunSave resumeFrom;
    /** Wurde dieses Spiel direkt im Endlosmodus begonnen (für "Nochmal")? */
    private final boolean startedEndless;

    private final Effects fx = new Effects();
    private final Listener listener = new Listener(fx);
    private final World world;
    private final FixedTimestep stepper = new FixedTimestep(World.STEP, 8);
    private final GameLayout lay = new GameLayout();
    private final UprightGfx upright = new UprightGfx();

    private final Ui ui = new Ui();
    private final Ui overlayUi = new Ui();
    private final Button[] cards = new Button[TYPES.length];
    private Button startBtn;
    private Button speedBtn;
    private Button pauseBtn;
    private Button autoBtn;
    private Button toggleBtn;
    private final Button[] trackBtns = new Button[3];
    private Button sellBtn;
    private Button modeBtn;

    // Layout-Zustand
    private boolean portrait;
    private boolean railWanted = true;
    private final Smooth railT = new Smooth(1, 11);
    private double laidOutRail = -1;
    private boolean laidOutSelected;

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
    /** Touch: Der Turm schwebt an seiner Stelle und wird erst mit dem Haken fest gebaut. */
    private boolean pending;
    /** Der schwebende Turm wird direkt gegriffen und mit dem Finger gezogen (ohne Sprung). */
    private boolean grabbing;
    private double grabDX;
    private double grabDY;
    /** Angezeigte (weich nachgeführte) Position des schwebenden Turms. */
    private double shownX;
    private double shownY;
    private boolean shownInit;
    private double sellArmed;
    private double time;
    private double celebrate;
    private int labelWave = -1;
    private boolean labelEndless;
    private Button pressedPanel;

    // Profil und Speichern
    private int killsCommitted;
    private int xpRun;
    private final int levelAtStart;
    private double saveTimer;
    private boolean saveSoon;
    private boolean ended;
    private boolean newRecord;
    private String medalText = "";

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
        this(app, level, null, false, null);
    }

    public GameScene(App app, LevelDef level, LevelDef editorReturn) {
        this(app, level, editorReturn, false, null);
    }

    /** Neues Spiel direkt im Endlosmodus. */
    public static GameScene endless(App app, LevelDef level) {
        return new GameScene(app, level, null, true, null);
    }

    /** Setzt einen gespeicherten Lauf fort (startet in der Pause). */
    public static GameScene resume(App app, LevelDef level, RunSave run) {
        return new GameScene(app, level, null, false, run);
    }

    private GameScene(App app, LevelDef level, LevelDef editorReturn, boolean endless, RunSave resume) {
        super(app);
        this.level = level;
        this.editorReturn = editorReturn;
        this.progressOn = editorReturn == null;
        this.progress = app.progress;
        this.resumeFrom = resume;
        this.startedEndless = endless;
        this.levelAtStart = progress.level();
        LevelDef run = level;
        if (progressOn && resume == null) {
            // Dauerhafter Startbonus aus dem Spielerlevel (Geld und Leben).
            run = level.copy();
            run.startMoney += progress.startMoneyBonus();
            run.startLives += progress.startLivesBonus();
        } else if (progressOn) {
            run = level.copy();
            run.startLives += progress.startLivesBonus();
        }
        this.world = new World(run, listener);
        if (endless) {
            world.enableEndless();
        }
        if (resume != null && resume.snapshot != null) {
            world.restore(resume.snapshot);
            killsCommitted = world.kills;
        }
        String saved = app.platform.store().get(RAIL_KEY);
        railWanted = !"0".equals(saved);
        railT.snap(railWanted ? 1 : 0);
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
            if (world.autoStart && world.readyForNextWave()) {
                startWave();
            }
        }));
        toggleBtn = ui.add(new Button("", Icon.UP, Theme.TEXT_DIM, this::toggleRail));
        toggleBtn.painter = this::paintToggle;
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
        if (resumeFrom != null) {
            showBanner(level.name.toUpperCase(), "Gespeicherter Lauf – Welle " + (world.waveIndex + 1), Theme.CYAN, 3.2);
            setOverlay(Overlay.PAUSE);
        } else if (world.endless) {
            showBanner("ENDLOS", "Wie weit kommst du?", Theme.MAGENTA, 3.2);
            introBanner = true;
        } else {
            showBanner(level.name.toUpperCase(), "Platziere Türme und starte die erste Welle", Theme.CYAN, 3.2);
            introBanner = true;
        }
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
        applyLayout();
    }

    private void applyLayout() {
        double sx = selected != null ? selected.x : 0;
        double sy = selected != null ? selected.y : 0;
        lay.compute(app.vp, world.width, world.height, railT.value, selected != null, sx, sy);
        laidOutRail = railT.value;
        laidOutSelected = selected != null;
        portrait = lay.mode == GameLayout.Mode.STACKED || lay.mode == GameLayout.Mode.COMPACT_PORT;
        boolean compact = lay.compact();

        startBtn.bounds(lay.start.x, lay.start.y, lay.start.w, lay.start.h);
        speedBtn.bounds(lay.speed.x, lay.speed.y, lay.speed.w, lay.speed.h);
        autoBtn.bounds(lay.auto.x, lay.auto.y, lay.auto.w, lay.auto.h);
        pauseBtn.bounds(lay.pause.x, lay.pause.y, lay.pause.w, lay.pause.h);
        toggleBtn.bounds(lay.toggle.x, lay.toggle.y, lay.toggle.w, lay.toggle.h);
        boolean extras = lay.showExtras && !(lay.mode == GameLayout.Mode.COMPACT_PORT && selected != null);
        speedBtn.visible = extras;
        autoBtn.visible = extras;
        pauseBtn.visible = extras;
        boolean startVisible = !(lay.mode == GameLayout.Mode.COMPACT_PORT && selected != null);
        startBtn.visible = startVisible;
        toggleBtn.visible = lay.showToggle && startVisible;
        // Schmale Kacheln bekommen eigene, kompakte Darstellung.
        Button.Painter startPainter = compact && lay.start.w < lay.start.h * 1.2 ? this::paintStartTile : null;
        startBtn.painter = startPainter;
        for (int i = 0; i < cards.length; i++) {
            cards[i].bounds(lay.tiles[i].x, lay.tiles[i].y, lay.tiles[i].w, lay.tiles[i].h);
            cards[i].visible = tilesVisible();
        }
        layoutUpgradePanel();
        layoutOverlay();
    }

    private boolean tilesVisible() {
        switch (lay.mode) {
            case DOCKED:
                return selected == null;
            case STACKED:
                return true;
            case COMPACT_LAND:
                return lay.showTiles;
            default:
                return lay.showTiles && !lay.panelReplacesTiles;
        }
    }

    private void layoutUpgradePanel() {
        double u = app.vp.u;
        GameLayout.Rect p = lay.panel;
        switch (lay.mode) {
            case COMPACT_LAND: {
                double k = lay.k;
                double gap = 4 * k;
                double rs = p.h / ((34 + 3 * 52 + 34 + 38 + 5 * 4) * k);
                double y = p.y + (34 * k + gap) * rs;
                for (int i = 0; i < 3; i++) {
                    trackBtns[i].bounds(p.x, y, p.w, 52 * k * rs);
                    y += 52 * k * rs + gap;
                }
                modeBtn.bounds(p.x, y, p.w, 34 * k * rs);
                y += 34 * k * rs + gap;
                sellBtn.bounds(p.x, y, p.w, 38 * k * rs);
                break;
            }
            case COMPACT_PORT: {
                double k = lay.k;
                double gap = 4 * k;
                double w = (p.w - 2 * gap) / 3;
                double th = Math.min(64 * k, p.h * 0.6);
                for (int i = 0; i < 3; i++) {
                    trackBtns[i].bounds(p.x + i * (w + gap), p.y, w, th);
                }
                double y = p.y + th + gap;
                double rh = p.h - th - gap;
                double w1 = (p.w - 2 * gap) * 0.26;
                double w2 = (p.w - 2 * gap) * 0.38;
                double w3 = p.w - 2 * gap - w1 - w2;
                modeBtn.bounds(p.x + w1 + gap, y, w2, rh);
                sellBtn.bounds(p.x + w1 + w2 + 2 * gap, y, w3, rh);
                break;
            }
            default: {
                double gap = 6 * u;
                double rows = 5.4;
                double rowH = Math.min(58 * u, (p.h - gap * 4) / rows);
                double y = p.y;
                sellBtn.bounds(p.x + p.w * 0.52, y, p.w * 0.48, rowH * 0.8);
                y += rowH * 0.8 + gap;
                for (int i = 0; i < 3; i++) {
                    trackBtns[i].bounds(p.x, y, p.w, rowH);
                    y += rowH + gap;
                }
                modeBtn.bounds(p.x, y, p.w, rowH * 0.78);
                break;
            }
        }
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

    private double toWorldX(double sx, double sy) {
        return lay.worldX(sx, sy);
    }

    private double toWorldY(double sx, double sy) {
        return lay.worldY(sx, sy, world.height);
    }

    private boolean onMap(double sx, double sy) {
        return lay.onMap(sx, sy);
    }

    // ----------------------------------------------------------------------------------------- Aktionen

    private double[] speeds() {
        return world.endless ? SPEEDS_ENDLESS : SPEEDS;
    }

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
        speedIndex = (speedIndex + 1) % speeds().length;
        speedBtn.label = (int) speeds()[speedIndex] + "x";
    }

    private void toggleRail() {
        railWanted = !railWanted;
        app.platform.store().put(RAIL_KEY, railWanted ? "1" : "0");
        if (!railWanted) {
            cancelPlacing();
        }
    }

    private boolean canBuild(TowerType t) {
        return !progressOn || progress.isUnlocked(t);
    }

    /** Wählt einen Turm zum Bauen aus – oder erklärt, warum er noch gesperrt ist. */
    private boolean beginPlacing(TowerType t, boolean showHint) {
        if (!canBuild(t)) {
            deny(t.label + " ab Spielerlevel " + t.unlockLevel);
            return false;
        }
        if (world.money < t.cost) {
            deny("Zu wenig Geld für " + t.label);
            return false;
        }
        placing = t;
        select(null);
        ghostOnMap = false;
        if (showHint) {
            showToast(t.label + ": " + t.tagline + " – tippe auf die Karte", t.color);
        }
        return true;
    }

    private void setOverlay(Overlay o) {
        overlay = o;
        overlayIn = 0;
        overlayUi.clear();
        cancelPlacing();
        if (o == Overlay.NONE) {
            return;
        }
        if (o == Overlay.PAUSE) {
            trySaveRun();
        }
        String back = editorReturn != null ? "ZURÜCK ZUM EDITOR" : "HAUPTMENÜ";
        Runnable leave = () -> {
            if (editorReturn != null) {
                app.goTo(new EditorScene(app, editorReturn));
            } else {
                trySaveRun();
                app.goTo(new MenuScene(app));
            }
        };
        Runnable again = () -> {
            if (progressOn) {
                app.saves.endRun(level.id); // bewusst neu begonnen: der alte Lauf wird verworfen
            }
            app.goTo(new GameScene(app, level, editorReturn, startedEndless, null));
        };
        Button a;
        Button b;
        Button c = null;
        if (o == Overlay.PAUSE) {
            a = new Button("WEITER", Icon.PLAY, Theme.GREEN, () -> setOverlay(Overlay.NONE));
            b = new Button("NEU STARTEN", Icon.LOOP, Theme.YELLOW, again);
            c = new Button(back, Icon.HOME, Theme.MAGENTA, leave);
        } else if (o == Overlay.WON) {
            a = new Button("ENDLOS WEITER", Icon.WAVES, Theme.MAGENTA, this::continueEndless);
            b = new Button("NOCHMAL", Icon.LOOP, Theme.GREEN, again);
            c = new Button(back, Icon.HOME, Theme.CYAN, leave);
        } else {
            a = new Button("NOCHMAL", Icon.LOOP, Theme.YELLOW, again);
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

    /** Nach dem Sieg über die letzten festen Wellen weiterspielen: ab jetzt Endlosmodus mit Meisterstufen. */
    private void continueEndless() {
        world.enableEndless();
        ended = false;
        endDelay = 0;
        celebrate = 0;
        speedIndex = Math.min(speedIndex, speeds().length - 1);
        setOverlay(Overlay.NONE);
        showBanner("ENDLOS", "Meisterstufen freigeschaltet!", Theme.MAGENTA, 3.4);
        if (progressOn) {
            trySaveRun();
        }
    }

    private void select(Tower t) {
        selected = t;
        sellArmed = 0;
        if (t != null) {
            cancelPlacing();
        }
        applyLayout();
    }

    private void cancelPlacing() {
        pending = false;
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

    // ------------------------------------------------------------------------------- Profil und Speichern

    /** Verbucht Abschüsse und Rekord im Profil und schreibt es weg. */
    private void commitStats() {
        if (!progressOn) {
            return;
        }
        int dk = world.kills - killsCommitted;
        if (dk > 0) {
            progress.kills = (int) Math.min(Progress.MAX_XP, (double) progress.kills + dk);
            killsCommitted = world.kills;
        }
        int before = progress.best(level.id, world.endless);
        int medalBefore = progress.medal(level.id);
        int levelBefore = progress.level();
        progress.onRecord(level.id, world.clearedWaves(), world.endless);
        if (world.clearedWaves() > before && before > 0) {
            newRecord = true;
        }
        int medalNow = progress.medal(level.id);
        if (medalNow > medalBefore) {
            int xp = Medals.xpBetween(medalBefore, medalNow);
            progress.addXp(xp);
            xpRun += xp;
            medalText = Medals.NAMES[medalNow - 1];
            showBanner(Medals.NAMES[medalNow - 1] + "-MEDAILLE!", "Welle " + Medals.WAVES[medalNow - 1] + " geschafft  ·  +" + xp
                    + " XP", Medals.COLORS[medalNow - 1], 3.6);
            fx.fireworkLater(0.0, world.width * 0.5, world.height * 0.4, Medals.COLORS[medalNow - 1], 2.2);
            fx.fireworkLater(0.25, world.width * 0.3, world.height * 0.3, Medals.COLORS[medalNow - 1], 1.8);
            fx.fireworkLater(0.5, world.width * 0.7, world.height * 0.3, Medals.COLORS[medalNow - 1], 1.8);
            if (progress.level() > levelBefore) {
                onLevelUp(levelBefore, progress.level());
            }
        }
        app.commitProgress();
    }

    /** Speichert den Lauf, wenn der Zustand es erlaubt (alle Gegner der laufenden Wellen sind schon erschienen). */
    private void trySaveRun() {
        if (!progressOn || ended || world.state != World.State.RUNNING) {
            return;
        }
        if (world.waveIndex == 0 && world.towers.isEmpty()) {
            return;
        }
        WorldSnapshot snap = world.snapshot();
        if (snap == null) {
            return;
        }
        commitStats();
        app.saves.saveRun(RunSave.of(level.id, app.saves.now(), snap));
        app.cloud.markDirty();
    }

    @Override
    public void onSuspend() {
        trySaveRun();
        if (overlay == Overlay.NONE && world.state == World.State.RUNNING) {
            setOverlay(Overlay.PAUSE);
        }
    }

    @Override
    public void onExit() {
        if (progressOn && !ended) {
            commitStats();
        }
    }

    private void onLevelUp(int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int l = from + 1; l <= to; l++) {
            for (TowerType t : Progress.unlockedAt(l)) {
                if (sb.length() > 0) {
                    sb.append(" + ");
                }
                sb.append(t.label);
            }
        }
        String sub = sb.length() > 0 ? "Neu: " + sb : Progress.titleFor(to);
        showBanner("LEVEL " + to, sub, Theme.MAGENTA, 3.4);
        for (int i = 0; i < 4; i++) {
            fx.fireworkLater(0.18 * i, world.width * (0.25 + 0.17 * i), world.height * (0.3 + 0.1 * (i % 2)),
                    Colors.hsv(0.78 + 0.05 * i, 0.8, 1), 1.7);
        }
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
        railT.target = railWanted ? 1 : 0;
        railT.update(dt);
        if (Math.abs(railT.value - laidOutRail) > 0.002 || (selected != null) != laidOutSelected) {
            applyLayout();
        }

        fx.beginFrame();
        if (overlay == Overlay.NONE || overlay == Overlay.WON || overlay == Overlay.LOST) {
            boolean running = overlay == Overlay.NONE;
            if (running) {
                double[] sp = speeds();
                int steps = stepper.advance(dt * sp[Math.min(speedIndex, sp.length - 1)]);
                for (int i = 0; i < steps; i++) {
                    world.step();
                }
                saveTimer += dt;
                if (saveTimer >= AUTOSAVE || saveSoon) {
                    saveTimer = 0;
                    saveSoon = false;
                    trySaveRun();
                }
            }
            for (int i = 0; i < world.projectiles.size(); i++) {
                listener.trail(world.projectiles.get(i), world.projectiles.get(i).x, world.projectiles.get(i).y);
            }
        }
        listener.update(dt);
        if (placing != null && ghostOnMap) {
            if (!shownInit) {
                shownX = ghostX;
                shownY = ghostY;
                shownInit = true;
            } else {
                shownX = Mathx.expDecay(shownX, ghostX, 34, dt);
                shownY = Mathx.expDecay(shownY, ghostY, 34, dt);
            }
        } else {
            shownInit = false;
        }

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
        if (labelWave != world.waveIndex || labelEndless != world.endless) {
            labelWave = world.waveIndex;
            labelEndless = world.endless;
            startBtn.label = world.waveIndex == 0 ? "START" : "WELLE " + (world.waveIndex + 1);
            if (speedIndex >= speeds().length) {
                speedIndex = 0;
            }
            speedBtn.label = (int) speeds()[speedIndex] + "x";
        }
        autoBtn.selected = world.autoStart;
        speedBtn.selected = speedIndex > 0;
    }

    // ----------------------------------------------------------------------------------------- Zeichnen

    private void mapTransform(Gfx g) {
        if (lay.rotated) {
            g.translate(lay.mapX + lay.mapW, lay.mapY);
            g.rotate(Math.PI / 2);
        } else {
            g.translate(lay.mapX, lay.mapY);
        }
        g.scale(lay.mapScale, lay.mapScale);
    }

    @Override
    public void render(Gfx g) {
        Viewport vp = app.vp;
        double u = vp.u;
        g.fillRect(0, 0, vp.w, vp.h, Theme.BLACK);
        double alpha = stepper.alpha();
        double mapScale = lay.mapScale;

        // --- Karte ---
        Gfx wg = lay.rotated ? upright.wrap(g, -Math.PI / 2) : g;
        TowerArt.uprightAngle = lay.rotated ? -Math.PI / 2 : 0;
        g.save();
        double sx = fx.shake > 0 ? Math.sin(time * 91) * fx.shake : 0;
        double sy = fx.shake > 0 ? Math.cos(time * 77) * fx.shake : 0;
        mapTransform(g);
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
        // Auf kleinen Bildschirmen (Handy) die Gegner optisch vergrößern und die HP-Zahl lesbar halten.
        double enemyScale = Mathx.clamp(10.5 / (17 * mapScale), 1, 1.5);
        // Bei vielen Gegnern/Partikeln die Glühpässe sparen (Level of Detail), danach wiederherstellen.
        int savedQuality = Neon.quality;
        int crowd = world.enemies.size();
        double load = fx.load();
        if (crowd > 90 || load > 0.8) {
            Neon.quality = 0;
        } else if (crowd > 45 || load > 0.5) {
            Neon.quality = Math.min(Neon.quality, 1);
        }
        WorldView.drawEnemies(wg, world, alpha, time, enemyScale, 9.5 / mapScale);
        WorldView.drawProjectiles(g, world, alpha);
        WorldView.drawEffects(wg, fx);
        Neon.quality = savedQuality;
        drawGhost(g);
        g.restore();
        g.save();
        mapTransform(g);
        WorldView.drawFrame(g, world.width, world.height);
        g.restore();
        TowerArt.uprightAngle = 0;

        // Wellen-Banner über der Karte
        drawBanner(g);
        if (pending && placing != null && ghostOnMap && !mapPressed) {
            drawConfirm(g);
        }

        // --- HUD, Panel, Steuerung ---
        drawHud(g, u);
        double open = lay.compact() ? railT.value : 1;
        if (selected != null) {
            drawUpgradePanel(g, u);
        }
        if (tilesVisible()) {
            g.save();
            if (lay.compact()) {
                g.alpha(Mathx.clamp01(open * 1.4));
            }
            for (Button c : cards) {
                c.render(g);
            }
            g.restore();
        }
        if (selected == null && lay.mode == GameLayout.Mode.STACKED) {
            drawInfoPanel(g, u);
        }
        ui.render(g);
        drawToast(g, u);

        if (overlay != Overlay.NONE) {
            drawOverlay(g, u);
        }
    }

    /**
     * Wie weit (in UI-Einheiten) der Turm beim ersten Ziehen über dem Finger schwebt, damit der Finger ihn nicht verdeckt.
     * Danach wird er relativ verschoben (wie ein Trackpad): Der Finger kann irgendwo liegen und den Turm feinfühlig führen.
     */
    public static final double TOUCH_LIFT = 34;

    private static final double CONFIRM_DX = 62;

    /** Mittelpunkt und Radius der Bestätigungsknöpfe (Bildschirm): {haken x, y, abbruch x, y, radius}. */
    private double[] confirmButtons() {
        double k = Math.max(app.vp.u, 1.05);
        double r = 24 * k;
        double sx = lay.screenX(ghostX, ghostY, world.height);
        double sy = lay.screenY(ghostX, ghostY);
        double dx = CONFIRM_DX * k;
        double minX = lay.mapX + r;
        double maxX = lay.mapX + lay.mapW - r;
        double okX = Mathx.clamp(sx + dx, minX, maxX);
        double noX = Mathx.clamp(sx - dx, minX, maxX);
        if (okX - noX < r * 2.2) {
            okX = Math.min(maxX, noX + r * 2.2);
        }
        double y = Mathx.clamp(sy, lay.mapY + r, lay.mapY + lay.mapH - r);
        return new double[] {okX, y, noX, y, r};
    }

    private void drawConfirm(Gfx g) {
        double[] c = confirmButtons();
        double r = c[4];
        int ok = ghostValid ? Theme.GREEN : Colors.withAlpha(Theme.GREEN, 0.3);
        g.fillCircle(c[0], c[1], r, Colors.withAlpha(0x000000, 0.85));
        Neon.circle(g, c[0], c[1], r, 2.2, ok, ghostValid ? 7 : 0);
        Icons.draw(g, Icon.CHECK, c[0], c[1], r * 0.5, ok, ghostValid ? 4 : 0);
        g.fillCircle(c[2], c[3], r, Colors.withAlpha(0x000000, 0.85));
        Neon.circle(g, c[2], c[3], r, 2.2, Theme.RED, 7);
        Icons.draw(g, Icon.CROSS, c[2], c[3], r * 0.45, Theme.RED, 4);
    }

    private void drawGhost(Gfx g) {
        if (placing == null || !ghostOnMap) {
            return;
        }
        double pulse = 0.5 + 0.5 * Math.sin(time * 6);
        double gx = shownInit ? shownX : ghostX;
        double gy = shownInit ? shownY : ghostY;
        WorldView.drawRange(g, gx, gy, placing.range, placing.color, time, ghostValid);
        g.save();
        g.alpha(ghostValid ? 0.85 : 0.55);
        int tint = ghostValid ? placing.color : Theme.RED;
        double bob = pending ? Math.sin(time * 4) * 1.6 : 0;
        TowerArt.drawIcon(g, placing, gx, gy + bob, World.TOWER_RADIUS * (pending ? 1.06 : 1), time);
        g.restore();
        if (!ghostValid) {
            Neon.circle(g, gx, gy, World.TOWER_RADIUS + 3 + pulse * 2, 2.2, tint, 6);
            Icons.draw(g, Icon.CROSS, gx, gy, 11, Theme.RED, 3);
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
        double mw = lay.mapW;
        double cx = lay.mapX + mw / 2;
        double cy = lay.mapY + lay.mapH * (lay.rotated ? 0.3 : 0.36);
        double h = Math.min(54 * app.vp.u, mw * 0.11);
        double tw = NeonText.width(bannerTitle, h);
        if (tw > mw * 0.92) {
            h *= mw * 0.92 / tw;
        }
        h = Math.max(h, 18);
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
        double fs = Math.max(12, 15 * u);
        double w = Math.min(lay.mapW + 10, g.textWidth(toast, fs, true) + 34 * u);
        double h = 34 * u;
        double cx = lay.mapX + lay.mapW / 2;
        double y = lay.mapY + lay.mapH - h - 14 * u;
        g.save();
        g.alpha(a);
        g.fillRoundRect(cx - w / 2, y, w, h, h / 2, Colors.withAlpha(0x000000, 0.88));
        Neon.roundRect(g, cx - w / 2, y, w, h, h / 2, 1.6, toastColor, 5);
        double shrink = Math.min(1, (w - 20 * u) / Math.max(1, g.textWidth(toast, fs, true)));
        g.text(toast, cx, y + h / 2, fs * shrink, Theme.TEXT, Gfx.ALIGN_CENTER, true);
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
        double size = Math.min(h * 0.5, (x + w - tx - h * 0.2) / Math.max(1, text.length() * 0.62));
        g.text(text, tx, y + h / 2, Math.max(8, size), Theme.TEXT, Gfx.ALIGN_LEFT, true);
    }

    private String waveText(boolean shortForm) {
        if (world.endless) {
            return (shortForm ? "W " : "WELLE ") + world.waveIndex + " ∞";
        }
        return (shortForm ? "W " : "WELLE ") + Math.min(world.waveIndex, world.totalWaves()) + "/" + world.totalWaves();
    }

    private void drawHud(Gfx g, double u) {
        String lives = Integer.toString(world.lives);
        String money = Fmt.tight(Mathx.roundToInt(moneyShown.value));
        GameLayout.Rect h = lay.hud;
        switch (lay.mode) {
            case DOCKED: {
                double chipH = 34 * u;
                double gap = 6 * u;
                double w1 = h.w * 0.38;
                chip(g, h.x, h.y, w1, chipH, Icon.HEART, lives, Theme.LIFE, livesPulse.value);
                chip(g, h.x + w1 + gap, h.y, h.w - w1 - gap, chipH, Icon.GEM, money, Theme.MONEY, moneyDeny);
                double y2 = h.y + chipH + gap;
                Draw.softPanel(g, h.x, y2, h.w, chipH, chipH * 0.3, Theme.CYAN);
                g.text(waveText(false), h.x + chipH * 0.35, y2 + chipH / 2, chipH * 0.46, Theme.TEXT, Gfx.ALIGN_LEFT, true);
                g.text(world.enemiesRemaining() + " Gegner", h.x + h.w - chipH * 0.3, y2 + chipH / 2, chipH * 0.38,
                        Theme.TEXT_DIM, Gfx.ALIGN_RIGHT, false);
                drawLevelBar(g, h.x, y2 + chipH + gap, h.w, chipH, u);
                break;
            }
            case STACKED:
            case COMPACT_PORT: {
                double gap = lay.mode == GameLayout.Mode.STACKED ? 6 * u : 4 * lay.k;
                double w1 = h.w * 0.20;
                double w2 = h.w * 0.27;
                double w3 = h.w * 0.15;
                double w4 = h.w - w1 - w2 - w3 - gap * 3;
                double x = h.x;
                chip(g, x, h.y, w1, h.h, Icon.HEART, lives, Theme.LIFE, livesPulse.value);
                x += w1 + gap;
                chip(g, x, h.y, w2, h.h, Icon.GEM, money, Theme.MONEY, moneyDeny);
                x += w2 + gap;
                chip(g, x, h.y, w3, h.h, Icon.STAR, Integer.toString(progress.level()), Theme.MAGENTA, 0);
                x += w3 + gap;
                Draw.softPanel(g, x, h.y, w4, h.h, h.h * 0.3, Theme.CYAN);
                boolean small = h.h < 34 * u;
                if (small) {
                    g.text(waveText(true), x + w4 / 2, h.y + h.h * 0.5, h.h * 0.42, Theme.TEXT, Gfx.ALIGN_CENTER, true);
                } else {
                    g.text(waveText(false), x + w4 / 2, h.y + h.h * 0.36, h.h * 0.36, Theme.TEXT, Gfx.ALIGN_CENTER, true);
                    g.text(world.enemiesRemaining() + " Gegner", x + w4 / 2, h.y + h.h * 0.74, h.h * 0.26,
                            Theme.TEXT_DIM, Gfx.ALIGN_CENTER, false);
                }
                if (progressOn) {
                    double bw = h.w;
                    g.fillRect(h.x, h.y + h.h + 1, bw, 2, Colors.withAlpha(Theme.MAGENTA, 0.2));
                    g.fillRect(h.x, h.y + h.h + 1, bw * progress.levelFraction(), 2, Colors.withAlpha(Theme.MAGENTA, 0.9));
                }
                break;
            }
            default: {
                // Handy quer: eine schwebende, halbtransparente Kopfzeile über der Karte
                double k = lay.k;
                g.save();
                g.alpha(0.88);
                g.fillRoundRect(h.x, h.y, h.w, h.h, h.h * 0.4, Colors.withAlpha(0x000000, 0.62));
                g.strokeRoundRect(h.x, h.y, h.w, h.h, h.h * 0.4, 1, Colors.withAlpha(Theme.CYAN, 0.4));
                double cy = h.y + h.h / 2;
                double fs = h.h * 0.5;
                double x = h.x + 8 * k;
                Icons.draw(g, Icon.HEART, x + fs * 0.5, cy, fs * 0.5, Theme.LIFE, 2);
                x += fs * 1.2;
                g.text(lives, x, cy, fs, Theme.TEXT, Gfx.ALIGN_LEFT, true);
                x += g.textWidth(lives, fs, true) + 10 * k;
                Icons.draw(g, Icon.GEM, x + fs * 0.5, cy, fs * 0.5, Theme.MONEY, 2);
                x += fs * 1.2;
                g.text(money, x, cy, fs, moneyDeny > 0.1 ? Theme.RED : Theme.TEXT, Gfx.ALIGN_LEFT, true);
                x += g.textWidth(money, fs, true) + 10 * k;
                String w = waveText(true);
                g.text(w, x, cy, fs, Theme.CYAN, Gfx.ALIGN_LEFT, true);
                x += g.textWidth(w, fs, true) + 10 * k;
                String lv = "LV " + progress.level();
                if (x + g.textWidth(lv, fs, true) < h.x + h.w - 4 * k) {
                    g.text(lv, x, cy, fs, Theme.MAGENTA, Gfx.ALIGN_LEFT, true);
                }
                g.restore();
                break;
            }
        }
    }

    /** Dritte Zeile der Seitenleiste: Spielerlevel mit Titel und Fortschrittsbalken. */
    private void drawLevelBar(Gfx g, double x, double y, double w, double h, double u) {
        Draw.softPanel(g, x, y, w, h, h * 0.3, Theme.MAGENTA);
        double is = h * 0.28;
        Icons.draw(g, Icon.STAR, x + h * 0.2 + is, y + h * 0.4, is, Theme.MAGENTA, 3);
        g.text("LEVEL " + progress.level(), x + h * 0.2 + is * 2 + h * 0.12, y + h * 0.37, h * 0.36, Theme.TEXT,
                Gfx.ALIGN_LEFT, true);
        g.text(progress.title(), x + w - h * 0.3, y + h * 0.37, h * 0.28, Theme.TEXT_DIM, Gfx.ALIGN_RIGHT, false);
        double bx = x + h * 0.3;
        double bw = w - h * 0.6;
        double by = y + h * 0.74;
        g.fillRoundRect(bx, by - 2, bw, 4, 2, Colors.withAlpha(Theme.MAGENTA, 0.22));
        double f = progressOn ? progress.levelFraction() : 0;
        if (f > 0) {
            g.fillRoundRect(bx, by - 2, Math.max(4, bw * f), 4, 2, Theme.MAGENTA);
        }
    }

    // ---------------------------------------------------------------------------------- Shop & Panel

    private void paintCard(Gfx g, Button b, TowerType type, int index) {
        double w = b.w;
        double h = b.h;
        boolean unlocked = canBuild(type);
        boolean can = unlocked && world.money >= type.cost;
        boolean active = placing == type;
        double hv = Math.max(b.hover.value, active ? 1 : 0);
        int c = type.color;
        double r = Math.min(w, h) * 0.2;
        g.fillRoundRect(-w / 2, -h / 2, w, h, r, Colors.withAlpha(Theme.PANEL, 0.94));
        g.fillRoundRect(-w / 2, -h / 2, w, h, r, Colors.withAlpha(c, (unlocked ? 0.04 : 0.0) + 0.13 * hv + 0.18 * b.press.value));
        Neon.roundRect(g, -w / 2, -h / 2, w, h, r, 1.8, Colors.withAlpha(c, !unlocked ? 0.22 : (can ? 0.95 : 0.4)),
                unlocked ? 2 + 7 * hv : 0);
        g.save();
        g.alpha(!unlocked ? 0.32 : (can ? 1 : 0.5));
        boolean horizontal = w > h * 1.5;
        String cost = Fmt.compact(type.cost);
        if (horizontal) {
            double ir = h * 0.33;
            TowerArt.drawIcon(g, type, -w / 2 + h * 0.52, 0, ir, app.time);
            double tx = -w / 2 + h * 1.02;
            g.text(type.label, tx, -h * 0.17, Math.max(11, h * 0.27), Theme.TEXT, Gfx.ALIGN_LEFT, true);
            if (unlocked) {
                Icons.draw(g, Icon.GEM, tx + h * 0.1, h * 0.2, h * 0.13, Theme.MONEY, 2);
                g.text(cost, tx + h * 0.28, h * 0.2, Math.max(11, h * 0.26), can ? Theme.MONEY : Theme.RED,
                        Gfx.ALIGN_LEFT, true);
            }
        } else {
            double ir = Math.min(w * 0.34, h * 0.27);
            TowerArt.drawIcon(g, type, 0, -h * 0.18, ir, app.time);
            if (unlocked) {
                double cs = Math.max(9, Math.min(w * 0.22, h * 0.19));
                double tw = g.textWidth(cost, cs, true);
                double gem = Math.min(w * 0.09, h * 0.09);
                double x0 = -(tw + gem * 2.4) / 2;
                Icons.draw(g, Icon.GEM, x0 + gem, h * 0.31, gem, Theme.MONEY, 2);
                g.text(cost, x0 + gem * 2.4, h * 0.31, cs, can ? Theme.MONEY : Theme.RED, Gfx.ALIGN_LEFT, true);
            }
        }
        g.restore();
        if (!unlocked) {
            // Schloss über dem Turm, darunter das Level, ab dem er frei wird
            double ls = Math.min(w, h) * (horizontal ? 0.2 : 0.2);
            double lx = horizontal ? w / 2 - h * 0.62 : 0;
            double ly = horizontal ? 0 : -h * 0.12;
            Icons.draw(g, Icon.LOCK, lx, ly, ls, Colors.withAlpha(Theme.TEXT_DIM, 0.95), 0);
            String need = "LV " + type.unlockLevel;
            if (horizontal) {
                g.text(need, -w / 2 + h * 1.02, h * 0.2, Math.max(10, h * 0.24), Theme.TEXT_DIM, Gfx.ALIGN_LEFT, true);
            } else {
                g.text(need, 0, h * 0.31, Math.max(9, Math.min(w * 0.24, h * 0.2)), Theme.TEXT_DIM, Gfx.ALIGN_CENTER, true);
            }
        }
        if (!app.platform.touchPrimary() && horizontal) {
            g.text(Integer.toString(index + 1), -w / 2 + 7, -h / 2 + 9, 10, Colors.withAlpha(Theme.TEXT, 0.55),
                    Gfx.ALIGN_LEFT, true);
        }
    }

    private void paintStartTile(Gfx g, Button b) {
        double w = b.w;
        double h = b.h;
        boolean on = b.enabled;
        int c = Theme.GREEN;
        double r = Math.min(w, h) * 0.22;
        double hv = Math.max(b.hover.value, 0);
        g.fillRoundRect(-w / 2, -h / 2, w, h, r, Colors.withAlpha(Theme.PANEL, 0.94));
        g.fillRoundRect(-w / 2, -h / 2, w, h, r, Colors.withAlpha(c, 0.07 + 0.1 * hv + 0.22 * b.press.value));
        Neon.roundRect(g, -w / 2, -h / 2, w, h, r, 2, c, 4 + 6 * hv);
        Icons.draw(g, Icon.PLAY, 0, -h * 0.14, Math.min(w * 0.3, h * 0.24), c, 5);
        String t = world.waveIndex == 0 ? "START" : "W" + (world.waveIndex + 1);
        g.text(t, 0, h * 0.32, Math.max(9, Math.min(w * 0.24, h * 0.17)), Theme.TEXT, Gfx.ALIGN_CENTER, true);
    }

    private void paintToggle(Gfx g, Button b) {
        double w = b.w;
        double h = b.h;
        double hv = b.hover.value;
        double r = Math.min(w, h) * 0.22;
        g.fillRoundRect(-w / 2, -h / 2, w, h, r, Colors.withAlpha(Theme.PANEL, 0.94));
        Neon.roundRect(g, -w / 2, -h / 2, w, h, r, 1.6, Colors.withAlpha(Theme.TEXT_DIM, 0.8 + 0.2 * hv), 2 + 4 * hv);
        // Pfeil: zeigt dorthin, wohin sich die Leiste einklappt (bzw. von dort aus wieder heraus)
        double ang;
        if (lay.mode == GameLayout.Mode.COMPACT_PORT) {
            ang = railWanted ? Math.PI : 0;
        } else {
            ang = railWanted ? Math.PI / 2 : -Math.PI / 2;
        }
        g.save();
        g.rotate(ang);
        Icons.draw(g, Icon.UP, 0, 0, Math.min(w, h) * 0.28, Theme.TEXT_DIM, 0);
        g.restore();
    }

    private void drawUpgradePanel(Gfx g, double u) {
        if (selected == null) {
            return;
        }
        Tower t = selected;
        String stufe = "Stufe " + t.totalLevels() + (world.endless ? "" : "/15");
        switch (lay.mode) {
            case COMPACT_LAND: {
                GameLayout.Rect p = lay.panel;
                double k = lay.k;
                g.fillRoundRect(p.x - 4 * k, p.y - 4 * k, p.w + 8 * k, p.h + 8 * k, 10 * k, Colors.withAlpha(0x000000, 0.86));
                g.strokeRoundRect(p.x - 4 * k, p.y - 4 * k, p.w + 8 * k, p.h + 8 * k, 10 * k, 1.2,
                        Colors.withAlpha(t.type.color, 0.5));
                double hh = 34 * k * (p.h / ((34 + 3 * 52 + 34 + 38 + 5 * 4) * k));
                TowerArt.drawIcon(g, t.type, p.x + hh * 0.5, p.y + hh / 2, hh * 0.34, app.time);
                g.text(t.type.label, p.x + hh * 1.0, p.y + hh * 0.34, Math.max(11, hh * 0.34), Theme.TEXT,
                        Gfx.ALIGN_LEFT, true);
                g.text(stufe, p.x + hh * 1.0, p.y + hh * 0.74, Math.max(9, hh * 0.27), Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
                break;
            }
            case COMPACT_PORT: {
                GameLayout.Rect p = lay.panel;
                double k = lay.k;
                // Kennung des Turms links neben "Ziel" und "Verkaufen"
                double rh = modeBtn.h;
                double bx = p.x;
                double bw = modeBtn.x - 4 * k - p.x;
                Draw.softPanel(g, bx, modeBtn.y, bw, rh, rh * 0.25, t.type.color);
                TowerArt.drawIcon(g, t.type, bx + rh * 0.5, modeBtn.y + rh / 2, rh * 0.3, app.time);
                g.text(world.endless ? Integer.toString(t.totalLevels()) : t.totalLevels() + "/15", bx + rh * 0.95,
                        modeBtn.y + rh / 2, Math.max(10, rh * 0.36), Theme.TEXT, Gfx.ALIGN_LEFT, true);
                break;
            }
            default: {
                // Kopfzeile: Symbol, Name, Gesamtstufe
                double hx = lay.panel.x;
                double hy = lay.panel.y;
                double hh = sellBtn.h;
                Draw.softPanel(g, hx, hy, lay.panel.w * 0.5 - 6 * u, hh, hh * 0.25, t.type.color);
                TowerArt.drawIcon(g, t.type, hx + hh * 0.55, hy + hh / 2, hh * 0.34, app.time);
                g.text(t.type.label, hx + hh * 1.0, hy + hh * 0.34, Math.max(11, hh * 0.3), Theme.TEXT, Gfx.ALIGN_LEFT, true);
                g.text(stufe, hx + hh * 1.0, hy + hh * 0.7, Math.max(10, hh * 0.24), Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
                break;
            }
        }
        sellBtn.render(g);
        for (Button b : trackBtns) {
            b.render(g);
        }
        modeBtn.render(g);
    }

    // ------------------------------------------------------------------------- Info-Panel (Hochformat)

    /** Im gestapelten Hochformat ist unter dem Shop Platz: Werte des gewählten Turms oder Vorschau der nächsten Welle. */
    private void drawInfoPanel(Gfx g, double u) {
        GameLayout.Rect p = lay.info;
        if (p.h < 70 * u) {
            return;
        }
        if (placing != null) {
            drawTowerInfo(g, placing, u);
        } else {
            drawWavePreview(g, u);
        }
    }

    private void drawTowerInfo(Gfx g, TowerType t, double u) {
        GameLayout.Rect p = lay.info;
        double x = p.x;
        double y = p.y;
        double w = p.w;
        double h = Math.min(p.h, 150 * u);
        Draw.softPanel(g, x, y, w, h, 14 * u, t.color);
        double pad = 12 * u;
        TowerArt.drawIcon(g, t, x + pad + 26 * u, y + pad + 26 * u, 26 * u, app.time);
        g.text(t.label, x + pad * 2 + 52 * u, y + pad + 12 * u, 17 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        g.text(t.tagline, x + pad * 2 + 52 * u, y + pad + 36 * u, 12 * u, Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        double cw = (w - pad * 2) / 3;
        double cy = y + pad + 52 * u + (h - pad * 2 - 52 * u) / 2 + 4 * u;
        UpgradeTrack[] tracks = UpgradeTrack.values();
        String[] vals = {
            Integer.toString(Mathx.roundToInt(t.range)),
            Integer.toString(t.damage),
            Mathx.round1(1.0 / t.interval) + "/s"
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
        GameLayout.Rect p = lay.info;
        double x = p.x;
        double y = p.y;
        double w = p.w;
        double h = Math.min(p.h, 230 * u);
        Draw.softPanel(g, x, y, w, h, 14 * u, Theme.CYAN);
        double pad = 12 * u;
        WaveDef next = world.nextWave();
        if (next == null) {
            g.text("ALLE WELLEN GESTARTET", x + w / 2, y + h / 2, 14 * u, Theme.TEXT_DIM, Gfx.ALIGN_CENTER, true);
            return;
        }
        g.text("NÄCHSTE WELLE " + (world.waveIndex + 1), x + pad, y + pad + 8 * u, 13 * u, Theme.TEXT,
                Gfx.ALIGN_LEFT, true);
        g.text("Bonus +" + Fmt.compact(next.bonus), x + w - pad, y + pad + 8 * u, 12 * u, Theme.MONEY, Gfx.ALIGN_RIGHT, true);
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
                return Integer.toString(Mathx.roundToInt(t.type.range * track.multAt(lvl)));
            case DAMAGE:
                double d = t.type.damage * track.multAt(lvl);
                return Fmt.compact(d >= Tower.MAX_DAMAGE ? Tower.MAX_DAMAGE : Math.max(1, Mathx.roundToInt(d)));
            case SPEED:
            default: {
                double perSec = UpgradeTrack.SPEED.multAt(lvl) / t.type.interval;
                return Mathx.round1(perSec) + "/s";
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
        int max = world.maxLevel(track);
        boolean maxed = lvl >= max;
        int cost = world.upgradeCost(t, track);
        boolean can = !maxed && world.money >= cost;
        double hv = b.hover.value;
        int c = track.color;
        double r = h * 0.22;
        g.fillRoundRect(-w / 2, -h / 2, w, h, r, Colors.withAlpha(Theme.PANEL, 0.94));
        g.fillRoundRect(-w / 2, -h / 2, w, h, r, Colors.withAlpha(c, 0.04 + 0.10 * hv + 0.18 * b.press.value));
        Neon.roundRect(g, -w / 2, -h / 2, w, h, r, 1.6, Colors.withAlpha(c, maxed ? 0.45 : 0.9), 2 + 6 * hv);
        boolean tile = w < h * 2.6;
        int pips = Math.min(lvl, UpgradeTrack.MAX_LEVEL);
        String master = lvl > UpgradeTrack.MAX_LEVEL ? "+" + (lvl - UpgradeTrack.MAX_LEVEL) : "";

        if (tile) {
            // Schmale Kachel (Handy hoch): Kopf, Stufenpunkte + nächster Wert, Preis
            double labelSize = Math.max(8, h * 0.17);
            Icons.draw(g, trackIcon(track), -w / 2 + h * 0.2, -h * 0.31, h * 0.13, c, 2);
            g.text(track.label, -w / 2 + h * 0.38, -h * 0.31, labelSize, Theme.TEXT, Gfx.ALIGN_LEFT, true);
            double pip = h * 0.055;
            double px0 = -w / 2 + h * 0.14;
            for (int i = 0; i < UpgradeTrack.MAX_LEVEL; i++) {
                double px = px0 + pip + i * pip * 2.9;
                if (i < pips) {
                    g.fillCircle(px, -h * 0.04, pip, c);
                } else {
                    g.strokeCircle(px, -h * 0.04, pip, 1.1, Colors.withAlpha(c, 0.45));
                }
            }
            if (!master.isEmpty()) {
                g.text(master, px0 + pip * 2 + 5 * pip * 2.9, -h * 0.04, Math.max(8, h * 0.17), Theme.YELLOW,
                        Gfx.ALIGN_LEFT, true);
            }
            if (!maxed) {
                g.text("› " + statText(t, track, true), w / 2 - h * 0.1, -h * 0.04, Math.max(8, h * 0.16),
                        Theme.TEXT_DIM, Gfx.ALIGN_RIGHT, false);
            } else {
                g.text(statText(t, track, false), w / 2 - h * 0.1, -h * 0.04, Math.max(8, h * 0.16), Theme.TEXT_DIM,
                        Gfx.ALIGN_RIGHT, false);
            }
            double bh = h * 0.30;
            double by = h * 0.31;
            if (maxed) {
                g.text("MAX", 0, by, h * 0.2, Theme.TEXT_DIM, Gfx.ALIGN_CENTER, true);
            } else {
                int bc = can ? Theme.GREEN : Theme.RED;
                double bw = w - h * 0.2;
                g.fillRoundRect(-bw / 2, by - bh / 2, bw, bh, bh * 0.3, Colors.withAlpha(bc, can ? 0.14 : 0.07));
                Neon.roundRect(g, -bw / 2, by - bh / 2, bw, bh, bh * 0.3, 1.3, Colors.withAlpha(bc, can ? 0.95 : 0.5), can ? 3 : 0);
                String cs = Fmt.compact(cost);
                double fs = Math.max(9, Math.min(bh * 0.62, (bw - bh * 1.1) / Math.max(1, cs.length() * 0.62)));
                double tw = g.textWidth(cs, fs, true);
                double gem = bh * 0.22;
                double x0 = -(tw + gem * 2.5) / 2;
                Icons.draw(g, Icon.GEM, x0 + gem, by, gem, can ? Theme.MONEY : Colors.withAlpha(Theme.MONEY, 0.5), 0);
                g.text(cs, x0 + gem * 2.5, by, fs, can ? Theme.TEXT : Colors.withAlpha(Theme.RED, 0.9), Gfx.ALIGN_LEFT, true);
            }
            return;
        }

        Icons.draw(g, trackIcon(track), -w / 2 + h * 0.52, 0, h * 0.27, c, 3);
        double tx = -w / 2 + h * 1.0;
        double priceW = Math.min(w * (w < 240 ? 0.28 : 0.32), h * 1.5);
        double avail = (w / 2 - priceW - h * 0.12) - h * 0.1 - tx;
        double labelSize = Math.max(8, Math.min(h * 0.23, avail / Math.max(1, track.label.length() * 0.64)));
        g.text(track.label, tx, -h * 0.25, labelSize, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        // Stufenpunkte (Meisterstufen als goldene Zahl dahinter)
        double pip = Math.min(h * 0.09, avail / (5 * 2.9 + (lvl > UpgradeTrack.MAX_LEVEL ? 3.5 : 0)));
        for (int i = 0; i < UpgradeTrack.MAX_LEVEL; i++) {
            double px = tx + pip + i * pip * 2.9;
            if (i < pips) {
                g.fillCircle(px, h * 0.02, pip, c);
            } else {
                g.strokeCircle(px, h * 0.02, pip, 1.2, Colors.withAlpha(c, 0.45));
            }
        }
        if (!master.isEmpty()) {
            g.text(master, tx + pip + 5 * pip * 2.9 - pip, h * 0.02, Math.max(8, Math.min(h * 0.22, pip * 2.6)), Theme.YELLOW,
                    Gfx.ALIGN_LEFT, true);
        }
        String cur = statText(t, track, false);
        String txt = maxed ? cur : cur + " > " + statText(t, track, true);
        g.text(txt, tx, h * 0.31, Math.max(8, Math.min(h * 0.2, avail / Math.max(1, txt.length() * 0.58))), Theme.TEXT_DIM,
                Gfx.ALIGN_LEFT, false);

        // Preis
        double bw = priceW;
        double bh = h * 0.62;
        double bx = w / 2 - bw - h * 0.12;
        if (maxed) {
            g.text("MAX", bx + bw / 2, 0, h * 0.28, Theme.TEXT_DIM, Gfx.ALIGN_CENTER, true);
        } else {
            int bc = can ? Theme.GREEN : Theme.RED;
            g.fillRoundRect(bx, -bh / 2, bw, bh, bh * 0.3, Colors.withAlpha(bc, can ? 0.14 : 0.07));
            Neon.roundRect(g, bx, -bh / 2, bw, bh, bh * 0.3, 1.4, Colors.withAlpha(bc, can ? 0.95 : 0.5), can ? 4 : 0);
            Icons.draw(g, Icon.GEM, bx + bw * 0.2, 0, bh * 0.2, can ? Theme.MONEY : Colors.withAlpha(Theme.MONEY, 0.5), 0);
            String cs = Fmt.compact(cost);
            double fs = Math.min(h * 0.3, Math.min(bw * 0.3, (bw * 0.6) / Math.max(1, cs.length() * 0.62)));
            g.text(cs, bx + bw * 0.4, 0, Math.max(8, fs), can ? Theme.TEXT : Colors.withAlpha(Theme.RED, 0.9),
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
        boolean flat = w > h * 2.2 && h < 44;
        if (armed) {
            g.text("SICHER?", 0, -h * 0.02, Math.max(11, h * 0.34), Theme.TEXT, Gfx.ALIGN_CENTER, true);
        } else if (flat) {
            // Flache Zeile (Handy): Beschriftung und Erlös nebeneinander
            String v = Fmt.compact(world.sellValue(selected));
            double fs = Math.max(9, h * 0.4);
            double tw = g.textWidth("VERKAUFEN", fs, true) + g.textWidth(v, fs, true) + h * 0.9;
            if (tw > w * 0.9) {
                fs *= w * 0.9 / tw;
                tw = w * 0.9;
            }
            double x0 = -tw / 2;
            g.text("VERKAUFEN", x0, 0, fs, Theme.TEXT, Gfx.ALIGN_LEFT, true);
            x0 += g.textWidth("VERKAUFEN", fs, true) + h * 0.2;
            Icons.draw(g, Icon.GEM, x0 + h * 0.13, 0, h * 0.13, Theme.MONEY, 2);
            g.text(v, x0 + h * 0.34, 0, fs, Theme.MONEY, Gfx.ALIGN_LEFT, true);
        } else {
            g.text("VERKAUFEN", 0, -h * 0.17, Math.max(10, h * 0.26), Theme.TEXT, Gfx.ALIGN_CENTER, true);
            Icons.draw(g, Icon.GEM, -h * 0.34, h * 0.22, h * 0.13, Theme.MONEY, 2);
            g.text(Fmt.compact(world.sellValue(selected)), -h * 0.14, h * 0.22, Math.max(10, h * 0.27),
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
        double fs = Math.max(9, h * 0.32);
        if (w < 120) {
            g.text(selected.mode.label, w / 2 - h * 0.25, -h * 0.02, Math.max(8, Math.min(fs, (w - h * 1.1) / 5.5)),
                    Theme.TEXT, Gfx.ALIGN_RIGHT, true);
            return;
        }
        g.text("ZIEL", -w / 2 + h * 1.0, -h * 0.02, Math.max(9, h * 0.26), Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        g.text(selected.mode.label, w / 2 - h * 0.35, -h * 0.02, fs, Theme.TEXT, Gfx.ALIGN_RIGHT, true);
    }

    // ---------------------------------------------------------------------------------------- Overlay

    private String newTowersText() {
        StringBuilder sb = new StringBuilder();
        int now = progress.level();
        for (int l = levelAtStart + 1; l <= now; l++) {
            for (TowerType t : Progress.unlockedAt(l)) {
                if (sb.length() > 0) {
                    sb.append(" + ");
                }
                sb.append(t.label);
            }
        }
        return sb.toString();
    }

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
        double fs = Math.max(12, 15 * u);
        if (overlay != Overlay.PAUSE) {
            String stats;
            if (world.endless) {
                stats = "Endlos: Welle " + world.clearedWaves() + " geschafft   ·   " + Fmt.compact(world.kills) + " Gegner";
            } else {
                stats = "Welle " + Math.min(world.waveIndex, world.totalWaves()) + "/" + world.totalWaves()
                        + "   ·   " + world.kills + " Gegner besiegt   ·   " + world.lives + " Leben";
            }
            g.text(stats, cx, ty + th * 1.0, fs, Theme.TEXT, Gfx.ALIGN_CENTER, true);
            if (progressOn) {
                String xp = "+" + xpRun + " XP   ·   LEVEL " + progress.level();
                if (progress.level() > levelAtStart) {
                    xp = "+" + xpRun + " XP   ·   LEVEL " + levelAtStart + " → " + progress.level();
                }
                if (newRecord) {
                    xp += "   ·   NEUER REKORD!";
                }
                if (!medalText.isEmpty()) {
                    xp += "   ·   " + medalText + "!";
                }
                g.text(xp, cx, ty + th * 1.0 + fs * 1.5, fs * 0.9, Theme.MAGENTA, Gfx.ALIGN_CENTER, true);
                String nt = newTowersText();
                if (!nt.isEmpty()) {
                    g.text("Neu freigeschaltet: " + nt, cx, ty + th * 1.0 + fs * 2.9, fs * 0.9, Theme.GREEN,
                            Gfx.ALIGN_CENTER, true);
                } else if (overlay == Overlay.WON) {
                    g.text("Endlosmodus freigeschaltet", cx, ty + th * 1.0 + fs * 2.9, fs * 0.9, Theme.TEXT_DIM,
                            Gfx.ALIGN_CENTER, true);
                }
            }
        } else {
            String sub = world.endless ? level.name + "  ·  Endlos, Welle " + world.waveIndex : level.name;
            g.text(sub, cx, ty + th * 0.95, fs, Theme.TEXT_DIM, Gfx.ALIGN_CENTER, true);
        }
        overlayUi.render(g);
        g.restore();
    }

    // ----------------------------------------------------------------------------------------- Eingabe

    private int cardAt(double x, double y) {
        if (!tilesVisible()) {
            return -1;
        }
        for (int i = 0; i < cards.length; i++) {
            if (cards[i].contains(x, y) && cards[i].appear.value > 0.3) {
                return i;
            }
        }
        return -1;
    }

    private Button[] panelButtons() {
        return new Button[] {sellBtn, trackBtns[0], trackBtns[1], trackBtns[2], modeBtn};
    }

    @Override
    public void pointerDown(double x, double y, boolean touch) {
        touchMode = touch;
        if (overlay != Overlay.NONE) {
            overlayUi.pointerDown(x, y);
            return;
        }
        if (pending && placing != null && ghostOnMap) {
            double[] c = confirmButtons();
            if (Mathx.dist(x, y, c[0], c[1]) <= c[4] * 1.15) {
                if (ghostValid) {
                    placeTower(placing, ghostX, ghostY);
                    placing = null;
                    pending = false;
                    ghostOnMap = false;
                } else {
                    deny(world.checkPlacement(ghostX, ghostY) == World.PlaceCheck.ON_PATH
                            ? "Nicht auf der Gegnerspur" : "Hier kann nicht gebaut werden");
                }
                return;
            }
            if (Mathx.dist(x, y, c[2], c[3]) <= c[4] * 1.15) {
                cancelPlacing();
                return;
            }
        }
        if (selected != null) {
            for (Button b : panelButtons()) {
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
            double wx = toWorldX(x, y);
            double wy = toWorldY(x, y);
            if (placing != null) {
                if (pending && touch && ghostOnMap) {
                    // Relatives Ziehen: Der Turm behält seinen Abstand zum Finger und bewegt sich 1:1 mit, egal wo der
                    // Finger aufsetzt – kein Sprung, und der Finger verdeckt den Turm nicht.
                    double gsx = lay.screenX(ghostX, ghostY, world.height);
                    double gsy = lay.screenY(ghostX, ghostY);
                    grabbing = true;
                    grabDX = gsx - x;
                    grabDY = gsy - y;
                    return;
                }
                grabbing = false;
                pending = false;
                updateGhost(x, y, touch ? TOUCH_LIFT * app.vp.u : 0);
                return;
            }
            Tower hit = world.towerAt(wx, wy, 18 / lay.mapScale);
            select(hit == selected ? null : hit);
            if (hit != null && hit == selected) {
                sellArmed = 0;
            }
        } else if (placing == null && selected != null && !panelContains(x, y)) {
            select(null);
        }
    }

    private boolean panelContains(double x, double y) {
        return lay.panel.contains(x, y);
    }

    private void updateGhost(double sx, double sy, double offsetY) {
        dismissIntroBanner();
        ghostOnMap = onMap(sx, sy - offsetY);
        ghostX = toWorldX(sx, sy - offsetY);
        ghostY = toWorldY(sx, sy - offsetY);
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
        for (Button b : panelButtons()) {
            b.hover.target = (!touch && selected != null && b.contains(x, y)) ? 1 : 0;
            if (pressedPanel == b && !b.contains(x, y)) {
                b.press.target = 0;
            }
        }
        double u = app.vp.u;
        if (cardDown && pressed) {
            if (!dragging && Mathx.dist(x, y, dragX0, dragY0) > 12 * u) {
                if (canBuild(dragType) && world.money >= dragType.cost) {
                    dragging = true;
                    placing = dragType;
                    select(null);
                } else {
                    cardDown = false;
                    deny(canBuild(dragType) ? "Zu wenig Geld für " + dragType.label
                            : dragType.label + " ab Spielerlevel " + dragType.unlockLevel);
                }
            }
            if (dragging) {
                updateGhost(x, y, touch ? TOUCH_LIFT * u : 0);
            }
            return;
        }
        if (placing != null) {
            if (touch && pressed && mapPressed) {
                if (grabbing) {
                    updateGhost(x + grabDX, y + grabDY, 0);
                } else {
                    updateGhost(x, y, TOUCH_LIFT * u);
                }
            } else if (!touch) {
                updateGhost(x, y, 0);
            }
        } else if (!touch && !pressed) {
            hoverTower = onMap(x, y) ? world.towerAt(toWorldX(x, y), toWorldY(x, y), 6) : null;
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
                double offset = touch ? TOUCH_LIFT * app.vp.u : 0;
                updateGhost(x, y, offset);
                if (touch && ghostOnMap) {
                    // Finger weg: Der Turm schwebt weiter und wartet auf den Haken (verschieben geht per Ziehen auf der Karte).
                    pending = true;
                    showToast("Verschieben, dann Haken zum Bauen", placing.color);
                } else if (ghostOnMap && ghostValid) {
                    placeTower(dragType, ghostX, ghostY);
                    placing = null;
                    ghostOnMap = false;
                } else {
                    if (ghostOnMap) {
                        deny("Hier kann nicht gebaut werden");
                    }
                    placing = null;
                    ghostOnMap = false;
                }
            } else {
                // Antippen: Platzierungsmodus ein-/ausschalten
                if (placing == dragType) {
                    cancelPlacing();
                } else {
                    beginPlacing(dragType, true);
                }
            }
            return;
        }
        if (mapPressed) {
            mapPressed = false;
            grabbing = false;
            if (placing != null && touch && ghostOnMap) {
                pending = true;
            } else if (placing != null && onMap(x, y)) {
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
        grabbing = false;
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
            case "KeyH":
                if (lay.compact()) {
                    toggleRail();
                    return true;
                }
                return false;
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
                } else {
                    beginPlacing(TYPES[i], false);
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

    // ------------------------------------------------------------------------------ Zugriff für Tests

    World world() {
        return world;
    }

    @Override
    public double[] worldToScreen(double wx, double wy) {
        return new double[] {lay.screenX(wx, wy, world.height), lay.screenY(wx, wy)};
    }

    /** Kurzer Zustandstext für Browser-Tests (Geld, Leben, Welle, Türme, Kartenmaßstab …). */
    public String debugState() {
        return "money=" + world.money + " lives=" + world.lives + " wave=" + world.waveIndex + " cleared="
                + world.clearedWaves() + " towers=" + world.towers.size() + " kills=" + world.kills + " endless="
                + world.endless + " state=" + world.state + " overlay=" + overlay + " mapScale="
                + Mathx.round1(lay.mapScale * 1000) / 1000 + " rail=" + Mathx.round1(railT.value * 10) / 10 + " mode="
                + lay.mode + " rotated=" + lay.rotated + " selected=" + (selected != null);
    }

    /** Die Spielwelt – für Screenshot-Werkzeug und Tests (z. B. Türme direkt setzen). */
    public World testWorld() {
        return world;
    }

    /**
     * Bildschirmposition eines Bedienelements – für Screenshot-Werkzeug und Tests. Namen: {@code tile0..tile4},
     * {@code start}, {@code toggle}, {@code speed}, {@code auto}, {@code pause}, {@code track0..track2}, {@code sell},
     * {@code mode}, {@code confirm}/{@code cancel} (schwebender Turm), {@code ov0..ov2} (Schaltflächen der Einblendung), {@code world:x,y} (Weltkoordinaten). @return {x, y} oder {@code null}
     */
    public double[] anchor(String name) {
        Button b = null;
        if (name.startsWith("tile")) {
            b = cards[name.charAt(4) - '0'];
        } else if (name.startsWith("track")) {
            b = trackBtns[name.charAt(5) - '0'];
        } else if (name.equals("confirm") || name.equals("cancel")) {
            if (!pending || placing == null || !ghostOnMap) {
                return null;
            }
            double[] c = confirmButtons();
            return name.equals("confirm") ? new double[] {c[0], c[1]} : new double[] {c[2], c[3]};
        } else if (name.startsWith("ov")) {
            int i = name.charAt(2) - '0';
            b = i < overlayUi.buttons.size() ? overlayUi.buttons.get(i) : null;
        } else if (name.startsWith("world:")) {
            String[] xy = name.substring(6).split(",");
            double wx = Double.parseDouble(xy[0]);
            double wy = Double.parseDouble(xy[1]);
            return new double[] {lay.screenX(wx, wy, world.height), lay.screenY(wx, wy)};
        } else {
            switch (name) {
                case "start":
                    b = startBtn;
                    break;
                case "toggle":
                    b = toggleBtn;
                    break;
                case "speed":
                    b = speedBtn;
                    break;
                case "auto":
                    b = autoBtn;
                    break;
                case "pause":
                    b = pauseBtn;
                    break;
                case "sell":
                    b = sellBtn;
                    break;
                case "mode":
                    b = modeBtn;
                    break;
                default:
                    break;
            }
        }
        return b == null ? null : new double[] {b.cx(), b.cy()};
    }

    GameLayout layoutInfo() {
        return lay;
    }

    Button towerTile(int i) {
        return cards[i];
    }

    Button startButton() {
        return startBtn;
    }

    Button toggleButton() {
        return toggleBtn;
    }

    void forceOpenForTest(boolean open) {
        railWanted = open;
        railT.snap(open ? 1 : 0);
        applyLayout();
    }

    // ------------------------------------------------------------------------------------------- Ereignisse

    /** Ergänzt die Standard-Effekte um Spielfluss-Reaktionen (Banner, Verlust, Ende, Profil). */
    private final class Listener extends GameFx {
        Listener(Effects fx) {
            super(fx);
        }

        @Override
        public void onWaveStarted(int index, WaveDef wave) {
            boolean boss = hasBoss(wave);
            boolean quiet = world.endless && !boss && (index + 1) % 10 != 0;
            if (!quiet) {
                showBanner("WELLE " + (index + 1), boss ? "TITAN!" : wave.totalEnemies() + " Gegner",
                        boss ? Theme.RED : Theme.CYAN, 2.4);
            }
        }

        @Override
        public void onWaveCleared(int index, int bonus) {
            int xp = 0;
            if (progressOn) {
                int before = progress.level();
                xp = progress.onWaveCleared(index + 1, hasBoss(world.waveAt(index)));
                xpRun += xp;
                if (progress.level() > before) {
                    onLevelUp(before, progress.level());
                }
                commitStats();
                saveSoon = true; // erst nach dem Simulationsschritt speichern, nicht mittendrin
            }
            showToast("Welle geschafft!  +" + Fmt.compact(bonus) + (xp > 0 ? "  ·  +" + xp + " XP" : ""), Theme.GREEN);
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
            if (progressOn && !ended) {
                ended = true;
                commitStats();
                progress.gamesPlayed++;
                if (won) {
                    int lv = progress.level();
                    int gain = progress.onWon(level.id);
                    xpRun += gain;
                    if (progress.level() > lv) {
                        onLevelUp(lv, progress.level());
                    }
                }
                app.saves.endRun(level.id);
                app.commitProgress();
                if (app.cloud.connected()) {
                    app.cloud.sync();
                }
            }
        }
    }

    private static boolean hasBoss(WaveDef wave) {
        for (WaveDef.Group gr : wave.groups) {
            if (gr.type == EnemyType.BOSS) {
                return true;
            }
        }
        return false;
    }
}

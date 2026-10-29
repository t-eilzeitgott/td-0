package neontd.app;

import neontd.gfx.Gfx;
import neontd.gfx.Neon;
import neontd.level.LevelStore;
import neontd.math.Mathx;
import neontd.platform.Platform;
import neontd.progress.Progress;
import neontd.save.GistSync;
import neontd.save.SaveStore;
import neontd.scene.MenuScene;
import neontd.scene.Scene;
import neontd.ui.Transition;
import neontd.ui.Viewport;

/**
 * Das Spiel als Ganzes: hält die aktuelle Szene, leitet Eingaben weiter, animiert Szenenwechsel und passt die
 * Grafikqualität an, wenn das Gerät nicht mehr hinterherkommt. Die Plattform (Browser, Desktop) ruft nur
 * {@link #resize}, {@link #update}/{@link #render} und die Eingabemethoden auf.
 */
public final class App {
    public final Platform platform;
    public final LevelStore levels;
    /** Profil, Läufe und Cloud-Einstellungen im lokalen Speicher. */
    public final SaveStore saves;
    /** Das Spielerprofil (im Speicher gehalten; {@link #commitProgress} schreibt es weg). */
    public final Progress progress;
    /** Abgleich mit dem privaten GitHub-Gist des Spielers (inaktiv, solange nicht verbunden). */
    public final GistSync cloud;
    /** Globale Bestenlisten (best-effort). */
    public final neontd.save.Leaderboard board;
    public final Viewport vp = new Viewport();
    /** Laufzeit in Sekunden – für Animationen. */
    public double time;

    private final Transition transition = new Transition();
    private Scene scene;
    /** Die vorige Szene, solange die Überblendung läuft. */
    private Scene fading;
    private double last = -1;
    private double ema = 1.0 / 60;
    private double slowFor;
    private double fastFor;
    private boolean started;
    private double cloudTick;
    private int periodTick;

    public App(Platform platform) {
        this.platform = platform;
        this.saves = new SaveStore(platform.store(), platform::nowMillis);
        this.levels = new LevelStore(platform.store(), platform::nowMillis);
        this.progress = saves.loadProgress();
        this.cloud = new GistSync(platform.http(), saves, levels, progress);
        this.board = new neontd.save.Leaderboard(platform.http(), platform.store());
        board.setPlatformInfo(platform.describe());
        if (progress.grantLegacyCrystals()) {
            commitProgress();
        }
        checkPeriods();
        this.scene = new neontd.scene.IntroScene(this);
    }

    /** Setzt Spielerlevel und XP zurück (Einstellungen). */
    public void resetPlayerLevel() {
        progress.resetPlayerLevel(platform.nowMillis());
        commitProgress();
        cloud.flush();
    }

    /** Setzt das ganze Profil zurück und beendet alle gespeicherten Läufe (Einstellungen). Eigene Level bleiben. */
    public void resetEverything() {
        progress.resetAll(platform.nowMillis());
        for (String id : saves.runLevelIds()) {
            saves.endRun(id);
        }
        progress.refreshPeriods(today());
        progress.grantLegacyCrystals();
        commitProgress();
        cloud.flush();
    }

    /** Heutiger Tag (Tageszahl seit 1970, Ortszeit). */
    public int today() {
        return neontd.progress.Calendar.dayOf(platform.nowMillis(), platform.localOffsetMillis());
    }

    /** Stellt Tagesaufgaben und Season auf das aktuelle Datum um; schreibt das Profil bei einer Änderung weg. */
    public void checkPeriods() {
        if (progress.refreshPeriods(today())) {
            commitProgress();
        }
    }

    /** Schreibt das Profil weg und merkt es für den nächsten Cloud-Abgleich vor. */
    public void commitProgress() {
        saves.saveProgress(progress);
        cloud.markDirty();
    }

    /**
     * Die Seite/Anwendung wird unsichtbar oder beendet (Browser: Tab-Wechsel, Home-Taste am iPhone): Stand sichern
     * und – wenn verbunden – sofort in die Cloud schieben. Auf dem iPhone ist das oft die einzige Gelegenheit.
     */
    public void suspend() {
        scene.onSuspend();
        cloud.flush();
    }

    public Scene scene() {
        return scene;
    }

    /** Fenstergröße in logischen Pixeln samt Sicherheitsrändern (iPhone-Notch etc.). */
    public void resize(double w, double h, double insetL, double insetT, double insetR, double insetB) {
        vp.set(w, h, insetL, insetT, insetR, insetB);
        if (!started) {
            started = true;
            if (cloud.connected()) {
                cloud.sync();
            }
            scene.layout();
            scene.onEnter();
        } else {
            scene.layout();
        }
    }

    /** Wechselt mit Animation zu einer anderen Szene. */
    public void goTo(Scene next) {
        if (transition.active()) {
            return;
        }
        transition.start(() -> {
            scene.pointerCancel();
            scene.onExit();
            fading = scene;
            scene = next;
            scene.layout();
            scene.onEnter();
        });
    }

    public boolean transitioning() {
        return transition.active();
    }

    /**
     * Die Szene nach dem Intro: auf dem Handy im Browser zuerst „Installieren“ (die installierte App hat auf dem iPhone
     * einen eigenen Speicher – Name und Fortschritt aus dem Browser wären dort nicht da), dann die Namenswahl, sonst
     * die Startseite.
     */
    public Scene firstScene() {
        if (neontd.scene.InstallScene.shouldGate(this)) {
            return new neontd.scene.InstallScene(this, true);
        }
        return afterInstall();
    }

    /** Die Szene nach dem Installations-Hinweis: Namenswahl beim ersten Start, sonst die Startseite. */
    public Scene afterInstall() {
        return board.hasName() ? new MenuScene(this) : new neontd.scene.NameScene(this, true);
    }

    /** Fortschritt der laufenden Überblendung 0..1 (1 = keine Überblendung). */
    public double transitionProgress() {
        return transition.progress();
    }

    // ------------------------------------------------------------------------------------ Zeit, Zeichnen

    /** Schaltet die Zeit weiter. {@code nowSeconds} ist eine beliebige monotone Uhr. */
    public void update(double nowSeconds) {
        double dt = last < 0 ? 0 : Mathx.clamp(nowSeconds - last, 0, 0.1);
        last = nowSeconds;
        time += dt;
        governQuality(dt);
        cloudTick += dt;
        if (cloudTick >= 1) {
            cloudTick = 0;
            cloud.tick();
            board.flush();
            // Der Spielername der Bestenliste ist auch der Profilname (z. B. nach dem Bestätigen durch den Server)
            String n = board.playerName();
            if (n != null && !n.equals(progress.name)) {
                progress.name = n;
                commitProgress();
            }
            periodTick++;
            if (periodTick >= 5) {
                periodTick = 0;
                checkPeriods();
            }
        }
        transition.update(dt);
        if (!transition.active() && fading != null) {
            fading.onHidden();
            fading = null;
        }
        if (swipeState != SWIPE_ACTIVE) {
            swipeShown.update(dt);
            if (swipeScene != null && swipeScene != scene && swipeScene != fading) {
                swipeScene = null;
                swipeShown.snap(0);
            } else if (swipeScene == scene && swipeShown.target == 0 && swipeShown.value < 0.5) {
                swipeScene = null;
            }
        }
        scene.update(dt);
    }

    public void render(Gfx g) {
        if (fading != null && transition.active()) {
            g.save();
            g.alpha(transition.oldAlpha() * (fading == swipeScene ? 1 - transition.newAlpha() * 0.6 : 1));
            renderScene(g, fading);
            g.restore();
            g.save();
            g.alpha(transition.newAlpha());
            g.translate(0, transition.lift(vp.u));
            renderScene(g, scene);
            g.restore();
            return;
        }
        renderScene(g, scene);
    }

    public void frame(double nowSeconds, Gfx g) {
        update(nowSeconds);
        render(g);
    }

    /** Senkt bei dauerhaft niedriger Bildrate den Aufwand für Leuchteffekte und hebt ihn bei Luft wieder an. */
    private void governQuality(double dt) {
        if (dt <= 0) {
            return;
        }
        ema = ema * 0.94 + dt * 0.06;
        if (ema > 1.0 / 38) {
            slowFor += dt;
            fastFor = 0;
            if (slowFor > 1.5 && Neon.quality > 0) {
                Neon.quality--;
                slowFor = 0;
            }
        } else if (ema < 1.0 / 55) {
            fastFor += dt;
            slowFor = 0;
            if (fastFor > 12 && Neon.quality < 2) {
                Neon.quality++;
                fastFor = 0;
            }
        } else {
            slowFor = 0;
            fastFor = 0;
        }
    }

    // --------------------------------------------------------------------------------------- Eingabe

    public void pointerDown(double x, double y, boolean touch) {
        if (!transition.active()) {
            swipeDown(x, y, touch);
            scene.pointerDown(x, y, touch);
        }
    }

    public void pointerMove(double x, double y, boolean pressed, boolean touch) {
        if (!transition.active()) {
            if (swipeMove(x, y, pressed)) {
                return;
            }
            scene.pointerMove(x, y, pressed, touch);
        }
    }

    public void pointerUp(double x, double y, boolean touch) {
        if (!transition.active()) {
            if (swipeUp(x)) {
                return;
            }
            scene.pointerUp(x, y, touch);
        }
    }

    public void pointerCancel() {
        swipeState = SWIPE_NONE;
        scene.pointerCancel();
    }

    // ------------------------------------------------------------------------- Wisch-Geste „zurück“

    private static final int SWIPE_NONE = 0;
    private static final int SWIPE_MAYBE = 1;
    private static final int SWIPE_ACTIVE = 2;
    private int swipeState = SWIPE_NONE;
    private double swipeX0;
    private double swipeY0;
    private double swipeLastX;
    private double swipeLastT;
    private double swipeVel;
    /** Wie weit die Seite gerade mit dem Finger nach rechts gezogen ist (Bildschirmpunkte). */
    private double swipeDx;
    /** Angezeigter Versatz (folgt {@link #swipeDx}, gleitet nach dem Loslassen weiter oder zurück). */
    private final neontd.ui.Smooth swipeShown = new neontd.ui.Smooth(0, 22);
    /** Die Seite, zu der der Versatz gehört (sie behält ihn, während sie ausgeblendet wird). */
    private Scene swipeScene;

    private void swipeDown(double x, double y, boolean touch) {
        swipeState = touch && scene.swipeBack() ? SWIPE_MAYBE : SWIPE_NONE;
        swipeX0 = x;
        swipeY0 = y;
        swipeLastX = x;
        swipeLastT = time;
        swipeVel = 0;
    }

    /** @return true, wenn die Geste die Bewegung übernommen hat (die Seite bekommt sie dann nicht). */
    private boolean swipeMove(double x, double y, boolean pressed) {
        if (!pressed || swipeState == SWIPE_NONE) {
            return false;
        }
        double u = vp.u;
        double dx = x - swipeX0;
        double dy = y - swipeY0;
        if (swipeState == SWIPE_MAYBE) {
            if (Math.abs(dy) > 10 * u && Math.abs(dy) > Math.abs(dx)) {
                swipeState = SWIPE_NONE;          // senkrecht: die Seite scrollt
                return false;
            }
            if (dx < -10 * u) {
                swipeState = SWIPE_NONE;
                return false;
            }
            if (dx > 14 * u && dx > Math.abs(dy) * 1.4) {
                swipeState = SWIPE_ACTIVE;
                swipeScene = scene;
                scene.pointerCancel();
            } else {
                return false;
            }
        }
        double dt = Math.max(1.0 / 240, time - swipeLastT);
        swipeVel = Mathx.lerp(swipeVel, (x - swipeLastX) / dt, 0.4);
        swipeLastX = x;
        swipeLastT = time;
        swipeDx = Math.max(0, dx - 14 * u);
        swipeShown.snap(swipeDx);
        return true;
    }

    private boolean swipeUp(double x) {
        if (swipeState != SWIPE_ACTIVE) {
            swipeState = SWIPE_NONE;
            return false;
        }
        swipeState = SWIPE_NONE;
        boolean far = swipeDx > Math.min(110 * vp.u, vp.w * 0.3);
        boolean flick = swipeDx > 30 * vp.u && swipeVel > 450 * vp.u;
        if ((far || flick) && swipeVel > -150 * vp.u) {
            back();
            // gleitet hinaus, während die nächste Seite einblendet (falls „zurück“ hier nichts auslöst: zurückfedern)
            swipeShown.target = transition.active() ? vp.w * 0.45 : 0;
        } else {
            swipeShown.target = 0;
        }
        swipeDx = 0;
        return true;
    }

    /** Fortschritt der Geste 0..1 (1 = Loslassen geht zurück). */
    private double swipeProgress() {
        return Mathx.clamp01(swipeShown.value / Math.min(110 * vp.u, vp.w * 0.3));
    }

    private void renderScene(Gfx g, Scene s) {
        if (s != swipeScene || swipeShown.value < 0.5) {
            s.render(g);
            return;
        }
        double off = swipeShown.value;
        g.save();
        g.translate(off * 0.85, 0);
        s.render(g);
        g.restore();
        // Schatten am linken Rand der weggezogenen Seite und ein Zurück-Pfeil, der sich beim Ziehen füllt
        double p = swipeProgress();
        double u = vp.u;
        double r = 18 * u;
        double cx = vp.insetL + 10 * u + r + Math.min(off * 0.25, 24 * u) - r * (1 - Math.min(1, off / (40 * u)));
        double cy = vp.h / 2;
        g.save();
        g.alpha(Math.min(1, off / (40 * u)));
        g.fillCircle(cx, cy, r, neontd.gfx.Colors.lerp(neontd.gfx.Theme.SURFACE2, neontd.gfx.Theme.CYAN, p >= 1 ? 1 : p * 0.25));
        neontd.ui.Draw.chevron(g, cx - 1 * u, cy, r * 0.32, p >= 1 ? 0x000000 : neontd.gfx.Theme.TEXT, false);
        g.restore();
    }

    public void wheel(double dy) {
        if (!transition.active()) {
            scene.wheel(dy);
        }
    }

    /** @return true, wenn die Taste verarbeitet wurde (die Plattform unterdrückt dann ihre Standardaktion) */
    public boolean key(String code, boolean ctrl) {
        if (transition.active()) {
            return false;
        }
        if (code.equals("Escape")) {
            back();
            return true;
        }
        return scene.key(code, ctrl);
    }

    /** Zurück-Aktion (Escape). Im Hauptmenü ohne Wirkung. */
    public void back() {
        if (transition.active()) {
            return;
        }
        if (!scene.back() && !(scene instanceof MenuScene)) {
            goTo(new MenuScene(this));
        }
    }
}

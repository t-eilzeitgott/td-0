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
        scene.update(dt);
    }

    public void render(Gfx g) {
        if (fading != null && transition.active()) {
            g.save();
            g.alpha(transition.oldAlpha());
            fading.render(g);
            g.restore();
            g.save();
            g.alpha(transition.newAlpha());
            g.translate(0, transition.lift(vp.u));
            scene.render(g);
            g.restore();
            return;
        }
        scene.render(g);
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
            scene.pointerDown(x, y, touch);
        }
    }

    public void pointerMove(double x, double y, boolean pressed, boolean touch) {
        if (!transition.active()) {
            scene.pointerMove(x, y, pressed, touch);
        }
    }

    public void pointerUp(double x, double y, boolean touch) {
        if (!transition.active()) {
            scene.pointerUp(x, y, touch);
        }
    }

    public void pointerCancel() {
        scene.pointerCancel();
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

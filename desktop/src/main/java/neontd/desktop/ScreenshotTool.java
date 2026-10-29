package neontd.desktop;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import neontd.app.App;
import neontd.platform.KeyValueStore;
import neontd.platform.MemoryStore;
import neontd.platform.Platform;

/**
 * Entwicklungswerkzeug: Startet das Spiel ohne Fenster, führt ein kleines Skript aus (Klicks, Ziehen, Tasten,
 * Wartezeiten) und speichert Bildschirmfotos. So lassen sich alle Szenen reproduzierbar prüfen.
 *
 * <pre>
 * java -Djava.awt.headless=true neontd.desktop.ScreenshotTool OUT_DIR "size 1280 720; wait 2; shot menu"
 * </pre>
 *
 * Befehle: size W H [touch] · insets L T R B · open game|gamerich|select|editor|menu · wait SEK · shot NAME · click X Y · down X Y · move X Y · up X Y ·
 * drag X1 Y1 X2 Y2 SEK · key CODE [ctrl] · back
 */
public final class ScreenshotTool {
    private static final double DT = 1.0 / 60.0;

    private final File out;
    private final MemoryStore store = new MemoryStore();
    private boolean touch;
    private final App app;
    private final Java2DGfx gfx = new Java2DGfx();
    /** Beispieldaten statt echter Datenbank (siehe {@link DemoHttp}). */
    private final DemoHttp demo = new DemoHttp();
    /** Fester Zeitpunkt (Do, 29.10.2026, 12 Uhr MEZ), damit Arena-Woche und Tages-Challenge reproduzierbar sind. */
    private static final double CLOCK = neontd.progress.Calendar.daysFromCivil(2026, 10, 29) * 86_400_000.0 + 11 * 3_600_000.0;
    private BufferedImage image;
    private double w = 1280;
    private double h = 720;
    private double now;
    private double il;
    private double it;
    private double ir;
    private double ib;

    private ScreenshotTool(File out) {
        this.out = out;
        app = new App(new Platform() {
            @Override
            public KeyValueStore store() {
                return store;
            }

            @Override
            public boolean touchPrimary() {
                return touch;
            }

            @Override
            public double nowMillis() {
                return CLOCK;
            }

            @Override
            public double localOffsetMillis() {
                return 3_600_000.0;
            }

            @Override
            public neontd.save.Http http() {
                return demo;
            }

            @Override
            public String describe() {
                return "Screenshot-Werkzeug";
            }
        });
        resize();
    }

    private void resize() {
        image = new BufferedImage((int) w, (int) h, BufferedImage.TYPE_INT_RGB);
        app.resize(w, h, il, it, ir, ib);
    }

    private void command(String cmd) {
        if (cmd.equals("win") || cmd.equals("lose")) {
            if (app.scene() instanceof neontd.scene.GameScene) {
                ((neontd.scene.GameScene) app.scene()).testWorld().forceEnd(cmd.equals("win"));
            }
        }
    }

    private void openScene(String name) {
        if (name.startsWith("level:") || name.startsWith("levelrich:")) {
            boolean rich = name.startsWith("levelrich:");
            String id = name.substring(name.indexOf(':') + 1);
            neontd.level.LevelDef l = neontd.level.Levels.find(id);
            if (l == null) {
                throw new IllegalArgumentException("Unbekanntes Level: " + id);
            }
            if (rich) {
                l = l.copy();
                l.startMoney = 6000;
            }
            app.goTo(new neontd.scene.GameScene(app, l));
            return;
        }
        if (name.startsWith("world:")) {
            app.goTo(new neontd.scene.LevelSelectScene(app, Integer.parseInt(name.substring(6))));
            return;
        }
        switch (name) {
            case "game":
                app.goTo(new neontd.scene.GameScene(app, neontd.level.Levels.serpentine()));
                break;
            case "gamerich": {
                neontd.level.LevelDef rich = neontd.level.Levels.serpentine().copy();
                rich.startMoney = 6000;
                app.goTo(new neontd.scene.GameScene(app, rich));
                break;
            }
            case "endless": {
                neontd.level.LevelDef rich = neontd.level.Levels.serpentine().copy();
                rich.startMoney = 6000;
                app.goTo(neontd.scene.GameScene.endless(app, rich));
                break;
            }
            case "profile":
                app.goTo(new neontd.scene.ProfileScene(app));
                break;
            case "select":
                app.goTo(new neontd.scene.LevelSelectScene(app));
                break;
            case "chapter3":
                app.goTo(new neontd.scene.LevelSelectScene(app, 3));
                break;
            case "shop":
                app.goTo(new neontd.scene.ShopScene(app));
                break;
            case "shop2":
                app.goTo(new neontd.scene.ShopScene(app, 1));
                break;
            case "shop3":
                app.goTo(new neontd.scene.ShopScene(app, 2));
                break;
            case "missions":
                app.goTo(new neontd.scene.MissionsScene(app));
                break;
            case "season":
                app.goTo(new neontd.scene.MissionsScene(app, 1));
                break;
            case "achievements":
                app.goTo(new neontd.scene.AchievementsScene(app));
                break;
            case "settings":
                app.goTo(new neontd.scene.SettingsScene(app));
                break;
            case "editor":
                app.goTo(new neontd.scene.EditorScene(app, null));
                break;
            case "arena":
                app.goTo(new neontd.scene.ArenaScene(app));
                break;
            case "arenagame":
                app.goTo(neontd.scene.GameScene.arena(app, neontd.progress.Arena.of(app.today()), null));
                break;
            case "name":
                app.goTo(new neontd.scene.NameScene(app, true));
                break;
            case "daily":
                app.goTo(new neontd.scene.DailyScene(app));
                break;
            case "board":
                app.goTo(new neontd.scene.LeaderboardScene(app));
                break;
            case "menu":
                app.goTo(new neontd.scene.MenuScene(app));
                break;
            default:
                throw new IllegalArgumentException("Unbekannte Szene: " + name);
        }
    }

    private void advance(double seconds) {
        int n = (int) Math.round(seconds / DT);
        for (int i = 0; i < n; i++) {
            now += DT;
            demo.deliver();
            app.update(now);
        }
    }

    private void shot(String name) throws Exception {
        Graphics2D g2 = image.createGraphics();
        g2.setColor(java.awt.Color.BLACK);
        g2.fillRect(0, 0, image.getWidth(), image.getHeight());
        gfx.begin(g2, w, h);
        app.render(gfx);
        g2.dispose();
        out.mkdirs();
        File f = new File(out, name + ".png");
        ImageIO.write(image, "png", f);
        System.out.println("wrote " + f);
    }

    private void run(String script) throws Exception {
        for (String raw : script.split(";")) {
            String cmd = raw.trim();
            if (cmd.isEmpty()) {
                continue;
            }
            String[] p = cmd.split("\\s+");
            switch (p[0]) {
                case "size":
                    w = Double.parseDouble(p[1]);
                    h = Double.parseDouble(p[2]);
                    touch = p.length > 3 && p[3].equals("touch");
                    resize();
                    break;
                case "insets":
                    il = Double.parseDouble(p[1]);
                    it = Double.parseDouble(p[2]);
                    ir = Double.parseDouble(p[3]);
                    ib = Double.parseDouble(p[4]);
                    resize();
                    break;
                case "wait":
                    advance(Double.parseDouble(p[1]));
                    break;
                case "shot":
                    shot(p[1]);
                    break;
                case "click": {
                    double x = Double.parseDouble(p[1]);
                    double y = Double.parseDouble(p[2]);
                    app.pointerMove(x, y, false, touch);
                    app.pointerDown(x, y, touch);
                    advance(0.05);
                    app.pointerUp(x, y, touch);
                    advance(0.05);
                    break;
                }
                case "down":
                    app.pointerDown(Double.parseDouble(p[1]), Double.parseDouble(p[2]), touch);
                    break;
                case "move":
                    app.pointerMove(Double.parseDouble(p[1]), Double.parseDouble(p[2]), false, touch);
                    break;
                case "hold":
                    app.pointerMove(Double.parseDouble(p[1]), Double.parseDouble(p[2]), true, touch);
                    break;
                case "up":
                    app.pointerUp(Double.parseDouble(p[1]), Double.parseDouble(p[2]), touch);
                    break;
                case "drag": {
                    double x1 = Double.parseDouble(p[1]);
                    double y1 = Double.parseDouble(p[2]);
                    double x2 = Double.parseDouble(p[3]);
                    double y2 = Double.parseDouble(p[4]);
                    double sec = Double.parseDouble(p[5]);
                    app.pointerDown(x1, y1, touch);
                    int steps = Math.max(1, (int) Math.round(sec / DT));
                    for (int i = 1; i <= steps; i++) {
                        double t = (double) i / steps;
                        app.pointerMove(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, true, touch);
                        advance(DT);
                    }
                    app.pointerUp(x2, y2, touch);
                    advance(0.05);
                    break;
                }
                case "level": {
                    // Profil auf ein bestimmtes Spielerlevel setzen (schaltet Türme frei)
                    int lv = Integer.parseInt(p[1]);
                    app.progress.xp = neontd.progress.Progress.xpAtLevel(lv) + 10;
                    break;
                }
                case "crystals":
                    // crystals N  – Kristalle gutschreiben
                    app.progress.addCrystals(Double.parseDouble(p[1]));
                    break;
                case "own": {
                    // own TURM|SPECIAL-Schlüssel  – ohne Preis freischalten (nur für Bilder)
                    String what = p[1];
                    if (neontd.sim.Special.byKey(what) != null) {
                        app.progress.ownedSpecials.add(what);
                    } else {
                        app.progress.ownedTowers.add(what.toUpperCase());
                    }
                    break;
                }
                case "ownall":
                    for (neontd.sim.TowerType t : neontd.sim.TowerType.values()) {
                        if (t.tier == 2) {
                            app.progress.ownedTowers.add(t.name());
                        }
                    }
                    for (neontd.sim.Special sp : neontd.sim.Special.values()) {
                        app.progress.ownedSpecials.add(sp.key);
                    }
                    break;
                case "metric":
                    // metric NAME N  – Zähler erhöhen (Aufgaben und Errungenschaften)
                    app.progress.addMetric(neontd.progress.Metric.valueOf(p[1].toUpperCase()), Integer.parseInt(p[2]));
                    break;
                case "best":
                    // best LEVEL WELLE  – Rekord setzen (schaltet Medaillen und Folgelevel frei)
                    app.progress.onRecord(p[1], Integer.parseInt(p[2]), true);
                    break;
                case "stars":
                    // stars LEVEL N  – Sterne eines Levels setzen (schaltet Folgelevel und Welten frei)
                    app.progress.onStars(p[1], Integer.parseInt(p[2]));
                    break;
                case "starworld": {
                    // starworld WELT N  – alle Level einer Welt mit N Sternen
                    int w = Integer.parseInt(p[1]);
                    for (int i = 1; i <= neontd.level.Worlds.LEVELS; i++) {
                        app.progress.onStars(neontd.level.Worlds.IDS[w - 1] + "-" + i, Integer.parseInt(p[2]));
                    }
                    break;
                }
                case "name": {
                    // name TEXT  – Spielernamen eintippen und bestätigen (Namensseite) bzw. direkt anmelden
                    String text = cmd.substring(5).trim();
                    if (app.scene() instanceof neontd.scene.NameScene) {
                        neontd.scene.NameScene ns = (neontd.scene.NameScene) app.scene();
                        ns.typeForTest(text);
                        if (!text.endsWith("…")) {
                            advance(0.1);
                        }
                    } else {
                        app.board.register(text, (ok, err) -> { });
                        advance(0.1);
                    }
                    break;
                }
                case "submitname":
                    ((neontd.scene.NameScene) app.scene()).submitForTest();
                    advance(0.1);
                    break;
                case "myscore":
                    // myscore N  – eigener Bestwert in den Beispieldaten (0 = noch kein Lauf)
                    demo.myScore = Long.parseLong(p[1]);
                    break;
                case "anchor": {
                    // anchor click|hold|up NAME  – Bedienelement der Spielszene ansteuern
                    if (app.scene() instanceof neontd.scene.ArenaScene && p[2].equals("play")) {
                        double[] a = ((neontd.scene.ArenaScene) app.scene()).playAnchor();
                        run(p[1] + " " + a[0] + " " + a[1]);
                        break;
                    }
                    double[] a = ((neontd.scene.GameScene) app.scene()).anchor(p[2]);
                    if (a == null) {
                        throw new IllegalArgumentException("Unbekannter Anker: " + p[2]);
                    }
                    run(p[1] + " " + a[0] + " " + a[1]);
                    break;
                }
                case "dragto": {
                    // dragto NAME wx wy sec  – von einem Bedienelement auf einen Weltpunkt ziehen
                    neontd.scene.GameScene gs = (neontd.scene.GameScene) app.scene();
                    double[] a = gs.anchor(p[1]);
                    double[] b = gs.anchor("world:" + p[2] + "," + p[3]);
                    // Auf dem Finger-Gerät schwebt die Vorschau 56 Einheiten über dem Finger (siehe GameScene).
                    double lift = touch ? neontd.scene.GameScene.TOUCH_LIFT * app.vp.u : 0;
                    run("drag " + a[0] + " " + a[1] + " " + b[0] + " " + (b[1] + lift) + " " + p[4]);
                    double[] ok = touch ? gs.anchor("confirm") : null;
                    if (ok != null) {
                        run("click " + ok[0] + " " + ok[1]); // schwebenden Turm mit dem Haken fest bauen
                    }
                    break;
                }
                case "wclick": {
                    double[] b = app.scene().worldToScreen(Double.parseDouble(p[1]), Double.parseDouble(p[2]));
                    run("click " + b[0] + " " + b[1]);
                    break;
                }
                case "wdrag": {
                    // wdrag wx1 wy1 wx2 wy2 sec  – zwischen zwei Weltpunkten ziehen
                    double[] a = app.scene().worldToScreen(Double.parseDouble(p[1]), Double.parseDouble(p[2]));
                    double[] b = app.scene().worldToScreen(Double.parseDouble(p[3]), Double.parseDouble(p[4]));
                    run("drag " + a[0] + " " + a[1] + " " + b[0] + " " + b[1] + " " + p[5]);
                    break;
                }
                case "tap": {
                    // tap NAME  – Schaltfläche der Spielszene antippen (siehe GameScene.anchor)
                    run("anchor click " + p[1]);
                    break;
                }
                case "key":
                    app.key(p[1], p.length > 2 && p[2].equals("ctrl"));
                    advance(0.05);
                    break;
                case "back":
                    app.back();
                    advance(0.05);
                    break;
                case "open":
                    openScene(p[1]);
                    advance(1.0);
                    break;
                case "win":
                case "lose":
                    command(p[0]);
                    advance(0.05);
                    break;
                default:
                    throw new IllegalArgumentException("Unbekannter Befehl: " + cmd);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Java2DGfx.exactAdditive = true;
        if (args.length < 2) {
            System.err.println("Aufruf: ScreenshotTool OUT_DIR \"befehl; befehl; ...\"");
            System.exit(2);
        }
        new ScreenshotTool(new File(args[0])).run(args[1]);
    }
}

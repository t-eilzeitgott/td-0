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
        });
        resize();
    }

    private void resize() {
        image = new BufferedImage((int) w, (int) h, BufferedImage.TYPE_INT_RGB);
        app.resize(w, h, il, it, ir, ib);
    }

    private void openScene(String name) {
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
            case "editor":
                app.goTo(new neontd.scene.EditorScene(app, null));
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
                case "anchor": {
                    // anchor click|hold|up NAME  – Bedienelement der Spielszene ansteuern
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
                    double lift = touch ? 56 * app.vp.u : 0;
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

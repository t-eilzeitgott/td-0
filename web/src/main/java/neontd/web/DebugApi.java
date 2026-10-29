package neontd.web;

import neontd.app.App;
import neontd.level.Levels;
import neontd.progress.Progress;
import neontd.scene.EditorScene;
import neontd.scene.GameScene;
import neontd.scene.LevelSelectScene;
import neontd.scene.MenuScene;
import neontd.scene.ProfileScene;
import neontd.scene.Scene;

/**
 * Kleine Befehlsschnittstelle für automatische Browser-Tests; nur aktiv, wenn die Adresse {@code ?debug} enthält
 * ({@code window.__ntd.call("befehl")}). Liefert Bildschirmpositionen von Schaltflächen und Zustandstexte, damit Tests
 * nicht von Pixelmaßen abhängen.
 */
final class DebugApi {
    private DebugApi() {
    }

    static String handle(App app, String cmd) {
        String[] p = cmd.split(" ", 2);
        String arg = p.length > 1 ? p[1] : "";
        Scene s = app.scene();
        switch (p[0]) {
            case "anchor": {
                double[] a = null;
                if (s instanceof GameScene) {
                    a = ((GameScene) s).anchor(arg);
                } else if (s instanceof ProfileScene) {
                    a = ((ProfileScene) s).anchor(arg);
                } else if (s instanceof neontd.scene.PanelScene && arg.startsWith("modal")) {
                    a = ((neontd.scene.PanelScene) s).modalAnchor(arg.charAt(5) - '0');
                } else if (s instanceof MenuScene && arg.equals("play")) {
                    a = ((MenuScene) s).playAnchor();
                } else if (s instanceof neontd.scene.ArenaScene && arg.equals("play")) {
                    a = ((neontd.scene.ArenaScene) s).playAnchor();
                } else if (s instanceof neontd.scene.NameScene) {
                    a = arg.equals("field") ? ((neontd.scene.NameScene) s).fieldAnchor() : ((neontd.scene.NameScene) s).goAnchor();
                }
                return a == null ? "null" : a[0] + "," + a[1];
            }
            case "world": {
                String[] xy = arg.split(",");
                double[] a = s.worldToScreen(Double.parseDouble(xy[0]), Double.parseDouble(xy[1]));
                return a == null ? "null" : a[0] + "," + a[1];
            }
            case "go":
                switch (arg) {
                    case "menu":
                        app.goTo(new MenuScene(app));
                        break;
                    case "select":
                        app.goTo(new LevelSelectScene(app));
                        break;
                    case "chapter":
                        app.goTo(new LevelSelectScene(app, 1));
                        break;
                    case "profile":
                        app.goTo(new ProfileScene(app));
                        break;
                    case "shop":
                        app.goTo(new neontd.scene.ShopScene(app));
                        break;
                    case "missions":
                        app.goTo(new neontd.scene.MissionsScene(app));
                        break;
                    case "achievements":
                        app.goTo(new neontd.scene.AchievementsScene(app));
                        break;
                    case "settings":
                        app.goTo(new neontd.scene.SettingsScene(app));
                        break;
                    case "editor":
                        app.goTo(new EditorScene(app, null));
                        break;
                    case "game":
                        app.goTo(new GameScene(app, Levels.serpentine()));
                        break;
                    case "first":
                        app.goTo(new GameScene(app, Levels.builtins().get(0)));
                        break;
                    case "daily":
                        app.goTo(new neontd.scene.DailyScene(app));
                        break;
                    case "board":
                        app.goTo(new neontd.scene.LeaderboardScene(app));
                        break;
                    case "arena":
                        app.goTo(new neontd.scene.ArenaScene(app));
                        break;
                    case "arenagame":
                        app.goTo(GameScene.arena(app, neontd.progress.Arena.of(app.today()), null));
                        break;
                    default:
                        return "unknown scene";
                }
                return "ok";
            case "level":
                app.progress.xp = Progress.xpAtLevel(Integer.parseInt(arg)) + 10;
                return "ok";
            case "spot": {
                // nächster baubarer Platz zu "x,y" (Weltkoordinaten)
                if (!(s instanceof GameScene)) {
                    return "null";
                }
                neontd.sim.World w = ((GameScene) s).testWorld();
                String[] xy = arg.split(",");
                double tx = Double.parseDouble(xy[0]);
                double ty = Double.parseDouble(xy[1]);
                double best = Double.MAX_VALUE;
                double bx = 0;
                double by = 0;
                for (double gy = 40; gy < w.height - 30; gy += 6) {
                    for (double gx = 40; gx < w.width - 30; gx += 6) {
                        if (w.checkPlacement(gx, gy) == neontd.sim.World.PlaceCheck.OK) {
                            double d = Math.hypot(gx - tx, gy - ty);
                            if (d < best) {
                                best = d;
                                bx = gx;
                                by = gy;
                            }
                        }
                    }
                }
                return bx + "," + by;
            }
            case "tower": {
                // Position des zuletzt gebauten Turms
                if (!(s instanceof GameScene)) {
                    return "null";
                }
                java.util.List<neontd.sim.Tower> ts = ((GameScene) s).testWorld().towers;
                return ts.isEmpty() ? "none" : ts.get(ts.size() - 1).x + "," + ts.get(ts.size() - 1).y;
            }
            case "crystals":
                app.progress.addCrystals(Double.parseDouble(arg));
                app.commitProgress();
                return "ok";
            case "buy": {
                // buy TURM | buy special:KEY – wie ein Kauf im Shop (Level und Kristalle werden geprüft)
                neontd.progress.Progress.Buy r;
                if (arg.startsWith("special:")) {
                    neontd.sim.Special sp = neontd.sim.Special.byKey(arg.substring(8));
                    r = sp == null ? neontd.progress.Progress.Buy.INVALID : app.progress.buySpecial(sp);
                } else {
                    r = app.progress.buyTower(neontd.sim.TowerType.valueOf(arg.toUpperCase()));
                }
                app.commitProgress();
                return r.name();
            }
            case "money":
                if (s instanceof GameScene) {
                    ((GameScene) s).testWorld().money = Double.parseDouble(arg);
                }
                return "ok";
            case "state":
                if (s instanceof GameScene) {
                    return ((GameScene) s).debugState();
                }
                return "scene=" + (s instanceof ProfileScene ? "ProfileScene" : s instanceof LevelSelectScene
                        ? "LevelSelectScene" : s instanceof EditorScene ? "EditorScene" : s instanceof MenuScene
                        ? "MenuScene" : s instanceof neontd.scene.NameScene ? "NameScene" : s instanceof neontd.scene.ArenaScene
                        ? "ArenaScene" : s instanceof neontd.scene.IntroScene ? "IntroScene" : s.getClass().getSimpleName())
                        + " player=" + app.board.playerName() + " pending=" + app.board.arenaPendingCount();
            case "profile":
                return "level=" + app.progress.level() + " xp=" + app.progress.xp + " name=" + app.progress.name
                        + " kills=" + app.progress.kills + " waves=" + app.progress.wavesCleared + " won="
                        + app.progress.won.size() + " cloud=" + app.cloud.connected() + " sync=" + app.cloud.status
                        + " msg=" + app.cloud.message + " crystals=" + app.progress.crystals() + " towers="
                        + app.progress.ownedTowers + " day=" + app.progress.dailyDay;
            default:
                return "unknown command";
        }
    }
}

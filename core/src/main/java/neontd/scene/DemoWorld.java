package neontd.scene;

import java.util.ArrayList;
import java.util.List;
import neontd.fx.Effects;
import neontd.gfx.Gfx;
import neontd.level.LevelDef;
import neontd.level.Levels;
import neontd.physics.FixedTimestep;
import neontd.render.GameFx;
import neontd.render.WorldView;
import neontd.sim.AutoPlayer;
import neontd.sim.EnemyType;
import neontd.sim.TowerType;
import neontd.sim.WaveDef;
import neontd.sim.WaveFactory;
import neontd.sim.World;

/**
 * Eine lebendige Hintergrund-Demo für das Hauptmenü: Die echte Simulation läuft mit einem Bot als Spieler in
 * Dauerschleife – inklusive Feuerwerk. Zeigt nebenbei, dass Simulation und Darstellung sauber getrennt sind.
 */
final class DemoWorld {
    private static final TowerType[] ORDER = {
        TowerType.PULSE, TowerType.MORTAR, TowerType.FROST, TowerType.ARC, TowerType.SNIPER,
        TowerType.PULSE, TowerType.MORTAR, TowerType.ARC
    };

    private final Effects fx = new Effects();
    private final GameFx gameFx = new GameFx(fx);
    private final FixedTimestep stepper = new FixedTimestep(World.STEP, 4);
    private World world;
    private AutoPlayer bot;
    private double time;
    private double thinkTimer;
    private double restartTimer = -1;

    DemoWorld() {
        reset();
    }

    private void reset() {
        fx.clear();
        LevelDef def = Levels.serpentine().copy();
        def.startMoney = 2100;
        def.startLives = 1000;
        List<WaveDef> waves = new ArrayList<>();
        for (int i = 3; i <= 9; i++) {
            WaveDef w = WaveFactory.wave(i);
            waves.add(w);
        }
        // Zum Schluss ein dicker Titan für ein großes Feuerwerk.
        waves.add(new WaveDef().add(EnemyType.BLOCK, 16, 0.5, 0, WaveFactory.hp(EnemyType.BLOCK, 9))
                .add(EnemyType.BOSS, 1, 1, 3, 420).bonus(0));
        world = new World(def, waves, gameFx);
        bot = new AutoPlayer(world, false, ORDER.length, ORDER);
        bot.startWaves = false;
        bot.think();
        world.startNextWave();
        restartTimer = -1;
    }

    void update(double dt) {
        time += dt;
        int steps = stepper.advance(dt * 1.35);
        for (int i = 0; i < steps; i++) {
            world.step();
        }
        for (int i = 0; i < world.projectiles.size(); i++) {
            gameFx.trail(world.projectiles.get(i), world.projectiles.get(i).x, world.projectiles.get(i).y);
        }
        gameFx.update(dt);
        thinkTimer += dt;
        if (thinkTimer > 0.5) {
            thinkTimer = 0;
            bot.think();
            if (world.canStartWave() && world.activeWaves() == 0) {
                world.startNextWave();
            }
        }
        boolean over = world.state != World.State.RUNNING;
        if (over && restartTimer < 0) {
            restartTimer = 2.5;
        }
        if (restartTimer >= 0) {
            restartTimer -= dt;
            if (restartTimer < 0) {
                reset();
            }
        }
    }

    /** Zeichnet die Demo so, dass sie den Bildschirm ausfüllt (Ausschnitt, falls das Seitenverhältnis abweicht). */
    void render(Gfx g, double w, double h) {
        double s = Math.max(w / world.width, h / world.height);
        double ox = (w - world.width * s) / 2;
        double oy = (h - world.height * s) / 2;
        g.save();
        g.translate(ox, oy);
        g.scale(s, s);
        double alpha = stepper.alpha();
        WorldView.drawPaths(g, world.paths, time, 0);
        WorldView.drawTowers(g, world, time);
        WorldView.drawEnemies(g, world, alpha, time);
        WorldView.drawProjectiles(g, world, alpha);
        WorldView.drawEffects(g, fx);
        g.restore();
    }
}

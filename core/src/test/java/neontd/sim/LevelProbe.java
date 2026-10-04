package neontd.sim;

import neontd.level.LevelDef;
import neontd.level.Levels;

/** Messwerkzeug: Ein Bot spielt jedes eingebaute Level im Endlosmodus und meldet, wie weit er kommt. */
public final class LevelProbe {
    private LevelProbe() {
    }

    public static void main(String[] args) {
        int cap = args.length > 0 ? Integer.parseInt(args[0]) : 300;
        for (LevelDef def : Levels.builtins()) {
            String problem = def.validate();
            if (args.length > 1 && !def.id.matches(args[1])) {
                continue;
            }
            World w = new World(def, SimListener.NONE);
            int spots = 0;
            if (problem == null) {
                for (double y = 40; y < w.height - 30; y += 30) {
                    for (double x = 40; x < w.width - 30; x += 30) {
                        if (w.checkPlacement(x, y) == World.PlaceCheck.OK) {
                            spots++;
                        }
                    }
                }
            }
            w.enableEndless();
            AutoPlayer bot = new AutoPlayer(w, true, 40, null);
            int steps = 0;
            int w20 = -1;
            while (w.state == World.State.RUNNING && w.clearedWaves() < cap && steps < 60 * 3600 * 40) {
                if (steps % 20 == 0) {
                    bot.think();
                }
                w.step();
                steps++;
                if (w20 < 0 && w.clearedWaves() >= 20) {
                    w20 = w.lives;
                }
            }
            System.out.printf("%-14s diff=%2d hp×%.2f pfade=%d länge=%5.0f spots=%3d %s  Welle %3d geschafft, Leben bei 20: %2d, Ende: %s (Leben %d)%n",
                    def.name, def.difficulty, def.hpMul, def.paths.size(), def.totalLength(), spots, problem == null ? "ok " : problem,
                    w.clearedWaves(), w20, w.state, w.lives);
        }
    }
}

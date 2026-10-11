package neontd.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import neontd.level.LevelDef;
import neontd.level.Levels;

/**
 * Entwicklungswerkzeug: bestimmt für jedes generierte Level den größten Lebenspunkte-Faktor, den der Test-Bot mit
 * mindestens {@link #MIN_LIVES} Leben besteht, und druckt die Tabelle {@code LIMIT} für {@code Levels}.
 */
public final class Calibrate {
    static final int MIN_LIVES = 10;

    private Calibrate() {
    }

    static int lives(LevelDef def, double hpMul) {
        LevelDef d = def.copy();
        d.hpMul = hpMul;
        World w = new World(d, SimListener.NONE);
        AutoPlayer bot = new AutoPlayer(w, true, 40, null);
        int steps = 0;
        while (w.state == World.State.RUNNING && steps < 60 * 3600) {
            if (steps % 20 == 0) {
                bot.think();
            }
            w.step();
            steps++;
        }
        return w.state == World.State.WON ? w.lives : -1;
    }

    static double limit(LevelDef def) {
        double lo = 0.3;
        double hi = 4.0;
        for (int i = 0; i < 9; i++) {
            double mid = (lo + hi) / 2;
            if (lives(def, mid) >= MIN_LIVES) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("lives")) {
            // Bot-Lauf mit dem eingestellten Faktor: "lives <Regex für die Level-Kennung>"
            String re = args.length > 1 ? args[1] : ".*";
            Levels.builtins().parallelStream().filter(l -> l.id.matches(re)).forEach(
                    l -> System.out.printf(Locale.ROOT, "%-14s hp x%.2f  Leben nach Welle 20: %d%n", l.name, l.hpMul,
                            lives(l, l.hpMul)));
            return;
        }
        List<LevelDef> todo = new ArrayList<>();
        for (LevelDef l : Levels.builtins()) {
            if (l.number > 1) {
                todo.add(l);
            }
        }
        double[] res = new double[todo.size()];
        IntStream.range(0, todo.size()).parallel().forEach(i -> res[i] = limit(todo.get(i)));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < res.length; i++) {
            if (i % (Levels.PER_CHAPTER - 1) == 0) {
                sb.append("\n        ");
            }
            sb.append(String.format(Locale.ROOT, "%.2f, ", res[i]));
        }
        System.out.println(sb);
    }
}

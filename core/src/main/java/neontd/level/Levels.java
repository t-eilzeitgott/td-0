package neontd.level;

import java.util.ArrayList;
import java.util.List;
import neontd.sim.EnemyType;
import neontd.sim.WaveDef;
import neontd.sim.WaveFactory;

/** Die eingebauten Level. */
public final class Levels {
    public static final String SERPENTINE = "serpentine";

    private Levels() {
    }

    public static List<LevelDef> builtins() {
        List<LevelDef> list = new ArrayList<>();
        list.add(serpentine());
        return list;
    }

    public static LevelDef find(String id) {
        for (LevelDef l : builtins()) {
            if (l.id.equals(id)) {
                return l;
            }
        }
        return null;
    }

    /** Handgebaute Wellen eines eingebauten Levels oder {@code null}. */
    public static List<WaveDef> wavesFor(String id) {
        if (SERPENTINE.equals(id)) {
            return serpentineWaves();
        }
        return null;
    }

    /** Level 1: ein ruhig geschwungener Pfad mit vielen Plätzen zwischen den Bahnen. */
    public static LevelDef serpentine() {
        LevelDef l = new LevelDef(SERPENTINE, "Serpentine");
        l.builtin = true;
        l.startMoney = 500;
        l.startLives = 20;
        l.waveCount = 20;
        l.paths.add(new double[] {
            30, 120,
            250, 120,
            430, 170,
            620, 120,
            840, 100,
            1010, 140,
            1120, 250,
            1050, 370,
            860, 410,
            650, 375,
            450, 320,
            270, 360,
            170, 480,
            250, 590,
            450, 625,
            680, 575,
            900, 560,
            1070, 610,
            1250, 590
        });
        return l;
    }

    private static List<WaveDef> serpentineWaves() {
        List<WaveDef> waves = WaveFactory.generate(20);
        // Finale: zwei Titanen statt einem.
        WaveDef last = new WaveDef().bonus(WaveFactory.bonus(20));
        for (WaveDef.Group g : waves.get(19).groups) {
            int count = g.type == EnemyType.BOSS ? 2 : g.count;
            last.groups.add(new WaveDef.Group(g.type, count, g.interval, g.delay, g.hp, g.path));
        }
        waves.set(19, last);
        return waves;
    }
}

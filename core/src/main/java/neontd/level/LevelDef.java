package neontd.level;

import java.util.ArrayList;
import java.util.List;
import neontd.math.Mathx;
import neontd.physics.Path;
import neontd.sim.WaveDef;
import neontd.sim.WaveFactory;

/**
 * Beschreibung eines Levels: Spielfeldgröße, ein oder mehrere Pfade (als Kontrollpunkte), Startwerte und Wellen.
 * Eigene Level aus dem Editor haben generierte Wellen, eingebaute Level handgebaute (siehe {@link Levels}).
 */
public final class LevelDef {
    public static final int DEFAULT_WIDTH = 1280;
    public static final int DEFAULT_HEIGHT = 720;
    public static final int MAX_PATHS = 3;
    public static final double MIN_PATH_LENGTH = 700;
    /** Abstand der Polylinien-Stützpunkte; fein genug für glatte Kurven, grob genug für schnelle Abfragen. */
    public static final double PATH_SAMPLE_STEP = 4;

    public String id = "";
    public String name = "";
    public boolean builtin;
    public int width = DEFAULT_WIDTH;
    public int height = DEFAULT_HEIGHT;
    /** Je Pfad: x0,y0,x1,y1,... */
    public final ArrayList<double[]> paths = new ArrayList<>();
    public int startMoney = 500;
    public int startLives = 20;
    /** Anzahl Wellen bei generierten Wellen (eingebaute Level bringen ihre eigene Liste mit). */
    public int waveCount = 25;
    /** Schwierigkeitsfaktor auf alle Lebenspunkte (1 = Standardkurve; eingebaute Level werden schwerer). */
    public double hpMul = 1;
    /** Anzeige: Schwierigkeitsstufe 1–10 bei eingebauten Leveln, sonst 0. */
    public int difficulty;

    public LevelDef() {
    }

    public LevelDef(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public LevelDef copy() {
        LevelDef c = new LevelDef(id, name);
        c.builtin = builtin;
        c.width = width;
        c.height = height;
        for (double[] p : paths) {
            c.paths.add(p.clone());
        }
        c.startMoney = startMoney;
        c.startLives = startLives;
        c.waveCount = waveCount;
        c.hpMul = hpMul;
        c.difficulty = difficulty;
        return c;
    }

    public Path[] buildPaths() {
        Path[] result = new Path[paths.size()];
        for (int i = 0; i < result.length; i++) {
            double[] pts = paths.get(i);
            result[i] = Path.fromControlPoints(pts, pts.length / 2, PATH_SAMPLE_STEP);
        }
        return result;
    }

    public List<WaveDef> buildWaves() {
        if (builtin) {
            return Levels.campaignWaves(this);
        }
        return WaveFactory.generate(waveCount, hpMul);
    }

    /** Prüft, ob das Level spielbar ist. Liefert {@code null} wenn ja, sonst eine verständliche Fehlermeldung. */
    public String validate() {
        if (paths.isEmpty()) {
            return "Zeichne zuerst einen Pfad.";
        }
        if (paths.size() > MAX_PATHS) {
            return "Höchstens " + MAX_PATHS + " Pfade.";
        }
        for (int i = 0; i < paths.size(); i++) {
            double[] pts = paths.get(i);
            int n = pts.length / 2;
            String label = paths.size() > 1 ? "Pfad " + (i + 1) + ": " : "";
            if (Path.distinctCount(pts, n) < 2) {
                return label + "Mindestens zwei Punkte setzen.";
            }
            for (int k = 0; k < n; k++) {
                if (pts[k * 2] < 0 || pts[k * 2] > width || pts[k * 2 + 1] < 0 || pts[k * 2 + 1] > height) {
                    return label + "Punkte müssen im Spielfeld liegen.";
                }
            }
            Path p = Path.fromControlPoints(pts, n, PATH_SAMPLE_STEP);
            if (p.length() < MIN_PATH_LENGTH) {
                return label + "Der Pfad ist zu kurz (mindestens " + (int) MIN_PATH_LENGTH + ").";
            }
        }
        return null;
    }

    /** Gesamtlänge aller Pfade (für Anzeigen), 0 wenn ungültig. */
    public double totalLength() {
        double sum = 0;
        for (double[] pts : paths) {
            int n = pts.length / 2;
            if (Path.distinctCount(pts, n) >= 2) {
                sum += Path.fromControlPoints(pts, n, PATH_SAMPLE_STEP).length();
            }
        }
        return sum;
    }

    /** Begrenzt alle Punkte auf das Spielfeld. */
    public void clampPoints() {
        for (double[] pts : paths) {
            for (int i = 0; i < pts.length; i += 2) {
                pts[i] = Mathx.clamp(pts[i], 0, width);
                pts[i + 1] = Mathx.clamp(pts[i + 1], 0, height);
            }
        }
    }
}

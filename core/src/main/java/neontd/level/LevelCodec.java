package neontd.level;

import neontd.math.Mathx;

/**
 * Lesbares Textformat für Level (eine Eigenschaft pro Zeile), damit gespeicherte Level robust, versionierbar
 * und von Hand prüfbar sind:
 *
 * <pre>
 * NTD1
 * name=Eigenes Level 1
 * size=1280,720
 * money=500
 * lives=20
 * waves=25
 * path=30,120;250,120;430,170
 * </pre>
 */
public final class LevelCodec {
    private static final String MAGIC = "NTD1";

    private LevelCodec() {
    }

    public static String encode(LevelDef l) {
        StringBuilder sb = new StringBuilder();
        sb.append(MAGIC).append('\n');
        sb.append("name=").append(l.name.replace('\n', ' ').replace('\r', ' ')).append('\n');
        sb.append("size=").append(l.width).append(',').append(l.height).append('\n');
        sb.append("money=").append(l.startMoney).append('\n');
        sb.append("lives=").append(l.startLives).append('\n');
        sb.append("waves=").append(l.waveCount).append('\n');
        for (double[] pts : l.paths) {
            sb.append("path=");
            for (int i = 0; i < pts.length; i += 2) {
                if (i > 0) {
                    sb.append(';');
                }
                sb.append(num(pts[i])).append(',').append(num(pts[i + 1]));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** @return das Level oder {@code null}, wenn der Text nicht lesbar ist */
    public static LevelDef decode(String id, String text) {
        if (text == null) {
            return null;
        }
        try {
            String[] lines = text.split("\n");
            if (lines.length == 0 || !MAGIC.equals(lines[0].trim())) {
                return null;
            }
            LevelDef l = new LevelDef(id, "Level");
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                int eq = line.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();
                switch (key) {
                    case "name":
                        l.name = val;
                        break;
                    case "size": {
                        String[] wh = val.split(",");
                        l.width = Mathx.clamp(Integer.parseInt(wh[0].trim()), 320, 4000);
                        l.height = Mathx.clamp(Integer.parseInt(wh[1].trim()), 240, 4000);
                        break;
                    }
                    case "money":
                        l.startMoney = Mathx.clamp(Integer.parseInt(val), 0, 1000000);
                        break;
                    case "lives":
                        l.startLives = Mathx.clamp(Integer.parseInt(val), 1, 1000);
                        break;
                    case "waves":
                        l.waveCount = Mathx.clamp(Integer.parseInt(val), 1, 200);
                        break;
                    case "path": {
                        String[] pairs = val.split(";");
                        double[] pts = new double[pairs.length * 2];
                        for (int k = 0; k < pairs.length; k++) {
                            String[] xy = pairs[k].split(",");
                            pts[k * 2] = Double.parseDouble(xy[0].trim());
                            pts[k * 2 + 1] = Double.parseDouble(xy[1].trim());
                        }
                        if (l.paths.size() < LevelDef.MAX_PATHS) {
                            l.paths.add(pts);
                        }
                        break;
                    }
                    default:
                        break; // Unbekanntes ignorieren – neuere Versionen dürfen mehr speichern.
                }
            }
            return l;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Zahl ohne wissenschaftliche Schreibweise, höchstens eine Nachkommastelle. */
    private static String num(double v) {
        double r = Mathx.round1(v);
        if (r == Math.rint(r)) {
            return Integer.toString((int) r);
        }
        return Double.toString(r);
    }
}

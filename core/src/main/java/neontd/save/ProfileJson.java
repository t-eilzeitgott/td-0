package neontd.save;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neontd.progress.Progress;

/** Wandelt ein {@link Progress} in JSON-Werte und zurück (lokaler Speicher und Cloud nutzen dasselbe Format). */
public final class ProfileJson {
    private ProfileJson() {
    }

    public static Map<String, Object> toMap(Progress p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.name == null ? "" : p.name);
        m.put("xp", p.xp);
        m.put("kills", p.kills);
        m.put("waves", p.wavesCleared);
        m.put("played", p.gamesPlayed);
        m.put("wins", p.gamesWon);
        m.put("won", new java.util.ArrayList<Object>(p.won));
        m.put("best", new LinkedHashMap<String, Object>(p.bestWave));
        m.put("endless", new LinkedHashMap<String, Object>(p.bestEndless));
        m.put("t", p.updated);
        return m;
    }

    /** Liest ein Profil; fehlende oder unsinnige Felder werden zu sicheren Standardwerten. */
    public static Progress fromMap(Map<String, Object> m) {
        Progress p = new Progress();
        if (m == null) {
            return p;
        }
        p.name = Progress.cleanName(Json.str(m, "name", ""));
        p.xp = clampInt(Json.integer(m, "xp", 0), Progress.MAX_XP);
        p.kills = clampInt(Json.integer(m, "kills", 0), Progress.MAX_XP);
        p.wavesCleared = clampInt(Json.integer(m, "waves", 0), Progress.MAX_XP);
        p.gamesPlayed = clampInt(Json.integer(m, "played", 0), Progress.MAX_XP);
        p.gamesWon = clampInt(Json.integer(m, "wins", 0), Progress.MAX_XP);
        List<Object> won = Json.list(m, "won");
        if (won != null) {
            for (Object o : won) {
                if (o instanceof String && !((String) o).isEmpty()) {
                    p.won.add((String) o);
                }
            }
        }
        readCounts(Json.map(m, "best"), p.bestWave);
        readCounts(Json.map(m, "endless"), p.bestEndless);
        p.updated = Math.max(0, Json.num(m, "t", 0));
        return p;
    }

    private static void readCounts(Map<String, Object> src, Map<String, Integer> dst) {
        if (src == null) {
            return;
        }
        for (Map.Entry<String, Object> e : src.entrySet()) {
            if (e.getValue() instanceof Number) {
                dst.put(e.getKey(), clampInt((int) Math.min(Integer.MAX_VALUE, ((Number) e.getValue()).doubleValue()),
                        Progress.MAX_XP));
            }
        }
    }

    private static int clampInt(int v, int max) {
        return v < 0 ? 0 : Math.min(v, max);
    }

    /**
     * Führt zwei Profile zusammen, ohne Fortschritt zu verlieren: Zähler und Rekorde nehmen jeweils den größeren
     * Wert, gewonnene Level werden vereinigt, der Name stammt vom jüngeren Stand.
     */
    public static Progress merge(Progress a, Progress b) {
        Progress r = new Progress();
        Progress newer = a.updated >= b.updated ? a : b;
        r.name = !newer.name.isEmpty() ? newer.name : (a.name.isEmpty() ? b.name : a.name);
        r.xp = Math.max(a.xp, b.xp);
        r.kills = Math.max(a.kills, b.kills);
        r.wavesCleared = Math.max(a.wavesCleared, b.wavesCleared);
        r.gamesPlayed = Math.max(a.gamesPlayed, b.gamesPlayed);
        r.gamesWon = Math.max(a.gamesWon, b.gamesWon);
        r.won.addAll(a.won);
        r.won.addAll(b.won);
        maxInto(r.bestWave, a.bestWave);
        maxInto(r.bestWave, b.bestWave);
        maxInto(r.bestEndless, a.bestEndless);
        maxInto(r.bestEndless, b.bestEndless);
        r.updated = Math.max(a.updated, b.updated);
        return r;
    }

    private static void maxInto(Map<String, Integer> dst, Map<String, Integer> src) {
        for (Map.Entry<String, Integer> e : src.entrySet()) {
            Integer old = dst.get(e.getKey());
            if (old == null || e.getValue() > old) {
                dst.put(e.getKey(), e.getValue());
            }
        }
    }

    public static boolean sameAs(Progress a, Progress b) {
        return Json.write(toMap(a)).equals(Json.write(toMap(b)));
    }
}

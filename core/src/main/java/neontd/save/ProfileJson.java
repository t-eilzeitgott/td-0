package neontd.save;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neontd.math.Mathx;
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
        m.put("stars", new LinkedHashMap<String, Object>(p.stars));
        m.put("sday", p.streakDay);
        m.put("streak", p.streak);
        m.put("t", p.updated);
        // Shop, Errungenschaften, Aufgaben, Season
        m.put("cr", p.crystalsEarned);
        m.put("ot", new java.util.ArrayList<Object>(p.ownedTowers));
        m.put("os", new java.util.ArrayList<Object>(p.ownedSpecials));
        m.put("pk", new LinkedHashMap<String, Object>(p.perkLevels));
        m.put("ach", new java.util.ArrayList<Object>(p.achievements));
        m.put("stat", new LinkedHashMap<String, Object>(p.stats));
        m.put("day", p.dailyDay);
        m.put("dp", ints(p.dailyProgress));
        m.put("dc", bits(p.dailyClaimed));
        m.put("db", p.dailyBonusClaimed);
        m.put("ssn", p.seasonId);
        m.put("sxp", p.seasonXp);
        m.put("sc", bits(p.seasonClaimed));
        m.put("mp", ints(p.monthlyProgress));
        m.put("mc", bits(p.monthlyClaimed));
        m.put("epoch", p.resetEpoch);
        return m;
    }

    private static List<Object> ints(int[] a) {
        List<Object> l = new java.util.ArrayList<>();
        for (int v : a) {
            l.add(v);
        }
        return l;
    }

    private static String bits(boolean[] a) {
        StringBuilder sb = new StringBuilder();
        for (boolean b : a) {
            sb.append(b ? '1' : '0');
        }
        return sb.toString();
    }

    private static void readInts(List<Object> src, int[] dst, int max) {
        if (src == null) {
            return;
        }
        for (int i = 0; i < dst.length && i < src.size(); i++) {
            Object o = src.get(i);
            if (o instanceof Number) {
                dst[i] = clampInt((int) Math.min(Integer.MAX_VALUE, ((Number) o).doubleValue()), max);
            }
        }
    }

    private static void readBits(String src, boolean[] dst) {
        if (src == null) {
            return;
        }
        for (int i = 0; i < dst.length && i < src.length(); i++) {
            dst[i] = src.charAt(i) == '1';
        }
    }

    private static void readStrings(List<Object> src, java.util.Set<String> dst) {
        if (src == null) {
            return;
        }
        for (Object o : src) {
            if (o instanceof String && !((String) o).isEmpty()) {
                dst.add((String) o);
            }
        }
    }

    /** Liest ein Profil; fehlende oder unsinnige Felder werden zu sicheren Standardwerten. */
    public static Progress fromMap(Map<String, Object> m) {
        Progress p = new Progress();
        if (m == null) {
            return p;
        }
        p.name = Progress.cleanName(Json.str(m, "name", ""));
        p.xp = Mathx.clamp(Json.num(m, "xp", 0), 0, Progress.MAX_XP);
        p.kills = clampInt(Json.integer(m, "kills", 0), Progress.MAX_COUNT);
        p.wavesCleared = clampInt(Json.integer(m, "waves", 0), Progress.MAX_COUNT);
        p.gamesPlayed = clampInt(Json.integer(m, "played", 0), Progress.MAX_COUNT);
        p.gamesWon = clampInt(Json.integer(m, "wins", 0), Progress.MAX_COUNT);
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
        readCounts(Json.map(m, "stars"), p.stars);
        p.streakDay = (int) Mathx.clamp(Json.num(m, "sday", -1), -1, 1_000_000);
        p.streak = clampInt(Json.integer(m, "streak", 0), 100_000);
        p.updated = Math.max(0, Json.num(m, "t", 0));
        p.crystalsEarned = Mathx.clamp(Json.num(m, "cr", 0), 0, Mathx.MAX_SAFE);
        readStrings(Json.list(m, "ot"), p.ownedTowers);
        readStrings(Json.list(m, "os"), p.ownedSpecials);
        readCounts(Json.map(m, "pk"), p.perkLevels);
        readStrings(Json.list(m, "ach"), p.achievements);
        Map<String, Object> st = Json.map(m, "stat");
        if (st != null) {
            for (Map.Entry<String, Object> e : st.entrySet()) {
                if (e.getValue() instanceof Number) {
                    p.stats.put(e.getKey(), Mathx.clamp(((Number) e.getValue()).doubleValue(), 0, Mathx.MAX_SAFE));
                }
            }
        }
        p.dailyDay = (int) Mathx.clamp(Json.num(m, "day", -1), -1, 1_000_000);
        readInts(Json.list(m, "dp"), p.dailyProgress, Progress.MAX_COUNT);
        readBits(Json.str(m, "dc", null), p.dailyClaimed);
        p.dailyBonusClaimed = Json.bool(m, "db", false);
        p.seasonId = (int) Mathx.clamp(Json.num(m, "ssn", -1), -1, 1_000_000);
        p.seasonXp = clampInt(Json.integer(m, "sxp", 0), neontd.progress.Season.MAX_XP);
        readBits(Json.str(m, "sc", null), p.seasonClaimed);
        readInts(Json.list(m, "mp"), p.monthlyProgress, Progress.MAX_COUNT);
        readBits(Json.str(m, "mc", null), p.monthlyClaimed);
        p.resetEpoch = Math.max(0, Json.num(m, "epoch", 0));
        return p;
    }

    private static void readCounts(Map<String, Object> src, Map<String, Integer> dst) {
        if (src == null) {
            return;
        }
        for (Map.Entry<String, Object> e : src.entrySet()) {
            if (e.getValue() instanceof Number) {
                dst.put(e.getKey(), clampInt((int) Math.min(Integer.MAX_VALUE, ((Number) e.getValue()).doubleValue()),
                        Progress.MAX_COUNT));
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
        if (a.resetEpoch != b.resetEpoch) {
            // Ein Zurücksetzen gewinnt: Der Stand mit dem jüngeren Zurücksetzen gilt komplett, der andere wird verworfen.
            Progress w = a.resetEpoch > b.resetEpoch ? a : b;
            Progress r = new Progress();
            r.copyFrom(w);
            r.updated = Math.max(a.updated, b.updated);
            return r;
        }
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
        maxInto(r.stars, a.stars);
        maxInto(r.stars, b.stars);
        Progress sk = a.streakDay > b.streakDay ? a : (b.streakDay > a.streakDay ? b : (a.streak >= b.streak ? a : b));
        r.streakDay = sk.streakDay;
        r.streak = sk.streak;
        r.updated = Math.max(a.updated, b.updated);
        r.resetEpoch = a.resetEpoch;
        r.ownedTowers.addAll(a.ownedTowers);
        r.ownedTowers.addAll(b.ownedTowers);
        r.ownedSpecials.addAll(a.ownedSpecials);
        r.ownedSpecials.addAll(b.ownedSpecials);
        maxInto(r.perkLevels, a.perkLevels);
        maxInto(r.perkLevels, b.perkLevels);
        r.achievements.addAll(a.achievements);
        r.achievements.addAll(b.achievements);
        for (Progress src : new Progress[] {a, b}) {
            for (Map.Entry<String, Double> e : src.stats.entrySet()) {
                Double old = r.stats.get(e.getKey());
                if (old == null || e.getValue() > old) {
                    r.stats.put(e.getKey(), e.getValue());
                }
            }
        }
        // Tagesaufgaben: der jüngere Tag gewinnt; am selben Tag Fortschritt maximal, Abholmarken vereinigt.
        Progress d = a.dailyDay >= b.dailyDay ? a : b;
        r.dailyDay = d.dailyDay;
        for (int i = 0; i < r.dailyProgress.length; i++) {
            r.dailyProgress[i] = a.dailyDay == b.dailyDay ? Math.max(a.dailyProgress[i], b.dailyProgress[i])
                    : d.dailyProgress[i];
            r.dailyClaimed[i] = a.dailyDay == b.dailyDay ? a.dailyClaimed[i] || b.dailyClaimed[i] : d.dailyClaimed[i];
        }
        r.dailyBonusClaimed = a.dailyDay == b.dailyDay ? a.dailyBonusClaimed || b.dailyBonusClaimed
                : d.dailyBonusClaimed;
        // Season: dasselbe Prinzip nach Monat.
        Progress se = a.seasonId >= b.seasonId ? a : b;
        boolean same = a.seasonId == b.seasonId;
        r.seasonId = se.seasonId;
        r.seasonXp = same ? Math.max(a.seasonXp, b.seasonXp) : se.seasonXp;
        for (int i = 0; i < r.seasonClaimed.length; i++) {
            r.seasonClaimed[i] = same ? a.seasonClaimed[i] || b.seasonClaimed[i] : se.seasonClaimed[i];
        }
        for (int i = 0; i < r.monthlyProgress.length; i++) {
            r.monthlyProgress[i] = same ? Math.max(a.monthlyProgress[i], b.monthlyProgress[i]) : se.monthlyProgress[i];
            r.monthlyClaimed[i] = same ? a.monthlyClaimed[i] || b.monthlyClaimed[i] : se.monthlyClaimed[i];
        }
        // Das Guthaben darf durch das Zusammenführen nie negativ werden.
        r.crystalsEarned = Math.max(Math.max(a.crystalsEarned, b.crystalsEarned), r.spentCrystals());
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

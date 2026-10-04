package neontd.save;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import neontd.level.LevelStore;
import neontd.progress.Progress;

/**
 * Alles, was synchronisiert wird: Profil, eigene Level (samt Löschmarkern) und gespeicherte Läufe. Das Bündel ist
 * zugleich das Dateiformat in der Cloud (ein Gist) und beim Export-Code. {@link #merge} vereinigt zwei Stände, ohne
 * Fortschritt zu verlieren.
 */
public final class SaveBundle {
    public static final String APP = "neon-td";
    public static final int VERSION = 1;
    private static final String CODE_PREFIX = "NTD1:";

    public Progress profile = new Progress();
    public final Map<String, LevelStore.Entry> levels = new TreeMap<>();
    public final Map<String, RunSave> runs = new TreeMap<>();

    // ---------------------------------------------------------------------------------- Lokal ↔ Bündel

    public static SaveBundle collect(SaveStore saves, LevelStore levelStore) {
        SaveBundle b = new SaveBundle();
        b.profile = saves.loadProgress();
        b.levels.putAll(levelStore.entries());
        for (String id : saves.runLevelIds()) {
            RunSave r = saves.loadRun(id);
            if (r != null) {
                b.runs.put(id, r);
            }
        }
        return b;
    }

    /** Schreibt das Bündel in den lokalen Speicher (nur, was sich unterscheidet). */
    public void applyTo(SaveStore saves, LevelStore levelStore) {
        saves.putProgress(profile);
        Map<String, LevelStore.Entry> local = levelStore.entries();
        for (LevelStore.Entry e : levels.values()) {
            LevelStore.Entry l = local.get(e.id);
            boolean same = l != null && l.t == e.t && (l.code == null ? e.code == null : l.code.equals(e.code));
            if (!same) {
                levelStore.apply(e);
            }
        }
        for (RunSave r : runs.values()) {
            RunSave l = saves.loadRun(r.levelId);
            if (l == null || l.t != r.t || l.over != r.over) {
                saves.putRun(r);
            }
        }
    }

    // ------------------------------------------------------------------------------------ Zusammenführen

    public static SaveBundle merge(SaveBundle a, SaveBundle b) {
        SaveBundle r = new SaveBundle();
        r.profile = ProfileJson.merge(a.profile, b.profile);
        for (LevelStore.Entry e : a.levels.values()) {
            r.levels.put(e.id, e);
        }
        for (LevelStore.Entry e : b.levels.values()) {
            LevelStore.Entry cur = r.levels.get(e.id);
            if (cur == null || e.t > cur.t) {
                r.levels.put(e.id, e);
            }
        }
        for (RunSave x : a.runs.values()) {
            r.runs.put(x.levelId, x);
        }
        for (RunSave x : b.runs.values()) {
            RunSave cur = r.runs.get(x.levelId);
            if (cur == null || x.t > cur.t) {
                r.runs.put(x.levelId, x);
            }
        }
        return r;
    }

    // -------------------------------------------------------------------------------------------- JSON

    public Map<String, Object> toMap(boolean includeRuns) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("app", APP);
        m.put("v", VERSION);
        m.put("profile", ProfileJson.toMap(profile));
        Map<String, Object> lv = new LinkedHashMap<>();
        for (LevelStore.Entry e : levels.values()) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("t", e.t);
            if (e.code == null) {
                o.put("del", Boolean.TRUE);
            } else {
                o.put("code", e.code);
            }
            lv.put(e.id, o);
        }
        m.put("levels", lv);
        Map<String, Object> rn = new LinkedHashMap<>();
        if (includeRuns) {
            for (RunSave r : runs.values()) {
                rn.put(r.levelId, r.toMap());
            }
        }
        m.put("runs", rn);
        return m;
    }

    public String encode() {
        return Json.write(toMap(true));
    }

    /** @return das Bündel oder {@code null}, wenn der Text kein Neon-TD-Speicherstand ist */
    public static SaveBundle decode(String text) {
        Map<String, Object> m = Json.asMap(Json.tryParse(text));
        return m == null ? null : fromMap(m);
    }

    public static SaveBundle fromMap(Map<String, Object> m) {
        if (!APP.equals(Json.str(m, "app", ""))) {
            return null;
        }
        SaveBundle b = new SaveBundle();
        b.profile = ProfileJson.fromMap(Json.map(m, "profile"));
        Map<String, Object> lv = Json.map(m, "levels");
        if (lv != null) {
            for (Map.Entry<String, Object> e : lv.entrySet()) {
                Map<String, Object> o = Json.asMap(e.getValue());
                if (o == null || e.getKey().isEmpty()) {
                    continue;
                }
                double t = Math.max(0, Json.num(o, "t", 0));
                String code = Json.bool(o, "del", false) ? null : Json.str(o, "code", null);
                if (code == null && !Json.bool(o, "del", false)) {
                    continue;
                }
                b.levels.put(e.getKey(), new LevelStore.Entry(e.getKey(), code, t));
            }
        }
        Map<String, Object> rn = Json.map(m, "runs");
        if (rn != null) {
            for (Map.Entry<String, Object> e : rn.entrySet()) {
                RunSave r = RunSave.fromMap(e.getKey(), Json.asMap(e.getValue()));
                if (r != null) {
                    b.runs.put(e.getKey(), r);
                }
            }
        }
        return b;
    }

    // ------------------------------------------------------------------------------------ Export-Code

    /** Kurzer Text zum Kopieren (Profil und Level, ohne laufende Spiele): "NTD1:…". */
    public String toCode() {
        return CODE_PREFIX + Base64Lite.encode(Json.write(toMap(false)));
    }

    /** @return das Bündel oder {@code null} bei einem ungültigen Code */
    public static SaveBundle fromCode(String code) {
        if (code == null) {
            return null;
        }
        String c = code.trim();
        if (!c.startsWith(CODE_PREFIX)) {
            return null;
        }
        String json = Base64Lite.decode(c.substring(CODE_PREFIX.length()));
        return json == null ? null : decode(json);
    }

    /** Gleicher Inhalt (ohne Rücksicht auf die Reihenfolge der Einträge)? */
    public boolean sameContent(SaveBundle o) {
        return Json.write(toMap(true)).equals(Json.write(o.toMap(true)));
    }
}

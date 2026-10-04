package neontd.save;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neontd.sim.WorldSnapshot;

/**
 * Ein gespeicherter Spiellauf ("Fortsetzen"): Zeitstempel plus {@link WorldSnapshot}. Ein beendeter Lauf bleibt als
 * kleiner Marker ({@code over}) stehen – so merkt die Cloud-Synchronisierung, dass er nicht wiederbelebt werden darf.
 */
public final class RunSave {
    public String levelId = "";
    /** Zeitpunkt (ms seit 1970). */
    public double t;
    /** Lauf ist beendet; {@link #snapshot} ist dann {@code null}. */
    public boolean over;
    public WorldSnapshot snapshot;

    public static RunSave of(String levelId, double t, WorldSnapshot s) {
        RunSave r = new RunSave();
        r.levelId = levelId;
        r.t = t;
        r.snapshot = s;
        return r;
    }

    public static RunSave ended(String levelId, double t) {
        RunSave r = new RunSave();
        r.levelId = levelId;
        r.t = t;
        r.over = true;
        return r;
    }

    /** Hat der Lauf etwas, das sich fortsetzen lässt? */
    public boolean resumable() {
        return !over && snapshot != null;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", t);
        if (over || snapshot == null) {
            m.put("over", Boolean.TRUE);
            return m;
        }
        WorldSnapshot s = snapshot;
        m.put("endless", s.endless);
        m.put("auto", s.autoStart);
        m.put("money", s.money);
        m.put("lives", s.lives);
        m.put("kills", s.kills);
        m.put("wave", s.waveIndex);
        m.put("cleared", s.clearedWaves);
        m.put("time", s.time);
        List<Object> towers = new ArrayList<>();
        for (WorldSnapshot.TowerData d : s.towers) {
            List<Object> a = new ArrayList<>();
            a.add(d.type);
            a.add(d.x);
            a.add(d.y);
            a.add(d.level[0]);
            a.add(d.level[1]);
            a.add(d.level[2]);
            a.add(d.invested);
            a.add(d.mode);
            towers.add(a);
        }
        m.put("towers", towers);
        List<Object> enemies = new ArrayList<>();
        for (WorldSnapshot.EnemyData d : s.enemies) {
            List<Object> a = new ArrayList<>();
            a.add(d.type);
            a.add(d.path);
            a.add(d.wave);
            a.add(d.hp);
            a.add(d.dist);
            a.add(d.reward);
            a.add(d.slowFactor);
            a.add(d.slowTimer);
            enemies.add(a);
        }
        m.put("enemies", enemies);
        return m;
    }

    /** Liest einen Lauf; {@code null}, wenn die Daten unbrauchbar sind. */
    public static RunSave fromMap(String levelId, Map<String, Object> m) {
        if (m == null) {
            return null;
        }
        RunSave r = new RunSave();
        r.levelId = levelId;
        r.t = Math.max(0, Json.num(m, "t", 0));
        if (Json.bool(m, "over", false)) {
            r.over = true;
            return r;
        }
        List<Object> towers = Json.list(m, "towers");
        List<Object> enemies = Json.list(m, "enemies");
        if (towers == null || enemies == null) {
            return null;
        }
        WorldSnapshot s = new WorldSnapshot();
        s.endless = Json.bool(m, "endless", false);
        s.autoStart = Json.bool(m, "auto", false);
        s.money = Json.num(m, "money", 0);
        s.lives = Json.integer(m, "lives", 1);
        s.kills = Json.integer(m, "kills", 0);
        s.waveIndex = Json.integer(m, "wave", 0);
        s.clearedWaves = Json.integer(m, "cleared", 0);
        s.time = Json.num(m, "time", 0);
        for (Object o : towers) {
            List<Object> a = Json.asList(o);
            if (a == null || a.size() < 8) {
                continue;
            }
            WorldSnapshot.TowerData d = new WorldSnapshot.TowerData();
            d.type = (int) Json.at(a, 0);
            d.x = Json.at(a, 1);
            d.y = Json.at(a, 2);
            d.level[0] = (int) Json.at(a, 3);
            d.level[1] = (int) Json.at(a, 4);
            d.level[2] = (int) Json.at(a, 5);
            d.invested = Json.at(a, 6);
            d.mode = (int) Json.at(a, 7);
            s.towers.add(d);
        }
        for (Object o : enemies) {
            List<Object> a = Json.asList(o);
            if (a == null || a.size() < 8) {
                continue;
            }
            WorldSnapshot.EnemyData d = new WorldSnapshot.EnemyData();
            d.type = (int) Json.at(a, 0);
            d.path = (int) Json.at(a, 1);
            d.wave = (int) Json.at(a, 2);
            d.hp = (int) Math.min(Integer.MAX_VALUE, Json.at(a, 3));
            d.dist = Json.at(a, 4);
            d.reward = (int) Math.min(Integer.MAX_VALUE, Json.at(a, 5));
            d.slowFactor = Json.at(a, 6);
            d.slowTimer = Json.at(a, 7);
            s.enemies.add(d);
        }
        r.snapshot = s;
        return r;
    }

    public String encode() {
        return Json.write(toMap());
    }

    public static RunSave decode(String levelId, String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        return fromMap(levelId, Json.asMap(Json.tryParse(text)));
    }
}

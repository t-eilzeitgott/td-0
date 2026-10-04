package neontd.level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;
import neontd.platform.KeyValueStore;

/** Verwaltet die selbst erstellten Level im dauerhaften Speicher. */
public final class LevelStore {
    private static final String INDEX_KEY = "neontd.levels";
    private static final String SEQ_KEY = "neontd.levelSeq";
    private static final String LEVEL_PREFIX = "neontd.level.";
    /** Zeitstempel je Level (ms seit 1970) und Löschmarker – Grundlage der Cloud-Zusammenführung. */
    private static final String TIME_PREFIX = "neontd.levelT.";
    private static final String GONE_PREFIX = "neontd.levelDel.";
    private static final String TOMB_INDEX = "neontd.levelGone";

    /** Ein Level samt Zeitstempel für den Abgleich; {@code code == null} heißt "gelöscht". */
    public static final class Entry {
        public final String id;
        public final String code;
        public final double t;

        public Entry(String id, String code, double t) {
            this.id = id;
            this.code = code;
            this.t = t;
        }
    }

    private final KeyValueStore kv;
    private final DoubleSupplier clock;

    public LevelStore(KeyValueStore kv) {
        this(kv, () -> 0);
    }

    public LevelStore(KeyValueStore kv, DoubleSupplier clock) {
        this.kv = kv;
        this.clock = clock;
    }

    private List<String> ids() {
        List<String> list = new ArrayList<>();
        String raw = kv.get(INDEX_KEY);
        if (raw != null && !raw.isEmpty()) {
            for (String id : raw.split(",")) {
                if (!id.isEmpty() && !list.contains(id)) {
                    list.add(id);
                }
            }
        }
        return list;
    }

    private void writeIds(List<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ids.get(i));
        }
        kv.put(INDEX_KEY, sb.toString());
    }

    /** Alle lesbaren eigenen Level in Erstellungsreihenfolge. */
    public List<LevelDef> loadAll() {
        List<LevelDef> result = new ArrayList<>();
        for (String id : ids()) {
            LevelDef l = LevelCodec.decode(id, kv.get(LEVEL_PREFIX + id));
            if (l != null) {
                result.add(l);
            }
        }
        return result;
    }

    public LevelDef load(String id) {
        return LevelCodec.decode(id, kv.get(LEVEL_PREFIX + id));
    }

    /** Ein neues, leeres Level mit eindeutiger Kennung und fortlaufendem Namen (noch nicht gespeichert). */
    public LevelDef createNew() {
        int seq = 1;
        String raw = kv.get(SEQ_KEY);
        if (raw != null) {
            try {
                seq = Integer.parseInt(raw.trim()) + 1;
            } catch (NumberFormatException e) {
                seq = ids().size() + 1;
            }
        }
        kv.put(SEQ_KEY, Integer.toString(seq));
        // Mit Zufallsendung, damit zwei Geräte nie dieselbe Kennung vergeben (wichtig für die Cloud-Zusammenführung).
        int salt = (int) (Math.abs(clock.getAsDouble()) % 1_679_616) ^ (int) (Math.random() * 46656);
        return new LevelDef("c" + seq + Integer.toString(salt % 46656 + 46656, 36), "Eigenes Level " + seq);
    }

    public void save(LevelDef l) {
        if (l.id == null || l.id.isEmpty() || l.builtin) {
            throw new IllegalArgumentException("Nur eigene Level mit Kennung können gespeichert werden");
        }
        kv.put(LEVEL_PREFIX + l.id, LevelCodec.encode(l));
        kv.put(TIME_PREFIX + l.id, Double.toString(clock.getAsDouble()));
        kv.remove(GONE_PREFIX + l.id);
        List<String> ids = ids();
        if (!ids.contains(l.id)) {
            ids.add(l.id);
            writeIds(ids);
        }
    }

    public void delete(String id) {
        kv.remove(LEVEL_PREFIX + id);
        kv.remove(TIME_PREFIX + id);
        kv.put(GONE_PREFIX + id, Double.toString(clock.getAsDouble()));
        List<String> gone = split(kv.get(TOMB_INDEX));
        if (!gone.contains(id)) {
            gone.add(id);
            kv.put(TOMB_INDEX, String.join(",", gone));
        }
        List<String> ids = ids();
        if (ids.remove(id)) {
            writeIds(ids);
        }
    }

    private static List<String> split(String raw) {
        List<String> list = new ArrayList<>();
        if (raw != null && !raw.isEmpty()) {
            for (String id : raw.split(",")) {
                if (!id.isEmpty() && !list.contains(id)) {
                    list.add(id);
                }
            }
        }
        return list;
    }

    private double timeOf(String key) {
        String raw = kv.get(key);
        if (raw == null) {
            return 0;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Alle Level (mit Code) und alle gelöschten (ohne Code) samt Zeitstempel – für den Cloud-Abgleich. */
    public Map<String, Entry> entries() {
        Map<String, Entry> map = new LinkedHashMap<>();
        for (String id : ids()) {
            String code = kv.get(LEVEL_PREFIX + id);
            if (code != null && LevelCodec.decode(id, code) != null) {
                map.put(id, new Entry(id, code, timeOf(TIME_PREFIX + id)));
            }
        }
        for (String id : split(kv.get(TOMB_INDEX))) {
            if (!map.containsKey(id)) {
                map.put(id, new Entry(id, null, timeOf(GONE_PREFIX + id)));
            }
        }
        return map;
    }

    /** Übernimmt einen Eintrag aus der Cloud (Level speichern oder löschen), ohne die Zeit neu zu stempeln. */
    public void apply(Entry e) {
        if (e.code == null) {
            kv.remove(LEVEL_PREFIX + e.id);
            kv.remove(TIME_PREFIX + e.id);
            kv.put(GONE_PREFIX + e.id, Double.toString(e.t));
            List<String> gone = split(kv.get(TOMB_INDEX));
            if (!gone.contains(e.id)) {
                gone.add(e.id);
                kv.put(TOMB_INDEX, String.join(",", gone));
            }
            List<String> ids = ids();
            if (ids.remove(e.id)) {
                writeIds(ids);
            }
            return;
        }
        if (LevelCodec.decode(e.id, e.code) == null) {
            return;
        }
        kv.put(LEVEL_PREFIX + e.id, e.code);
        kv.put(TIME_PREFIX + e.id, Double.toString(e.t));
        kv.remove(GONE_PREFIX + e.id);
        List<String> ids = ids();
        if (!ids.contains(e.id)) {
            ids.add(e.id);
            writeIds(ids);
        }
    }
}

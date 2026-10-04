package neontd.level;

import java.util.ArrayList;
import java.util.List;
import neontd.platform.KeyValueStore;

/** Verwaltet die selbst erstellten Level im dauerhaften Speicher. */
public final class LevelStore {
    private static final String INDEX_KEY = "neontd.levels";
    private static final String SEQ_KEY = "neontd.levelSeq";
    private static final String LEVEL_PREFIX = "neontd.level.";

    private final KeyValueStore kv;

    public LevelStore(KeyValueStore kv) {
        this.kv = kv;
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
        return new LevelDef("c" + seq, "Eigenes Level " + seq);
    }

    public void save(LevelDef l) {
        if (l.id == null || l.id.isEmpty() || l.builtin) {
            throw new IllegalArgumentException("Nur eigene Level mit Kennung können gespeichert werden");
        }
        kv.put(LEVEL_PREFIX + l.id, LevelCodec.encode(l));
        List<String> ids = ids();
        if (!ids.contains(l.id)) {
            ids.add(l.id);
            writeIds(ids);
        }
    }

    public void delete(String id) {
        kv.remove(LEVEL_PREFIX + id);
        List<String> ids = ids();
        if (ids.remove(id)) {
            writeIds(ids);
        }
    }
}

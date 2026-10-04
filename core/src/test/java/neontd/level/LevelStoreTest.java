package neontd.level;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neontd.platform.MemoryStore;
import org.junit.jupiter.api.Test;

class LevelStoreTest {
    @Test
    void codecRoundTripsBuiltinLevel() {
        LevelDef a = Levels.serpentine();
        LevelDef b = LevelCodec.decode("serpentine", LevelCodec.encode(a));
        assertNotNull(b);
        assertEquals(a.name, b.name);
        assertEquals(a.width, b.width);
        assertEquals(a.startMoney, b.startMoney);
        assertEquals(a.paths.size(), b.paths.size());
        assertArrayEquals(a.paths.get(0), b.paths.get(0), 0.06);
    }

    @Test
    void codecRejectsGarbageAndIgnoresUnknownKeys() {
        assertNull(LevelCodec.decode("x", null));
        assertNull(LevelCodec.decode("x", "hello world"));
        assertNull(LevelCodec.decode("x", "NTD1\npath=1,2;abc,def\n"));
        LevelDef l = LevelCodec.decode("x", "NTD1\nname=Test\nfuture=42\npath=0,0;100,0;200,50\n");
        assertNotNull(l);
        assertEquals("Test", l.name);
        assertEquals(1, l.paths.size());
    }

    @Test
    void storeCreatesSavesLoadsAndDeletes() {
        MemoryStore kv = new MemoryStore();
        LevelStore store = new LevelStore(kv);
        assertTrue(store.loadAll().isEmpty());

        LevelDef a = store.createNew();
        LevelDef b = store.createNew();
        assertNotEquals(a.id, b.id);
        assertEquals("Eigenes Level 1", a.name);
        assertEquals("Eigenes Level 2", b.name);

        a.paths.add(new double[] {40, 100, 640, 120, 1200, 600});
        store.save(a);
        store.save(b);
        assertEquals(2, store.loadAll().size());
        assertEquals(a.id, store.loadAll().get(0).id);

        a.name = "Umbenannt";
        store.save(a);
        assertEquals(2, store.loadAll().size());
        assertEquals("Umbenannt", store.load(a.id).name);

        store.delete(a.id);
        assertEquals(1, store.loadAll().size());
        assertNull(store.load(a.id));
        // Die Zähler laufen weiter: gelöschte Kennungen werden nicht wiederverwendet.
        assertEquals("Eigenes Level 3", store.createNew().name);
    }

    @Test
    void corruptEntriesAreSkipped() {
        MemoryStore kv = new MemoryStore();
        LevelStore store = new LevelStore(kv);
        LevelDef a = store.createNew();
        a.paths.add(new double[] {10, 10, 500, 10});
        store.save(a);
        kv.put("neontd.levels", a.id + ",ghost");
        assertEquals(1, store.loadAll().size());
    }
}

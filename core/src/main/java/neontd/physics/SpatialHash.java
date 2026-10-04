package neontd.physics;

import java.util.Arrays;

/**
 * Gleichmäßiges Raster für schnelle Umkreissuchen (Broad-Phase).
 *
 * <p>Jeder Eintrag landet genau in der Zelle seines Mittelpunkts, damit es keine Duplikate gibt. Wer
 * Objekte mit Radius sucht, übergibt deshalb {@code radius + größterObjektRadius} und prüft das Ergebnis
 * anschließend genau (Narrow-Phase). Die Struktur ist allokationsfrei im laufenden Betrieb.
 */
public final class SpatialHash {
    private final double invCell;
    private final int cols;
    private final int rows;
    private final int[] head;
    private int[] next = new int[256];
    private int[] ids = new int[256];
    private int count;

    public SpatialHash(double worldWidth, double worldHeight, double cellSize) {
        this.invCell = 1.0 / cellSize;
        this.cols = Math.max(1, (int) Math.ceil(worldWidth * invCell));
        this.rows = Math.max(1, (int) Math.ceil(worldHeight * invCell));
        this.head = new int[cols * rows];
        clear();
    }

    public void clear() {
        Arrays.fill(head, -1);
        count = 0;
    }

    private int cellX(double x) {
        int c = (int) Math.floor(x * invCell);
        return c < 0 ? 0 : (c >= cols ? cols - 1 : c);
    }

    private int cellY(double y) {
        int c = (int) Math.floor(y * invCell);
        return c < 0 ? 0 : (c >= rows ? rows - 1 : c);
    }

    /** Fügt id an Position (x, y) ein. Positionen außerhalb der Welt werden an den Rand geklemmt. */
    public void insert(int id, double x, double y) {
        if (count == ids.length) {
            ids = Arrays.copyOf(ids, count * 2);
            next = Arrays.copyOf(next, count * 2);
        }
        int cell = cellY(y) * cols + cellX(x);
        ids[count] = id;
        next[count] = head[cell];
        head[cell] = count;
        count++;
    }

    /**
     * Schreibt alle ids, deren Zelle den Suchkreis berührt, nach {@code out}.
     *
     * @return Anzahl der gefundenen ids (höchstens {@code out.length})
     */
    public int query(double cx, double cy, double r, int[] out) {
        int x0 = cellX(cx - r);
        int x1 = cellX(cx + r);
        int y0 = cellY(cy - r);
        int y1 = cellY(cy + r);
        int n = 0;
        for (int cy2 = y0; cy2 <= y1; cy2++) {
            int rowBase = cy2 * cols;
            for (int cx2 = x0; cx2 <= x1; cx2++) {
                for (int node = head[rowBase + cx2]; node >= 0; node = next[node]) {
                    if (n == out.length) {
                        return n;
                    }
                    out[n++] = ids[node];
                }
            }
        }
        return n;
    }
}

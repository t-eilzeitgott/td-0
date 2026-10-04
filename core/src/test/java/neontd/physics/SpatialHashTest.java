package neontd.physics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import neontd.math.Rng;
import org.junit.jupiter.api.Test;

class SpatialHashTest {
    @Test
    void queryNeverMissesPointsInsideTheCircle() {
        Rng rng = new Rng(42);
        SpatialHash h = new SpatialHash(1280, 720, 64);
        int n = 500;
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = rng.range(-50, 1330);
            y[i] = rng.range(-50, 770);
            h.insert(i, x[i], y[i]);
        }
        int[] out = new int[n];
        for (int q = 0; q < 200; q++) {
            double cx = rng.range(0, 1280);
            double cy = rng.range(0, 720);
            double r = rng.range(10, 250);
            int c = h.query(cx, cy, r, out);
            boolean[] found = new boolean[n];
            for (int i = 0; i < c; i++) {
                found[out[i]] = true;
            }
            for (int i = 0; i < n; i++) {
                if (Math.hypot(x[i] - cx, y[i] - cy) <= r) {
                    assertTrue(found[i], "Punkt " + i + " fehlt bei Anfrage " + q);
                }
            }
        }
    }

    @Test
    void clearEmptiesTheGrid() {
        SpatialHash h = new SpatialHash(100, 100, 10);
        h.insert(7, 50, 50);
        int[] out = new int[4];
        assertTrue(h.query(50, 50, 5, out) == 1);
        h.clear();
        assertTrue(h.query(50, 50, 5, out) == 0);
    }
}

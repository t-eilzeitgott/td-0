package neontd.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neontd.math.Rng;
import org.junit.jupiter.api.Test;

class MiscPhysicsTest {
    @Test
    void fixedTimestepProducesStableStepCounts() {
        FixedTimestep ft = new FixedTimestep(1.0 / 60, 5);
        int total = 0;
        for (int i = 0; i < 600; i++) {
            total += ft.advance(1.0 / 144); // 144-Hz-Display
        }
        // 600 Frames bei 144 Hz = 4,1667 s => 250 Schritte (±1 wegen Restzeit im Akkumulator)
        assertTrue(Math.abs(total - 250) <= 1, "total=" + total);
        assertTrue(ft.alpha() >= 0 && ft.alpha() < 1);
    }

    @Test
    void fixedTimestepCapsCatchUp() {
        FixedTimestep ft = new FixedTimestep(1.0 / 60, 5);
        assertEquals(5, ft.advance(10.0)); // 10 s Rückstand => nur 5 Schritte, Rest verworfen
        assertEquals(0, ft.advance(0.001));
    }

    @Test
    void rngIsDeterministicAndInRange() {
        Rng a = new Rng(1234);
        Rng b = new Rng(1234);
        double sum = 0;
        for (int i = 0; i < 10000; i++) {
            double va = a.nextDouble();
            assertEquals(va, b.nextDouble(), 0);
            assertTrue(va >= 0 && va < 1);
            sum += va;
        }
        assertEquals(0.5, sum / 10000, 0.02);
        Rng c = new Rng(99);
        for (int i = 0; i < 1000; i++) {
            int v = c.nextInt(7);
            assertTrue(v >= 0 && v < 7);
        }
    }

    @Test
    void rdpCollapsesStraightLinesAndKeepsCorners() {
        double[] line = {0, 0, 10, 0.2, 20, -0.1, 30, 0.1, 40, 0};
        double[] out = new double[line.length];
        assertEquals(2, Simplify.rdp(line, 5, 2, out));

        double[] ell = {0, 0, 50, 0, 100, 0, 100, 50, 100, 100};
        double[] out2 = new double[ell.length];
        int c = Simplify.rdp(ell, 5, 2, out2);
        assertEquals(3, c);
        assertEquals(100, out2[2], 0);
        assertEquals(0, out2[3], 0);
    }

    @Test
    void enforceMinGapKeepsEndpoints() {
        double[] pts = {0, 0, 5, 0, 10, 0, 60, 0, 62, 0};
        int c = Simplify.enforceMinGap(pts, 5, 20);
        assertEquals(0, pts[0], 0);
        assertEquals(62, pts[(c - 1) * 2], 0);
        assertTrue(c <= 3);
    }
}

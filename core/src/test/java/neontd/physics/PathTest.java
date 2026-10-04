package neontd.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neontd.math.Vec2;
import org.junit.jupiter.api.Test;

class PathTest {
    private static final double[] SNAKE = {
        0, 100, 300, 100, 400, 200, 300, 300, 100, 300, 0, 400, 100, 500, 500, 500
    };

    @Test
    void straightPathHasExactLength() {
        Path p = Path.fromControlPoints(new double[] {0, 0, 300, 400}, 2, 4);
        assertEquals(500, p.length(), 1e-9);
        Vec2 v = new Vec2();
        p.positionAt(250, v);
        assertEquals(150, v.x, 1e-9);
        assertEquals(200, v.y, 1e-9);
    }

    @Test
    void splinePassesThroughEveryControlPoint() {
        Path p = Path.fromControlPoints(SNAKE, SNAKE.length / 2, 4);
        for (int i = 0; i < SNAKE.length / 2; i++) {
            assertEquals(0, p.distanceTo(SNAKE[i * 2], SNAKE[i * 2 + 1]), 0.01, "Kontrollpunkt " + i);
        }
    }

    @Test
    void arcLengthParameterizationIsUniform() {
        Path p = Path.fromControlPoints(SNAKE, SNAKE.length / 2, 4);
        Vec2 a = new Vec2();
        Vec2 b = new Vec2();
        double maxStep = 0;
        double minStep = Double.MAX_VALUE;
        for (double s = 0; s + 1 < p.length(); s += 1) {
            p.positionAt(s, a);
            p.positionAt(s + 1, b);
            double d = Math.hypot(b.x - a.x, b.y - a.y);
            maxStep = Math.max(maxStep, d);
            minStep = Math.min(minStep, d);
        }
        // Eine Bogenlängeneinheit entspricht höchstens einer Welteinheit Luftlinie (Kurven sind kürzer, nie länger).
        assertTrue(maxStep <= 1.0000001, "maxStep=" + maxStep);
        assertTrue(minStep > 0.9, "minStep=" + minStep);
    }

    @Test
    void positionIsClampedAtEnds() {
        Path p = Path.fromControlPoints(SNAKE, SNAKE.length / 2, 4);
        Vec2 v = new Vec2();
        p.positionAt(-50, v);
        assertEquals(0, v.x, 1e-9);
        assertEquals(100, v.y, 1e-9);
        p.positionAt(p.length() + 50, v);
        assertEquals(500, v.x, 1e-9);
        assertEquals(500, v.y, 1e-9);
    }

    @Test
    void projectFindsClosestArcLength() {
        Path p = Path.fromControlPoints(new double[] {0, 0, 1000, 0}, 2, 4);
        Vec2 c = new Vec2();
        double s = p.project(400, 77, c);
        assertEquals(400, s, 1e-9);
        assertEquals(400, c.x, 1e-9);
        assertEquals(0, c.y, 1e-9);
        assertEquals(77, p.distanceTo(400, 77), 1e-9);
    }

    @Test
    void duplicatePointsAreMergedAndTooFewPointsRejected() {
        Path p = Path.fromControlPoints(new double[] {0, 0, 0.2, 0.1, 100, 0}, 3, 4);
        assertEquals(100, p.length(), 1e-6);
        assertThrows(IllegalArgumentException.class,
                () -> Path.fromControlPoints(new double[] {5, 5, 5.1, 5.1}, 2, 4));
        assertEquals(1, Path.distinctCount(new double[] {5, 5, 5.1, 5.1}, 2));
    }

    @Test
    void headingFollowsTheCurve() {
        Path p = Path.fromControlPoints(new double[] {0, 0, 100, 0, 100, 100}, 3, 4);
        assertEquals(0, p.headingAt(5), 0.2);
        assertEquals(Math.PI / 2, p.headingAt(p.length() - 5), 0.35);
    }
}

package neontd.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CollisionTest {
    @Test
    void fastProjectileDoesNotTunnelThroughSmallTarget() {
        // Projektil fliegt 50 Einheiten pro Schritt, das Ziel hat nur Radius 5 und liegt genau dazwischen.
        double t = Collision.sweptCircles(0, 0, 50, 0, 2, 25, 0, 0, 0, 5);
        assertTrue(t >= 0 && t < 1, "t=" + t);
        // Anfang und Ende des Schritts überlappen *nicht* – ein naiver Test würde den Treffer verpassen.
        assertFalse(Collision.circlesOverlap(0, 0, 2, 25, 0, 5));
        assertFalse(Collision.circlesOverlap(50, 0, 2, 25, 0, 5));
        assertEquals(18.0 / 50.0, t, 1e-9); // Kontakt bei x = 25 - (5 + 2)
    }

    @Test
    void movingTargetIsAccountedFor() {
        // Ziel läuft dem Projektil davon: Relativgeschwindigkeit zu klein, kein Treffer in diesem Schritt.
        assertEquals(-1, Collision.sweptCircles(0, 0, 10, 0, 2, 30, 0, 10, 0, 5), 0);
        // Ziel läuft ins Projektil hinein: Treffer.
        assertTrue(Collision.sweptCircles(0, 0, 10, 0, 2, 30, 0, -20, 0, 5) >= 0);
    }

    @Test
    void alreadyOverlappingReportsImmediateContact() {
        assertEquals(0, Collision.sweptCircles(0, 0, 5, 0, 5, 3, 0, 0, 0, 5), 0);
    }

    @Test
    void missesWhenPassingBesideTarget() {
        assertEquals(-1, Collision.sweptCircles(0, 0, 100, 0, 2, 50, 20, 0, 0, 5), 0);
    }

    @Test
    void segmentCircleAndPointSegment() {
        assertEquals(0.4, Collision.segmentCircle(0, 0, 100, 0, 50, 0, 10), 1e-9);
        assertEquals(-1, Collision.segmentCircle(0, 0, 100, 0, 50, 30, 10), 0);
        assertEquals(25, Collision.pointSegmentDistSq(5, 5, 0, 0, 10, 0), 1e-9);
        assertEquals(50, Collision.pointSegmentDistSq(-5, 5, 0, 0, 10, 0), 1e-9);
    }
}

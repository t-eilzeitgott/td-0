package neontd.level;

import neontd.math.Mathx;

/**
 * Eine Zone auf der Karte: entweder ein <b>Hindernis</b> (dort kann nicht gebaut werden – Gebäude, Fels, Wasser)
 * oder ein <b>Bonus-Pad</b> (ein Turm, der dort steht, bekommt einen dauerhaften Bonus). Zonen machen die
 * Platzierung zur Entscheidung: Der beste Platz ist nicht immer frei, und das Pad liegt selten dort, wo man es
 * am liebsten hätte.
 */
public final class Zone {
    /** Art der Zone; die Pads tragen je einen Bonus. */
    public enum Kind {
        VOID("Gesperrt", "Hier kann nicht gebaut werden", 0x3A3A52),
        PAD_DAMAGE("Kraftfeld", "+25 % Schaden für den Turm auf dem Pad", 0xFF4D5E),
        PAD_RANGE("Weitblick", "+25 % Reichweite für den Turm auf dem Pad", 0x4DA6FF),
        PAD_SPEED("Taktgeber", "+20 % Feuerrate für den Turm auf dem Pad", 0xFFD60A);

        public final String label;
        public final String desc;
        public final int color;

        Kind(String label, String desc, int color) {
            this.label = label;
            this.desc = desc;
            this.color = color;
        }

        public boolean pad() {
            return this != VOID;
        }
    }

    /** Radius aller Bonus-Pads (ein Turm passt genau darauf). */
    public static final double PAD_RADIUS = 36;
    public static final double PAD_DAMAGE_BONUS = 0.25;
    public static final double PAD_RANGE_BONUS = 0.25;
    public static final double PAD_SPEED_BONUS = 0.20;

    public final Kind kind;
    /** Mittelpunkt und halbe Ausdehnung (Hindernisse sind abgerundete Rechtecke, Pads Kreise mit r = {@code w}). */
    public final double x;
    public final double y;
    public final double w;
    public final double h;

    public Zone(Kind kind, double x, double y, double w, double h) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    /** Kreisförmiges Bonus-Pad. */
    public static Zone pad(Kind kind, double x, double y) {
        return new Zone(kind, x, y, PAD_RADIUS, PAD_RADIUS);
    }

    /** Hindernis als abgerundetes Rechteck mit Mittelpunkt (x, y) und voller Größe w × h. */
    public static Zone block(double x, double y, double w, double h) {
        return new Zone(Kind.VOID, x, y, w / 2, h / 2);
    }

    /** Rundes Hindernis. */
    public static Zone rock(double x, double y, double r) {
        return new Zone(Kind.VOID, x, y, r, r);
    }

    public boolean round() {
        return kind.pad() || w == h;
    }

    /** Überschneidet ein Kreis (Turm) das Hindernis? */
    public boolean blocks(double cx, double cy, double r) {
        if (kind.pad()) {
            return false;
        }
        if (w == h) {
            return Mathx.distSq(cx, cy, x, y) < (w + r) * (w + r);
        }
        double nx = Mathx.clamp(cx, x - w, x + w);
        double ny = Mathx.clamp(cy, y - h, y + h);
        return Mathx.distSq(cx, cy, nx, ny) < r * r;
    }

    /** Steht ein Turm mit Mittelpunkt (cx, cy) auf dem Pad? */
    public boolean covers(double cx, double cy) {
        return kind.pad() && Mathx.distSq(cx, cy, x, y) <= (w * 0.9) * (w * 0.9);
    }

    /** Kleinster Abstand eines Punkts zum Rand des Hindernisses (negativ = innen). */
    public double distance(double px, double py) {
        if (w == h) {
            return Mathx.dist(px, py, x, y) - w;
        }
        double nx = Mathx.clamp(px, x - w, x + w);
        double ny = Mathx.clamp(py, y - h, y + h);
        return Mathx.dist(px, py, nx, ny);
    }
}

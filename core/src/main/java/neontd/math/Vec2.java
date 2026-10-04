package neontd.math;

/** Veränderlicher 2D-Vektor. Wird vor allem als Out-Parameter benutzt, um Allokationen in heißen Schleifen zu vermeiden. */
public final class Vec2 {
    public double x;
    public double y;

    public Vec2() {
    }

    public Vec2(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public Vec2 set(double x, double y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public Vec2 set(Vec2 o) {
        this.x = o.x;
        this.y = o.y;
        return this;
    }

    public Vec2 add(double dx, double dy) {
        x += dx;
        y += dy;
        return this;
    }

    public Vec2 scale(double s) {
        x *= s;
        y *= s;
        return this;
    }

    public double len() {
        return Math.sqrt(x * x + y * y);
    }

    public double lenSq() {
        return x * x + y * y;
    }

    /** Normalisiert den Vektor; ein Nullvektor bleibt unverändert. */
    public Vec2 normalize() {
        double l = len();
        if (l > Mathx.EPS) {
            x /= l;
            y /= l;
        }
        return this;
    }

    public double angle() {
        return Math.atan2(y, x);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}

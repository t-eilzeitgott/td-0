package neontd.math;

/**
 * Deterministischer Zufallsgenerator (mulberry32) nur mit 32-Bit-Integer-Arithmetik.
 * Gleicher Seed ergibt auf JVM und im Browser dieselbe Folge – wichtig für reproduzierbare Simulationen und Tests.
 */
public final class Rng {
    private int state;

    public Rng(int seed) {
        this.state = seed;
    }

    public int nextInt() {
        int t = (state += 0x6D2B79F5);
        t = (t ^ (t >>> 15)) * (t | 1);
        t ^= t + (t ^ (t >>> 7)) * (t | 61);
        return t ^ (t >>> 14);
    }

    /** Gleichverteilt in [0, bound). */
    public int nextInt(int bound) {
        if (bound <= 0) {
            return 0;
        }
        return (int) (nextDouble() * bound);
    }

    /** Gleichverteilt in [0, 1). */
    public double nextDouble() {
        return (nextInt() >>> 1) / 2147483648.0;
    }

    public double range(double lo, double hi) {
        return lo + (hi - lo) * nextDouble();
    }

    public boolean chance(double p) {
        return nextDouble() < p;
    }

    /** Zufallswinkel in [0, 2π). */
    public double angle() {
        return nextDouble() * Mathx.TAU;
    }

    /** Näherung einer Normalverteilung (Summe von 4 Gleichverteilungen, Mittelwert 0, σ≈1). */
    public double gaussian() {
        double s = nextDouble() + nextDouble() + nextDouble() + nextDouble();
        return (s - 2.0) * 1.7320508;
    }
}

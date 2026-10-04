package neontd.physics;

/**
 * Entkoppelt Simulation und Darstellung: Die Welt wird immer in festen Schritten berechnet (deterministisch,
 * unabhängig von der Framerate), gezeichnet wird mit Interpolation zwischen den letzten beiden Zuständen.
 */
public final class FixedTimestep {
    private final double step;
    private final int maxStepsPerFrame;
    private double accumulator;

    public FixedTimestep(double step, int maxStepsPerFrame) {
        this.step = step;
        this.maxStepsPerFrame = maxStepsPerFrame;
    }

    public double step() {
        return step;
    }

    /**
     * Fügt Echtzeit hinzu und liefert, wie viele Simulationsschritte jetzt auszuführen sind. Bei Rückstand
     * (z.B. nach Tab-Wechsel) wird gekappt, statt in eine Aufholspirale zu geraten.
     */
    public int advance(double dt) {
        accumulator += dt;
        int steps = (int) (accumulator / step);
        if (steps > maxStepsPerFrame) {
            steps = maxStepsPerFrame;
            accumulator = 0;
            return steps;
        }
        accumulator -= steps * step;
        return steps;
    }

    /** Anteil (0..1) des nächsten Schritts, der schon vergangen ist – für die Interpolation beim Zeichnen. */
    public double alpha() {
        return accumulator / step;
    }

    public void reset() {
        accumulator = 0;
    }
}

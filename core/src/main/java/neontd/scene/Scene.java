package neontd.scene;

import neontd.app.App;
import neontd.gfx.Gfx;

/** Ein Bildschirm des Spiels (Menü, Levelauswahl, Spiel, Editor). */
public abstract class Scene {
    protected final App app;

    protected Scene(App app) {
        this.app = app;
    }

    /** Wird aufgerufen, sobald die Szene sichtbar wird, und nach jeder Größenänderung des Fensters. */
    public void layout() {
    }

    public void onEnter() {
    }

    public void onExit() {
    }

    public abstract void update(double dt);

    public abstract void render(Gfx g);

    public void pointerDown(double x, double y, boolean touch) {
    }

    public void pointerMove(double x, double y, boolean pressed, boolean touch) {
    }

    public void pointerUp(double x, double y, boolean touch) {
    }

    public void pointerCancel() {
    }

    /** Mausrad / Scroll-Geste: dy in logischen Pixeln (positiv = nach unten). */
    public void wheel(double dy) {
    }

    /** Taste gedrückt (Code wie im Browser: "Escape", "Space", "Digit1", "KeyZ" …). @return behandelt? */
    public boolean key(String code, boolean ctrl) {
        return false;
    }

    /** Zurück-Geste / Escape. @return behandelt? */
    public boolean back() {
        return false;
    }
}

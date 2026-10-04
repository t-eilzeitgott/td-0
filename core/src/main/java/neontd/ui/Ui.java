package neontd.ui;

import java.util.ArrayList;
import neontd.gfx.Gfx;

/** Hält Schaltflächen einer Szene und verteilt Zeigerereignisse (Maus, Finger) an sie. */
public final class Ui {
    public final ArrayList<Button> buttons = new ArrayList<>();
    private Button pressed;

    public <T extends Button> T add(T b) {
        buttons.add(b);
        return b;
    }

    public void clear() {
        buttons.clear();
        pressed = null;
    }

    private Button hit(double x, double y) {
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Button b = buttons.get(i);
            if (b.visible && b.enabled && b.appear.value > 0.3 && b.contains(x, y)) {
                return b;
            }
        }
        return null;
    }

    /** @return true, wenn eine Schaltfläche getroffen wurde (Ereignis verbraucht) */
    public boolean pointerDown(double x, double y) {
        pressed = hit(x, y);
        if (pressed != null) {
            pressed.press.target = 1;
            return true;
        }
        return false;
    }

    public void pointerMove(double x, double y, boolean mouse) {
        Button over = mouse ? hit(x, y) : null;
        for (int i = 0; i < buttons.size(); i++) {
            Button b = buttons.get(i);
            b.hover.target = (b == over && b.enabled) ? 1 : 0;
        }
        if (pressed != null && !pressed.contains(x, y)) {
            pressed.press.target = 0;
        } else if (pressed != null) {
            pressed.press.target = 1;
        }
    }

    /** @return true, wenn das Loslassen zu einer gedrückten Schaltfläche gehörte */
    public boolean pointerUp(double x, double y) {
        Button p = pressed;
        pressed = null;
        if (p == null) {
            return false;
        }
        p.press.target = 0;
        if (p.contains(x, y) && p.enabled && p.onClick != null) {
            p.onClick.run();
        }
        return true;
    }

    public void cancel() {
        if (pressed != null) {
            pressed.press.target = 0;
            pressed = null;
        }
    }

    public boolean isPressing() {
        return pressed != null;
    }

    public void update(double dt) {
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).update(dt);
        }
    }

    public void render(Gfx g) {
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).render(g);
        }
    }
}

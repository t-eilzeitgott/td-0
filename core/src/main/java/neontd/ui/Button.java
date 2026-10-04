package neontd.ui;

import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Neon;
import neontd.gfx.NeonText;
import neontd.gfx.Theme;

/** Eine Schaltfläche im Neon-Stil mit Hover-, Druck- und Einblend-Animation. */
public final class Button {
    /** Eigene Darstellung statt des Standardaussehens. Koordinaten: (0,0) = Mitte, Größe b.w × b.h. */
    public interface Painter {
        void paint(Gfx g, Button b);
    }

    public double x;
    public double y;
    public double w;
    public double h;
    public String label;
    public Icons.Icon icon;
    public int color = Theme.CYAN;
    public boolean enabled = true;
    public boolean visible = true;
    public boolean selected;
    /** Beschriftung in der Neon-Strichschrift statt der Systemschrift. */
    public boolean neonFont;
    public double fontScale = 1;
    public Runnable onClick;
    public Painter painter;

    public final Smooth hover = new Smooth(0, 16);
    public final Smooth press = new Smooth(0, 26);
    public final Smooth appear = new Smooth(1, 10);
    private double appearDelay;

    public Button(String label, Icons.Icon icon, int color, Runnable onClick) {
        this.label = label;
        this.icon = icon;
        this.color = color;
        this.onClick = onClick;
    }

    public Button bounds(double x, double y, double w, double h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        return this;
    }

    /** Blendet die Schaltfläche nach {@code delay} Sekunden mit einem kleinen Pop ein. */
    public Button appearAfter(double delay) {
        appear.snap(0);
        appear.target = 1;
        appearDelay = delay;
        return this;
    }

    public boolean contains(double px, double py) {
        return visible && px >= x && px <= x + w && py >= y && py <= y + h;
    }

    public double cx() {
        return x + w / 2;
    }

    public double cy() {
        return y + h / 2;
    }

    public void update(double dt) {
        if (appearDelay > 0) {
            appearDelay -= dt;
        } else {
            appear.update(dt);
        }
        hover.update(dt);
        press.update(dt);
    }

    public void render(Gfx g) {
        if (!visible) {
            return;
        }
        double ap = appear.value;
        if (ap <= 0.01) {
            return;
        }
        double pop = Easing.outBack(Math.min(1, ap));
        double sc = (0.8 + 0.2 * pop) * (1 - 0.05 * press.value);
        g.save();
        g.translate(cx(), cy());
        g.scale(sc, sc);
        g.alpha(Math.min(1, ap * 1.5) * (enabled ? 1 : 0.38));
        if (painter != null) {
            painter.paint(g, this);
        } else {
            paintDefault(g);
        }
        g.restore();
    }

    private void paintDefault(Gfx g) {
        double r = Math.min(w, h) * 0.28;
        double hv = Math.max(hover.value, selected ? 1 : 0);
        double pr = press.value;
        g.fillRoundRect(-w / 2, -h / 2, w, h, r, Colors.withAlpha(Theme.PANEL, 0.92));
        g.fillRoundRect(-w / 2, -h / 2, w, h, r, Colors.withAlpha(color, 0.05 + 0.10 * hv + 0.20 * pr));
        Neon.roundRect(g, -w / 2, -h / 2, w, h, r, 2, color, 3 + 7 * hv + 4 * pr);

        double textSize = h * 0.40 * fontScale;
        double iconSize = h * 0.30;
        boolean hasIcon = icon != null;
        boolean hasText = label != null && !label.isEmpty();
        double textW = 0;
        if (hasText) {
            textW = neonFont ? NeonText.width(label, textSize * 1.08) : g.textWidth(label, textSize, true);
        }
        double gap = hasIcon && hasText ? h * 0.2 : 0;
        double total = (hasIcon ? iconSize * 2 : 0) + gap + textW;
        // Passt der Inhalt nicht hinein, wird er verhältnismäßig verkleinert.
        double avail = w * 0.86;
        if (total > avail) {
            double k = avail / total;
            textSize *= k;
            iconSize *= k;
            textW *= k;
            gap *= k;
            total = avail;
        }
        double left = -total / 2;
        if (hasIcon) {
            Icons.draw(g, icon, left + iconSize, 0, iconSize, color, 3 + 3 * hv);
            left += iconSize * 2 + gap;
        }
        if (hasText) {
            if (neonFont) {
                NeonText.draw(g, label, left + textW / 2, 0, textSize * 1.08, Colors.lighten(color, 0.25), 3 + 3 * hv, 0, 0);
            } else {
                g.text(label, left, 0, textSize, Theme.TEXT, Gfx.ALIGN_LEFT, true);
            }
        }
    }
}

package neontd.ui;

import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.NeonText;
import neontd.gfx.Theme;

/**
 * Eine Schaltfläche im cleanen Stil (Fläche, Systemschrift, weicher Druck) mit Hover-, Druck- und Einblend-Animation.
 * {@link Style#PRIMARY} ist die gefüllte Akzent-Taste, {@link Style#SECONDARY} die dunkle Fläche, {@link Style#GHOST}
 * nur eine Linie.
 */
public final class Button {
    public enum Style { PRIMARY, SECONDARY, GHOST }

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
    public Style style = Style.SECONDARY;
    /** Kleiner roter Punkt oben rechts (etwas wartet). */
    public boolean badge;
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
        double sc = (0.9 + 0.1 * pop) * (1 - 0.035 * press.value);
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
        double r = Math.min(h * 0.5, Math.max(10, h * 0.32));
        double hv = hover.value;
        double pr = press.value;
        int fill;
        int fg;
        switch (style) {
            case PRIMARY:
                fill = Colors.lerp(Colors.lighten(color, 0.08 * hv), Colors.darken(color, 0.2), pr);
                fg = 0x000000;
                g.fillRoundRect(-w / 2, -h / 2, w, h, r, fill);
                break;
            case GHOST:
                fg = selected ? color : Theme.TEXT;
                if (hv > 0 || pr > 0 || selected) {
                    g.fillRoundRect(-w / 2, -h / 2, w, h, r, Colors.withAlpha(color, 0.06 * hv + 0.12 * pr + (selected ? 0.14 : 0)));
                }
                g.strokeRoundRect(-w / 2, -h / 2, w, h, r, 1, selected ? Colors.withAlpha(color, 0.6) : Theme.LINE);
                break;
            default:
                if (selected) {
                    fill = Colors.withAlpha(color, 0.18 + 0.06 * hv + 0.1 * pr);
                    fg = Colors.lighten(color, 0.3);
                } else {
                    fill = Colors.lerp(Theme.SURFACE2, Theme.SURFACE3, Math.max(hv * 0.8, pr));
                    fg = Theme.TEXT;
                }
                g.fillRoundRect(-w / 2, -h / 2, w, h, r, fill);
                break;
        }

        double textSize = h * 0.38 * fontScale;
        double iconSize = h * 0.26;
        boolean hasIcon = icon != null;
        boolean hasText = label != null && !label.isEmpty();
        double textW = 0;
        if (hasText) {
            textW = neonFont ? NeonText.width(label, textSize * 1.08) : g.textWidth(label, textSize, true);
        }
        double gap = hasIcon && hasText ? h * 0.18 : 0;
        double total = (hasIcon ? iconSize * 2 : 0) + gap + textW;
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
        int iconColor = style == Style.PRIMARY ? 0x000000 : (style == Style.SECONDARY && !selected ? color : fg);
        if (hasIcon) {
            Icons.draw(g, icon, left + iconSize, 0, iconSize, iconColor, 0);
            left += iconSize * 2 + gap;
        }
        if (hasText) {
            if (neonFont) {
                NeonText.draw(g, label, left + textW / 2, 0, textSize * 1.08, Colors.lighten(color, 0.25), 3 + 3 * hv, 0, 0);
            } else {
                g.text(label, left, 0, textSize, fg, Gfx.ALIGN_LEFT, true);
            }
        }
        if (badge) {
            double pr2 = Math.max(4, h * 0.09);
            g.fillCircle(w / 2 - pr2 * 1.4, -h / 2 + pr2 * 1.4, pr2, Theme.RED);
        }
    }
}

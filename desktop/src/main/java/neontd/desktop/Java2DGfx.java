package neontd.desktop;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;

/** {@link Gfx} auf Basis von Java2D (Desktop-Fenster und Screenshot-Werkzeug). */
public final class Java2DGfx implements Gfx {
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    private static final String FONT_FAMILY = "SansSerif";

    private Graphics2D g2;
    private double width;
    private double height;
    private Path2D.Double path = new Path2D.Double();
    private double alpha = 1;
    private boolean additive;

    private static final class State {
        AffineTransform tx;
        Shape clip;
        double alpha;
        boolean additive;
    }

    private final ArrayList<State> stack = new ArrayList<>();
    private final Map<Long, Font> fonts = new HashMap<>();

    /** Beginnt einen Frame auf dem übergebenen Graphics2D; width/height sind die logischen Maße. */
    public void begin(Graphics2D graphics, double w, double h) {
        this.g2 = graphics;
        this.width = w;
        this.height = h;
        this.alpha = 1;
        this.additive = false;
        this.stack.clear();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        path.reset();
    }

    @Override
    public double width() {
        return width;
    }

    @Override
    public double height() {
        return height;
    }

    @Override
    public void save() {
        State s = new State();
        s.tx = g2.getTransform();
        s.clip = g2.getClip();
        s.alpha = alpha;
        s.additive = additive;
        stack.add(s);
    }

    @Override
    public void restore() {
        if (stack.isEmpty()) {
            return;
        }
        State s = stack.remove(stack.size() - 1);
        g2.setTransform(s.tx);
        g2.setClip(s.clip);
        alpha = s.alpha;
        additive = s.additive;
    }

    @Override
    public void translate(double x, double y) {
        g2.translate(x, y);
    }

    @Override
    public void rotate(double radians) {
        g2.rotate(radians);
    }

    @Override
    public void scale(double sx, double sy) {
        g2.scale(sx, sy);
    }

    @Override
    public void alpha(double a) {
        alpha *= a;
    }

    @Override
    public void additive(boolean on) {
        additive = on;
    }

    @Override
    public void clipRect(double x, double y, double w, double h) {
        g2.clip(new Rectangle2D.Double(x, y, w, h));
    }

    @Override
    public void beginPath() {
        path.reset();
    }

    @Override
    public void moveTo(double x, double y) {
        path.moveTo(x, y);
    }

    @Override
    public void lineTo(double x, double y) {
        if (path.getCurrentPoint() == null) {
            path.moveTo(x, y);
        } else {
            path.lineTo(x, y);
        }
    }

    @Override
    public void arc(double cx, double cy, double r, double a0, double a1) {
        // Canvas: Winkel im Uhrzeigersinn bei nach unten zeigender y-Achse. Java2D: Grad, gegen den Uhrzeigersinn.
        double start = -Math.toDegrees(a0);
        double extent = -Math.toDegrees(a1 - a0);
        if (extent > 360) {
            extent = 360;
        } else if (extent < -360) {
            extent = -360;
        }
        path.append(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, start, extent, Arc2D.OPEN),
                path.getCurrentPoint() != null);
    }

    @Override
    public void closePath() {
        if (path.getCurrentPoint() != null) {
            path.closePath();
        }
    }

    /**
     * Echtes additives Mischen ist in Java2D langsam (eigener Composite). Das Live-Fenster nutzt deshalb normales
     * Alpha-Blending – auf schwarzem Grund sieht das fast identisch aus. Das Screenshot-Werkzeug schaltet es ein.
     */
    public static boolean exactAdditive;

    private void applyComposite(int color) {
        double a = alpha * Colors.alpha01(color);
        if (additive && exactAdditive) {
            g2.setComposite(new AdditiveComposite(a));
        } else {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.min(1, Math.max(0, a))));
        }
    }

    private static Color rgbColor(int c) {
        return new Color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF);
    }

    @Override
    public void fill(int color) {
        applyComposite(color);
        g2.setColor(rgbColor(color));
        g2.fill(path);
    }

    @Override
    public void stroke(double lw, int color) {
        applyComposite(color);
        g2.setColor(rgbColor(color));
        g2.setStroke(new BasicStroke((float) lw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(path);
    }

    @Override
    public void strokeDashed(double lw, int color, double dash, double gap, double phase) {
        applyComposite(color);
        g2.setColor(rgbColor(color));
        double period = Math.max(0.02, dash + gap);
        double ph = ((phase % period) + period) % period; // Java2D erlaubt keine negative Phase
        g2.setStroke(new BasicStroke((float) lw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
                new float[] {(float) Math.max(0.01, dash), (float) Math.max(0.01, gap)}, (float) ph));
        g2.draw(path);
    }

    @Override
    public void radialGlow(double cx, double cy, double r, int color) {
        applyComposite(color);
        Color c0 = rgbColor(color);
        Color c1 = new Color(c0.getRed(), c0.getGreen(), c0.getBlue(), 0);
        g2.setPaint(new RadialGradientPaint((float) cx, (float) cy, (float) r, new float[] {0f, 1f},
                new Color[] {c0, c1}));
        g2.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }

    private Font font(double size, boolean bold) {
        long key = (Math.round(size * 4) << 1) | (bold ? 1 : 0);
        Font f = fonts.get(key);
        if (f == null) {
            f = new Font(FONT_FAMILY, bold ? Font.BOLD : Font.PLAIN, 1).deriveFont((float) size);
            fonts.put(key, f);
        }
        return f;
    }

    @Override
    public void text(String s, double x, double y, double size, int color, int align, boolean bold) {
        applyComposite(color);
        g2.setColor(rgbColor(color));
        Font f = font(size, bold);
        g2.setFont(f);
        double w = f.getStringBounds(s, FRC).getWidth();
        java.awt.FontMetrics fm = g2.getFontMetrics(f);
        double px = align == ALIGN_CENTER ? x - w / 2 : (align == ALIGN_RIGHT ? x - w : x);
        double py = y + (fm.getAscent() - fm.getDescent()) / 2.0;
        g2.drawString(s, (float) px, (float) py);
    }

    @Override
    public double textWidth(String s, double size, boolean bold) {
        return font(size, bold).getStringBounds(s, FRC).getWidth();
    }
}

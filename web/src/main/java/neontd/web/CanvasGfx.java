package neontd.web;

import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import org.teavm.jso.canvas.CanvasGradient;
import org.teavm.jso.canvas.CanvasRenderingContext2D;

/** {@link Gfx} auf Basis von Canvas2D (läuft in jedem Browser, auch auf dem iPhone). */
final class CanvasGfx implements Gfx {
    private static final String FONT_FAMILY =
            "ui-rounded,'SF Pro Rounded','Avenir Next','Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif";
    private static final String HEX = "0123456789abcdef";

    private final CanvasRenderingContext2D ctx;
    private double width;
    private double height;

    // Zustand (wird bei save/restore mitgeführt)
    private double alpha = 1;
    private boolean additive;
    private final double[] alphaStack = new double[128];
    private final boolean[] additiveStack = new boolean[128];
    private int depth;

    // Zwischenspeicher, damit unveränderte Werte nicht erneut an den Browser gehen
    private double appliedAlpha = -1;
    private boolean appliedAdditive;
    private int appliedFill = -1;
    private int appliedStroke = -1;
    private double appliedLineWidth = -1;
    private int appliedAlign = -1;
    private String appliedFont = "";

    // Farb- und Schrift-Cache (direkt abgebildet, ohne Allokation bei Treffern)
    private final int[] colorKeys = new int[1024];
    private final String[] colorValues = new String[1024];
    private final int[] clearKeys = new int[256];
    private final String[] clearValues = new String[256];
    private final String[] fontCache = new String[2 * 1024];

    CanvasGfx(CanvasRenderingContext2D ctx) {
        this.ctx = ctx;
        java.util.Arrays.fill(colorKeys, -1);
        java.util.Arrays.fill(clearKeys, -1);
    }

    /** Beginnt einen Frame. width/height sind logische Pixel, dpr der Gerätepixel-Faktor. */
    void begin(double w, double h, double dpr) {
        this.width = w;
        this.height = h;
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.setGlobalAlpha(1);
        ctx.setGlobalCompositeOperation("source-over");
        ctx.setLineCap("round");
        ctx.setLineJoin("round");
        ctx.setTextBaseline("middle");
        Js.clearDash(ctx);
        alpha = 1;
        additive = false;
        depth = 0;
        appliedAlpha = 1;
        appliedAdditive = false;
        appliedFill = -1;
        appliedStroke = -1;
        appliedLineWidth = -1;
        appliedAlign = -1;
        appliedFont = "";
        ctx.beginPath();
    }

    @Override
    public double width() {
        return width;
    }

    @Override
    public double height() {
        return height;
    }

    // ------------------------------------------------------------------------------------------ Zustand

    @Override
    public void save() {
        if (depth < alphaStack.length) {
            alphaStack[depth] = alpha;
            additiveStack[depth] = additive;
        }
        depth++;
        ctx.save();
    }

    @Override
    public void restore() {
        if (depth <= 0) {
            return;
        }
        depth--;
        ctx.restore();
        if (depth < alphaStack.length) {
            alpha = alphaStack[depth];
            additive = additiveStack[depth];
        }
        // restore() setzt Alpha, Blendmodus, Linienbreite, Ausrichtung und Schrift im Browser zurück –
        // die Zwischenspeicher müssen daher neu abgeglichen werden.
        appliedAlpha = -1;
        appliedAdditive = !additive;
        appliedLineWidth = -1;
        appliedAlign = -1;
        appliedFont = "";
        appliedFill = -1;
        appliedStroke = -1;
    }

    @Override
    public void translate(double x, double y) {
        ctx.translate(x, y);
    }

    @Override
    public void rotate(double radians) {
        ctx.rotate(radians);
    }

    @Override
    public void scale(double sx, double sy) {
        ctx.scale(sx, sy);
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
        ctx.beginPath();
        ctx.rect(x, y, w, h);
        ctx.clip();
        ctx.beginPath();
    }

    // -------------------------------------------------------------------------------------------- Pfade

    @Override
    public void beginPath() {
        ctx.beginPath();
    }

    @Override
    public void moveTo(double x, double y) {
        ctx.moveTo(x, y);
    }

    @Override
    public void lineTo(double x, double y) {
        ctx.lineTo(x, y);
    }

    @Override
    public void arc(double cx, double cy, double r, double a0, double a1) {
        ctx.arc(cx, cy, r, a0, a1);
    }

    @Override
    public void closePath() {
        ctx.closePath();
    }

    private void applyAlphaAndBlend(int color) {
        double a = alpha * Colors.alpha01(color);
        if (a != appliedAlpha) {
            ctx.setGlobalAlpha(a);
            appliedAlpha = a;
        }
        if (additive != appliedAdditive) {
            ctx.setGlobalCompositeOperation(additive ? "lighter" : "source-over");
            appliedAdditive = additive;
        }
    }

    @Override
    public void fill(int color) {
        applyAlphaAndBlend(color);
        int rgb = color & 0xFFFFFF;
        if (rgb != appliedFill) {
            ctx.setFillStyle(css(rgb));
            appliedFill = rgb;
        }
        ctx.fill();
    }

    @Override
    public void stroke(double lw, int color) {
        applyAlphaAndBlend(color);
        int rgb = color & 0xFFFFFF;
        if (rgb != appliedStroke) {
            ctx.setStrokeStyle(css(rgb));
            appliedStroke = rgb;
        }
        if (lw != appliedLineWidth) {
            ctx.setLineWidth(lw);
            appliedLineWidth = lw;
        }
        ctx.stroke();
    }

    @Override
    public void strokeDashed(double lw, int color, double dash, double gap, double phase) {
        double period = Math.max(0.02, dash + gap);
        double ph = ((phase % period) + period) % period;
        Js.setDash(ctx, dash, gap, ph);
        stroke(lw, color);
        Js.clearDash(ctx);
    }

    @Override
    public void radialGlow(double cx, double cy, double r, int color) {
        applyAlphaAndBlend(color);
        int rgb = color & 0xFFFFFF;
        CanvasGradient grad = ctx.createRadialGradient(cx, cy, 0, cx, cy, r);
        grad.addColorStop(0, css(rgb));
        grad.addColorStop(1, cssClear(rgb));
        ctx.setFillStyle(grad);
        appliedFill = -1;
        ctx.fillRect(cx - r, cy - r, 2 * r, 2 * r);
    }

    // ---------------------------------------------------------------------------------------- Text

    private String font(double size, boolean bold) {
        int q = (int) Math.round(size * 2);
        if (q < 0) {
            q = 0;
        }
        if (q >= 1024) {
            return (bold ? "bold " : "") + size + "px " + FONT_FAMILY;
        }
        int idx = q * 2 + (bold ? 1 : 0);
        String f = fontCache[idx];
        if (f == null) {
            f = (bold ? "bold " : "") + (q / 2.0) + "px " + FONT_FAMILY;
            fontCache[idx] = f;
        }
        return f;
    }

    private void applyFont(double size, boolean bold) {
        String f = font(size, bold);
        if (!f.equals(appliedFont)) {
            ctx.setFont(f);
            appliedFont = f;
        }
    }

    @Override
    public void text(String s, double x, double y, double size, int color, int align, boolean bold) {
        applyAlphaAndBlend(color);
        int rgb = color & 0xFFFFFF;
        if (rgb != appliedFill) {
            ctx.setFillStyle(css(rgb));
            appliedFill = rgb;
        }
        applyFont(size, bold);
        if (align != appliedAlign) {
            ctx.setTextAlign(align == ALIGN_CENTER ? "center" : (align == ALIGN_RIGHT ? "right" : "left"));
            appliedAlign = align;
        }
        ctx.fillText(s, x, y);
    }

    @Override
    public double textWidth(String s, double size, boolean bold) {
        applyFont(size, bold);
        return ctx.measureText(s).getWidth();
    }

    // ---------------------------------------------------------------------------------------- Farben

    private String css(int rgb) {
        int idx = (rgb ^ (rgb >>> 10) ^ (rgb >>> 17)) & 1023;
        if (colorKeys[idx] == rgb) {
            return colorValues[idx];
        }
        String s = "#" + hex(rgb >> 20) + hex(rgb >> 16) + hex(rgb >> 12) + hex(rgb >> 8) + hex(rgb >> 4) + hex(rgb);
        colorKeys[idx] = rgb;
        colorValues[idx] = s;
        return s;
    }

    private String cssClear(int rgb) {
        int idx = (rgb ^ (rgb >>> 10) ^ (rgb >>> 17)) & 255;
        if (clearKeys[idx] == rgb) {
            return clearValues[idx];
        }
        String s = "rgba(" + ((rgb >> 16) & 255) + "," + ((rgb >> 8) & 255) + "," + (rgb & 255) + ",0)";
        clearKeys[idx] = rgb;
        clearValues[idx] = s;
        return s;
    }

    private static char hex(int nibble) {
        return HEX.charAt(nibble & 15);
    }
}

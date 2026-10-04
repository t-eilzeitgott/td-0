package neontd.gfx;

/**
 * Reicht alles an eine andere {@link Gfx} weiter, hält aber <b>Text aufrecht</b>, wenn die Karte gedreht gezeichnet
 * wird (Hochformat auf dem Handy: die Karte liegt um 90° gedreht, die HP-Zahlen sollen trotzdem waagerecht lesbar
 * bleiben). Der Text wird um seinen Ankerpunkt zurückgedreht.
 */
public final class UprightGfx implements Gfx {
    private Gfx g;
    private double counter;

    /** @param counterRotation Winkel, um den Text zurückgedreht wird (Radiant, im aktuellen Koordinatensystem) */
    public UprightGfx wrap(Gfx target, double counterRotation) {
        this.g = target;
        this.counter = counterRotation;
        return this;
    }

    @Override public double width() { return g.width(); }
    @Override public double height() { return g.height(); }
    @Override public void save() { g.save(); }
    @Override public void restore() { g.restore(); }
    @Override public void translate(double x, double y) { g.translate(x, y); }
    @Override public void rotate(double radians) { g.rotate(radians); }
    @Override public void scale(double sx, double sy) { g.scale(sx, sy); }
    @Override public void alpha(double a) { g.alpha(a); }
    @Override public void additive(boolean on) { g.additive(on); }
    @Override public void clipRect(double x, double y, double w, double h) { g.clipRect(x, y, w, h); }
    @Override public void beginPath() { g.beginPath(); }
    @Override public void moveTo(double x, double y) { g.moveTo(x, y); }
    @Override public void lineTo(double x, double y) { g.lineTo(x, y); }
    @Override public void arc(double cx, double cy, double r, double a0, double a1) { g.arc(cx, cy, r, a0, a1); }
    @Override public void closePath() { g.closePath(); }
    @Override public void fill(int color) { g.fill(color); }
    @Override public void stroke(double width, int color) { g.stroke(width, color); }

    @Override
    public void strokeDashed(double width, int color, double dash, double gap, double phase) {
        g.strokeDashed(width, color, dash, gap, phase);
    }

    @Override public void radialGlow(double cx, double cy, double r, int color) { g.radialGlow(cx, cy, r, color); }

    @Override
    public void text(String s, double x, double y, double size, int color, int align, boolean bold) {
        g.save();
        g.translate(x, y);
        g.rotate(counter);
        g.text(s, 0, 0, size, color, align, bold);
        g.restore();
    }

    @Override public double textWidth(String s, double size, boolean bold) { return g.textWidth(s, size, bold); }

    // Die Bequemlichkeits-Methoden weiterreichen, damit die schnellen Varianten der Zielimplementierung greifen.
    @Override public void rect(double x, double y, double w, double h) { g.rect(x, y, w, h); }
    @Override public void roundRect(double x, double y, double w, double h, double r) { g.roundRect(x, y, w, h, r); }
    @Override public void circle(double cx, double cy, double r) { g.circle(cx, cy, r); }
    @Override public void ngon(double cx, double cy, double r, int sides, double rotation) { g.ngon(cx, cy, r, sides, rotation); }
    @Override public void fillRect(double x, double y, double w, double h, int color) { g.fillRect(x, y, w, h, color); }

    @Override
    public void strokeRect(double x, double y, double w, double h, double lw, int color) {
        g.strokeRect(x, y, w, h, lw, color);
    }

    @Override
    public void fillRoundRect(double x, double y, double w, double h, double r, int color) {
        g.fillRoundRect(x, y, w, h, r, color);
    }

    @Override
    public void strokeRoundRect(double x, double y, double w, double h, double r, double lw, int color) {
        g.strokeRoundRect(x, y, w, h, r, lw, color);
    }

    @Override public void fillCircle(double cx, double cy, double r, int color) { g.fillCircle(cx, cy, r, color); }

    @Override
    public void strokeCircle(double cx, double cy, double r, double lw, int color) {
        g.strokeCircle(cx, cy, r, lw, color);
    }

    @Override
    public void line(double x1, double y1, double x2, double y2, double lw, int color) {
        g.line(x1, y1, x2, y2, lw, color);
    }
}

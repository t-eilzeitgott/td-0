package neontd.scene;

import java.util.ArrayList;
import java.util.List;
import neontd.app.App;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Icons.Icon;
import neontd.gfx.Neon;
import neontd.gfx.NeonText;
import neontd.gfx.Theme;
import neontd.math.Mathx;
import neontd.ui.Button;
import neontd.ui.Easing;
import neontd.ui.Fmt;
import neontd.ui.Smooth;
import neontd.ui.Ui;
import neontd.ui.Viewport;

/**
 * Gemeinsame Grundlage für die Listen-Bildschirme (Shop, Missionen, Errungenschaften, Einstellungen): Zurück-Taste,
 * Titel, Kristall-Anzeige, optionale Reiter, ein scrollbarer Inhaltsbereich (Ziehen mit Schwung, Mausrad), Hinweisleiste
 * und ein Bestätigungsdialog. Unterklassen zeichnen ihren Inhalt in {@link #drawContent} und melden antippbare Flächen
 * mit {@link #hit}; der Tipp kommt als {@link #onTap} zurück.
 */
abstract class PanelScene extends Scene {
    /** Eine antippbare Fläche des Inhalts (Bildschirmkoordinaten, gültig für ein Bild). */
    private static final class Hit {
        double x;
        double y;
        double w;
        double h;
        Object tag;
    }

    protected final Ui ui = new Ui();
    protected final Button back;
    protected double time;
    protected String title = "";
    protected int accent = Theme.CYAN;
    protected final List<Button> tabButtons = new ArrayList<>();
    protected int tab;

    // Geometrie (von layout() gesetzt)
    protected double contentX;
    protected double contentW;
    protected double contentTop;
    protected double contentBottom;
    private double titleY;
    private double chipRight;

    // Scrollen
    private double scroll;
    private double scrollMax;
    private double velocity;
    private boolean scrolling;
    private double downY;
    private double lastY;
    private double lastT;

    private final List<Hit> hits = new ArrayList<>();
    private Object pressedTag;
    protected Object hoverTag;

    // Hinweis und Dialog
    private String toast;
    private int toastColor = Theme.GREEN;
    private double toastTime;
    private boolean modalOpen;
    private String modalTitle = "";
    private final List<String> modalLines = new ArrayList<>();
    private int modalColor = Theme.CYAN;
    private final Ui modalUi = new Ui();
    private final Smooth modalIn = new Smooth(0, 12);

    protected PanelScene(App app, String title, int accent) {
        super(app);
        this.title = title;
        this.accent = accent;
        back = ui.add(new Button("", Icon.BACK, Theme.CYAN, () -> {
            if (!back()) {
                app.goTo(new MenuScene(app));
            }
        }));
    }

    // ----------------------------------------------------------------------------------------- Aufbau

    /** Legt die Reiter fest (leer = keine). */
    protected void setTabs(String... labels) {
        for (Button b : tabButtons) {
            ui.buttons.remove(b);
        }
        tabButtons.clear();
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            Button b = new Button(labels[i], null, accent, () -> selectTab(idx));
            b.fontScale = 0.9;
            tabButtons.add(ui.add(b));
        }
        tab = 0;
        refreshTabs();
    }

    protected void selectTab(int i) {
        if (i == tab) {
            return;
        }
        tab = i;
        scroll = 0;
        velocity = 0;
        refreshTabs();
        onTab(i);
    }

    protected void onTab(int i) {
    }

    private void refreshTabs() {
        for (int i = 0; i < tabButtons.size(); i++) {
            tabButtons.get(i).selected = i == tab;
        }
    }

    /** Reiter-Beschriftung nachträglich ändern (z. B. mit Hinweispunkt). */
    protected void setTabLabel(int i, String label) {
        if (i >= 0 && i < tabButtons.size()) {
            tabButtons.get(i).label = label;
        }
    }

    // ---------------------------------------------------------------------------------------- Layout

    @Override
    public void layout() {
        Viewport vp = app.vp;
        double u = vp.u;
        double m = 14 * u;
        double bs = 46 * u;
        back.bounds(vp.insetL + m, vp.insetT + m, bs, bs);
        titleY = vp.insetT + m + bs / 2;
        double y = vp.insetT + m + bs + m * 0.7;
        contentX = vp.insetL + m;
        contentW = Math.min(vp.safeW() - 2 * m, 980 * u);
        contentX = vp.insetL + (vp.safeW() - contentW) / 2;
        chipRight = vp.w - vp.insetR - m;
        if (!tabButtons.isEmpty()) {
            double th = 38 * u;
            double gap = 8 * u;
            double tw = (contentW - gap * (tabButtons.size() - 1)) / tabButtons.size();
            for (int i = 0; i < tabButtons.size(); i++) {
                tabButtons.get(i).bounds(contentX + i * (tw + gap), y, tw, th);
            }
            y += th + m * 0.7;
        }
        contentTop = y;
        contentBottom = vp.h - vp.insetB - m * 0.5;
        layoutModal();
    }

    private void layoutModal() {
        Viewport vp = app.vp;
        double u = vp.u;
        double bw = Math.min(vp.safeW() * 0.8, 340 * u);
        double bh = 52 * u;
        double y0 = vp.h / 2 + (modalLines.size() * 20 * u) / 2 + 18 * u;
        for (int i = 0; i < modalUi.buttons.size(); i++) {
            modalUi.buttons.get(i).bounds(vp.w / 2 - bw / 2, y0 + i * (bh + 10 * u), bw, bh);
        }
    }

    // ------------------------------------------------------------------------------------- Hinweise

    protected void toast(String msg, int color) {
        toast = msg;
        toastColor = color;
        toastTime = 2.6;
    }

    /**
     * Zeigt einen Dialog mit Text und zwei Schaltflächen. {@code ok} läuft nur bei Bestätigung.
     */
    protected void confirm(String heading, int color, String[] lines, String okLabel, Runnable ok) {
        modalTitle = heading;
        modalColor = color;
        modalLines.clear();
        for (String l : lines) {
            modalLines.add(l);
        }
        modalUi.clear();
        Button yes = new Button(okLabel, null, color, () -> {
            modalOpen = false;
            if (ok != null) {
                ok.run();
            }
        });
        yes.neonFont = true;
        yes.fontScale = 0.8;
        Button no = new Button("ABBRECHEN", Icon.CROSS, Theme.TEXT_DIM, () -> modalOpen = false);
        no.neonFont = true;
        no.fontScale = 0.8;
        modalUi.add(yes).appearAfter(0.05);
        modalUi.add(no).appearAfter(0.1);
        modalOpen = true;
        layoutModal();
    }

    /** Wie {@link #confirm}, aber nur mit einer OK-Schaltfläche. */
    protected void inform(String heading, int color, String[] lines) {
        modalTitle = heading;
        modalColor = color;
        modalLines.clear();
        for (String l : lines) {
            modalLines.add(l);
        }
        modalUi.clear();
        Button ok = new Button("OK", Icon.CHECK, color, () -> modalOpen = false);
        ok.neonFont = true;
        ok.fontScale = 0.8;
        modalUi.add(ok).appearAfter(0.05);
        modalOpen = true;
        layoutModal();
    }

    protected boolean modalOpen() {
        return modalOpen;
    }

    /** Mitte der {@code i}. Schaltfläche des offenen Dialogs (für Tests) oder {@code null}. */
    double[] modalAnchor(int i) {
        if (!modalOpen || i >= modalUi.buttons.size()) {
            return null;
        }
        Button b = modalUi.buttons.get(i);
        return new double[] {b.cx(), b.cy()};
    }

    boolean modalVisible() {
        return modalOpen;
    }

    // ------------------------------------------------------------------------------------- Zeichnen

    /**
     * Zeichnet den Inhalt ab der Höhe {@code y} (schon um das Scrollen verschoben) in der Breite {@code w}.
     *
     * @return die gesamte Höhe des Inhalts
     */
    protected abstract double drawContent(Gfx g, double x, double y, double w, double u);

    /** Meldet eine antippbare Fläche (nur sichtbare Teile zählen). */
    protected final void hit(double x, double y, double w, double h, Object tag) {
        if (y + h < contentTop || y > contentBottom) {
            return;
        }
        Hit hh = new Hit();
        hh.x = x;
        hh.y = Math.max(y, contentTop);
        hh.w = w;
        hh.h = Math.min(y + h, contentBottom) - hh.y;
        hh.tag = tag;
        hits.add(hh);
    }

    /** Ist {@code tag} gerade gedrückt (für Hervorhebung)? */
    protected final boolean pressed(Object tag) {
        return tag != null && tag.equals(pressedTag);
    }

    protected final boolean hovered(Object tag) {
        return tag != null && tag.equals(hoverTag);
    }

    protected final double scrollOffset() {
        return scroll;
    }

    @Override
    public void update(double dt) {
        time += dt;
        ui.update(dt);
        modalUi.update(dt);
        modalIn.target = modalOpen ? 1 : 0;
        modalIn.update(dt);
        if (!scrolling && pressedTag == null && Math.abs(velocity) > 5) {
            scroll = Mathx.clamp(scroll - velocity * dt, 0, scrollMax);
            velocity *= Math.exp(-4.5 * dt);
            if (scroll <= 0 || scroll >= scrollMax) {
                velocity = 0;
            }
        }
        if (toastTime > 0) {
            toastTime -= dt;
        }
        tick(dt);
    }

    /** Für Unterklassen: zusätzliche Animationen. */
    protected void tick(double dt) {
    }

    @Override
    public void render(Gfx g) {
        Viewport vp = app.vp;
        double u = vp.u;
        g.fillRect(0, 0, vp.w, vp.h, Theme.BLACK);
        drawBackdrop(g, vp);

        hits.clear();
        g.save();
        g.clipRect(0, contentTop - 4 * u, vp.w, contentBottom - contentTop + 8 * u);
        double h = drawContent(g, contentX, contentTop - scroll, contentW, u);
        g.restore();
        scrollMax = Math.max(0, h - (contentBottom - contentTop));
        scroll = Mathx.clamp(scroll, 0, scrollMax);
        drawScrollBar(g, u);

        String shown = title;
        double titleW = vp.safeW() - 2 * (46 * u + 28 * u) - 150 * u;
        NeonText.draw(g, shown, vp.w / 2, titleY, Math.min(30 * u, titleW / Math.max(4, shown.length() * 0.85)),
                accent, 8, 0.4, time);
        drawCrystalChip(g, u);
        ui.render(g);

        if (toastTime > 0 && toast != null) {
            double a = Mathx.clamp01(toastTime / 0.4);
            double fs = 15 * u;
            double w = Math.min(vp.safeW() - 24 * u, g.textWidth(toast, fs, true) + 34 * u);
            double hh = 34 * u;
            g.save();
            g.alpha(a);
            g.fillRoundRect(vp.w / 2 - w / 2, vp.h - vp.insetB - hh - 16 * u, w, hh, hh / 2, Colors.withAlpha(0x000000, 0.92));
            Neon.roundRect(g, vp.w / 2 - w / 2, vp.h - vp.insetB - hh - 16 * u, w, hh, hh / 2, 1.6, toastColor, 5);
            g.text(toast, vp.w / 2, vp.h - vp.insetB - hh / 2 - 16 * u, fs, Theme.TEXT, Gfx.ALIGN_CENTER, true);
            g.restore();
        }
        if (modalIn.value > 0.01) {
            drawModal(g, u);
        }
    }

    private void drawBackdrop(Gfx g, Viewport vp) {
        g.beginPath();
        double step = 56 * vp.u;
        double off = (time * 6) % step;
        for (double x = -off; x < vp.w; x += step) {
            g.moveTo(x, 0);
            g.lineTo(x, vp.h);
        }
        for (double y = -off; y < vp.h; y += step) {
            g.moveTo(0, y);
            g.lineTo(vp.w, y);
        }
        g.stroke(1, Colors.withAlpha(Theme.GRID, 0.55));
    }

    private void drawScrollBar(Gfx g, double u) {
        if (scrollMax <= 0) {
            return;
        }
        double trackH = contentBottom - contentTop;
        double barH = Math.max(30 * u, trackH * trackH / (trackH + scrollMax));
        double y = contentTop + (trackH - barH) * (scroll / scrollMax);
        g.fillRoundRect(app.vp.w - app.vp.insetR - 6 * u, y, 3 * u, barH, 2 * u, Colors.withAlpha(accent, 0.45));
    }

    /** Kristall-Guthaben oben rechts. */
    protected void drawCrystalChip(Gfx g, double u) {
        String text = Fmt.compact(app.progress.crystals());
        double fs = 15 * u;
        double w = g.textWidth(text, fs, true) + 44 * u;
        double h = 32 * u;
        double x = chipRight - w;
        double y = titleY - h / 2;
        g.fillRoundRect(x, y, w, h, h / 2, Colors.withAlpha(Theme.PANEL, 0.95));
        Neon.roundRect(g, x, y, w, h, h / 2, 1.6, Colors.withAlpha(Theme.CRYSTAL, 0.9), 4);
        Icons.draw(g, Icon.CRYSTAL, x + h * 0.55, titleY, h * 0.3, Theme.CRYSTAL, 3);
        g.text(text, x + h * 0.95, titleY, fs, Theme.TEXT, Gfx.ALIGN_LEFT, true);
    }

    private void drawModal(Gfx g, double u) {
        Viewport vp = app.vp;
        double a = Easing.outCubic(Mathx.clamp01(modalIn.value));
        g.save();
        g.alpha(a);
        g.fillRect(0, 0, vp.w, vp.h, Colors.withAlpha(0x000000, 0.84));
        double top = vp.h / 2 - (modalLines.size() * 20 * u) / 2 - 56 * u;
        NeonText.draw(g, modalTitle, vp.w / 2, top, Math.min(32 * u, vp.safeW() * 0.84 / Math.max(6, modalTitle.length() * 0.8)),
                modalColor, 10, 0.3, time);
        double y = top + 38 * u;
        for (String l : modalLines) {
            double fs = Math.max(11, 15 * u);
            double w = g.textWidth(l, fs, true);
            double k = Math.min(1, (vp.safeW() - 30 * u) / Math.max(1, w));
            g.text(l, vp.w / 2, y, fs * k, Theme.TEXT, Gfx.ALIGN_CENTER, true);
            y += 20 * u;
        }
        modalUi.render(g);
        g.restore();
    }

    // -------------------------------------------------------------------------------------- Eingabe

    private Object hitAt(double x, double y) {
        if (y < contentTop || y > contentBottom) {
            return null;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (x >= h.x && x <= h.x + h.w && y >= h.y && y <= h.y + h.h) {
                return h.tag;
            }
        }
        return null;
    }

    @Override
    public void pointerDown(double x, double y, boolean touch) {
        if (modalOpen) {
            modalUi.pointerDown(x, y);
            return;
        }
        if (ui.pointerDown(x, y)) {
            return;
        }
        downY = y;
        lastY = y;
        lastT = time;
        scrolling = false;
        velocity = 0;
        pressedTag = hitAt(x, y);
    }

    @Override
    public void pointerMove(double x, double y, boolean pressed, boolean touch) {
        if (modalOpen) {
            modalUi.pointerMove(x, y, !touch);
            return;
        }
        ui.pointerMove(x, y, !touch);
        if (!pressed) {
            hoverTag = touch ? null : hitAt(x, y);
            return;
        }
        if (ui.isPressing()) {
            return;
        }
        double u = app.vp.u;
        if (!scrolling && Math.abs(y - downY) > 9 * u) {
            scrolling = true;
            pressedTag = null;
        }
        if (scrolling) {
            double dy = y - lastY;
            scroll = Mathx.clamp(scroll - dy, 0, scrollMax);
            double dt = Math.max(1.0 / 240, time - lastT);
            velocity = Mathx.lerp(velocity, dy / dt, 0.4);
            lastY = y;
            lastT = time;
        }
    }

    @Override
    public void pointerUp(double x, double y, boolean touch) {
        if (modalOpen) {
            modalUi.pointerUp(x, y);
            return;
        }
        if (ui.pointerUp(x, y)) {
            return;
        }
        Object tag = pressedTag;
        pressedTag = null;
        if (tag != null && !scrolling && tag.equals(hitAt(x, y))) {
            onTap(tag);
        }
        scrolling = false;
        if (touch) {
            hoverTag = null;
        }
    }

    @Override
    public void pointerCancel() {
        ui.cancel();
        modalUi.cancel();
        pressedTag = null;
        scrolling = false;
    }

    @Override
    public void wheel(double dy) {
        if (!modalOpen) {
            scroll = Mathx.clamp(scroll + dy, 0, scrollMax);
        }
    }

    protected void onTap(Object tag) {
    }

    @Override
    public boolean back() {
        if (modalOpen) {
            modalOpen = false;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------------------- Zeichenhelfer

    /** Karte mit leuchtendem Rand; {@code hv} 0..1 hebt sie hervor. */
    protected void card(Gfx g, double x, double y, double w, double h, int color, double hv, boolean dim) {
        double r = Math.min(18, h * 0.2);
        g.fillRoundRect(x, y, w, h, r, Colors.withAlpha(Theme.PANEL, 0.95));
        g.fillRoundRect(x, y, w, h, r, Colors.withAlpha(color, 0.04 + 0.1 * hv));
        Neon.roundRect(g, x, y, w, h, r, 1.8, Colors.withAlpha(color, dim ? 0.28 : 0.9), dim ? 0 : 3 + 7 * hv);
    }

    /** Fortschrittsbalken. */
    protected void bar(Gfx g, double x, double y, double w, double h, double f, int color) {
        g.fillRoundRect(x, y, w, h, h / 2, Colors.withAlpha(0xFFFFFF, 0.1));
        if (f > 0) {
            g.fillRoundRect(x, y, Math.max(h, w * Mathx.clamp01(f)), h, h / 2, color);
        }
    }

    /** Text, der bei Bedarf verkleinert wird, damit er in {@code maxW} passt. */
    protected void fitText(Gfx g, String s, double x, double y, double size, double maxW, int color, int align,
                           boolean bold) {
        double w = g.textWidth(s, size, bold);
        double k = w > maxW ? maxW / Math.max(1, w) : 1;
        g.text(s, x, y, Math.max(8, size * k), color, align, bold);
    }

    /** Einfacher Zeilenumbruch auf höchstens {@code maxW} Breite. */
    protected List<String> wrap(Gfx g, String s, double size, double maxW) {
        List<String> out = new ArrayList<>();
        String[] words = s.split(" ");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String t = line.length() == 0 ? w : line + " " + w;
            if (g.textWidth(t, size, false) > maxW && line.length() > 0) {
                out.add(line.toString());
                line.setLength(0);
                line.append(w);
            } else {
                line.setLength(0);
                line.append(t);
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }
}

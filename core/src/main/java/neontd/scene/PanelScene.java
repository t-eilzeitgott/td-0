package neontd.scene;

import java.util.ArrayList;
import java.util.List;
import neontd.app.App;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Icons.Icon;
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
public abstract class PanelScene extends Scene {
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
        back = ui.add(new Button("", Icon.BACK, Theme.TEXT, () -> {
            if (!back()) {
                app.goTo(new MenuScene(app));
            }
        }));
        back.painter = (g, b) -> {
            double r = Math.min(b.w, b.h) / 2;
            g.fillCircle(0, 0, r, Colors.lerp(Theme.SURFACE2, Theme.SURFACE3, Math.max(b.hover.value, b.press.value)));
            neontd.ui.Draw.chevron(g, 1, 0, r * 0.3, Theme.TEXT, false);
        };
    }

    /** Hintergrund der Seite: pures Schwarz (Unterklassen können etwas ergänzen). */
    protected void drawBackdrop(Gfx g, Viewport vp) {
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
            b.painter = this::paintTab;
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

    /** Reiter als Segment: gewählt = helle Fläche, sonst nur Text. */
    private void paintTab(Gfx g, Button b) {
        double w = b.w;
        double h = b.h;
        double r = h * 0.4;
        if (b.selected) {
            g.fillRoundRect(-w / 2, -h / 2, w, h, r, Theme.SURFACE3);
        } else if (b.hover.value > 0 || b.press.value > 0) {
            g.fillRoundRect(-w / 2, -h / 2, w, h, r, Colors.withAlpha(0xFFFFFF, 0.04 * b.hover.value + 0.06 * b.press.value));
        }
        double fs = h * 0.36;
        String label = b.label;
        boolean dot = label.endsWith(" ●");
        if (dot) {
            label = label.substring(0, label.length() - 2);
        }
        double tw = g.textWidth(label, fs, true);
        double k = tw > w * 0.84 ? w * 0.84 / tw : 1;
        g.text(label, 0, 0, fs * k, b.selected ? Theme.TEXT : Theme.MUTED, Gfx.ALIGN_CENTER, true);
        if (dot) {
            g.fillCircle(tw * k / 2 + h * 0.22, -h * 0.18, h * 0.08, Theme.RED);
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
        double m = 16 * u;
        double bs = 40 * u;
        contentW = Math.min(vp.safeW() - 2 * m, 720 * u);
        contentX = vp.insetL + (vp.safeW() - contentW) / 2;
        back.bounds(contentX, vp.insetT + m, bs, bs);
        titleY = vp.insetT + m + bs / 2;
        double y = vp.insetT + m + bs + m * 0.9;
        chipRight = contentX + contentW;
        if (!tabButtons.isEmpty()) {
            double th = 36 * u;
            double gap = 3 * u;
            double tw = (contentW - gap * (tabButtons.size() - 1) - 2 * gap) / tabButtons.size();
            for (int i = 0; i < tabButtons.size(); i++) {
                tabButtons.get(i).bounds(contentX + gap + i * (tw + gap), y + gap, tw, th);
            }
            y += th + 2 * gap + m * 0.9;
        }
        contentTop = y;
        contentBottom = vp.h - vp.insetB - m * 0.5;
        layoutModal();
    }

    private void layoutModal() {
        Viewport vp = app.vp;
        double u = vp.u;
        double sw = Math.min(vp.safeW() - 24 * u, 440 * u);
        double bw = sw - 32 * u;
        double bh = 50 * u;
        int n = modalUi.buttons.size();
        double bottom = vp.h - vp.insetB - 16 * u - 16 * u;
        for (int i = 0; i < n; i++) {
            double y = bottom - (n - i) * (bh + 10 * u) + 10 * u;
            modalUi.buttons.get(i).bounds(vp.w / 2 - bw / 2, y, bw, bh);
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
        yes.style = Button.Style.PRIMARY;
        Button no = new Button("Abbrechen", null, Theme.TEXT, () -> modalOpen = false);
        modalUi.add(yes).appearAfter(0.05);
        modalUi.add(no).appearAfter(0.1);
        modalOpen = true;
        layoutModal();
    }

    /**
     * Ein Blatt von unten mit Überschrift, Zeilen und beliebigen Schaltflächen; jede schließt das Blatt, bevor ihre
     * Aktion läuft. Die erste Schaltfläche ist die Haupttaste.
     */
    protected void sheet(String heading, int color, String[] lines, Button... buttons) {
        modalTitle = heading;
        modalColor = color;
        modalLines.clear();
        for (String l : lines) {
            modalLines.add(l);
        }
        modalUi.clear();
        for (int i = 0; i < buttons.length; i++) {
            Button b = buttons[i];
            final Runnable action = b.onClick;
            b.onClick = () -> {
                modalOpen = false;
                if (action != null) {
                    action.run();
                }
            };
            if (i == 0) {
                b.style = Button.Style.PRIMARY;
            }
            modalUi.add(b).appearAfter(0.04 * i);
        }
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
        Button ok = new Button("OK", null, color, () -> modalOpen = false);
        ok.style = Button.Style.PRIMARY;
        modalUi.add(ok).appearAfter(0.05);
        modalOpen = true;
        layoutModal();
    }

    protected boolean modalOpen() {
        return modalOpen;
    }

    /** Mitte der {@code i}. Schaltfläche des offenen Dialogs (für Tests) oder {@code null}. */
    public double[] modalAnchor(int i) {
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

        // Kopf: Zurück, Titel links, Kristalle rechts (die Inhalte scrollen darunter hindurch)
        double headBottom = contentTop - 6 * u;
        g.fillRect(0, 0, vp.w, headBottom, Theme.BG);
        if (!tabButtons.isEmpty()) {
            Button t0 = tabButtons.get(0);
            Button tn = tabButtons.get(tabButtons.size() - 1);
            g.fillRoundRect(contentX, t0.y - 3 * u, contentW, t0.h + 6 * u, (t0.h + 6 * u) * 0.4, Theme.SURFACE);
        }
        double tx = back.x + back.w + 12 * u;
        double titleMax = chipRight - 110 * u - tx;
        neontd.ui.Draw.fit(g, title, tx, titleY, 24 * u, titleMax, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        drawCrystalChip(g, u);
        ui.render(g);

        if (toastTime > 0 && toast != null) {
            double a = Mathx.clamp01(toastTime / 0.4);
            double fs = 15 * u;
            double w = Math.min(vp.safeW() - 24 * u, g.textWidth(toast, fs, true) + 52 * u);
            double hh = 36 * u;
            g.save();
            g.alpha(a);
            double ty = vp.h - vp.insetB - hh - 16 * u;
            g.fillRoundRect(vp.w / 2 - w / 2, ty, w, hh, hh / 2, Theme.SURFACE3);
            g.fillCircle(vp.w / 2 - w / 2 + hh * 0.5, ty + hh / 2, hh * 0.12, toastColor);
            g.text(toast, vp.w / 2 + hh * 0.25, ty + hh / 2, fs, Theme.TEXT, Gfx.ALIGN_CENTER, true);
            g.restore();
        }
        if (modalIn.value > 0.01) {
            drawModal(g, u);
        }
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
        neontd.ui.Draw.chipRight(g, chipRight, titleY - 16 * u, 32 * u, Icon.CRYSTAL, Fmt.compact(app.progress.crystals()),
                Theme.CRYSTAL, true);
    }

    private void drawModal(Gfx g, double u) {
        Viewport vp = app.vp;
        double a = Easing.outCubic(Mathx.clamp01(modalIn.value));
        g.save();
        g.alpha(a);
        g.fillRect(0, 0, vp.w, vp.h, Colors.withAlpha(0x000000, 0.6));
        g.restore();
        double sw = Math.min(vp.safeW() - 24 * u, 440 * u);
        int n = modalUi.buttons.size();
        double textH = modalLines.size() * 21 * u;
        double sh = 24 * u + 30 * u + textH + 18 * u + n * (50 * u + 10 * u) + 16 * u;
        double sy = vp.h - vp.insetB - 16 * u - sh + (1 - a) * 40 * u;
        g.save();
        g.alpha(a);
        g.fillRoundRect(vp.w / 2 - sw / 2, sy, sw, sh, 22 * u, Theme.SURFACE);
        g.strokeRoundRect(vp.w / 2 - sw / 2, sy, sw, sh, 22 * u, 1, Theme.LINE);
        double y = sy + 24 * u + 10 * u;
        neontd.ui.Draw.fit(g, modalTitle, vp.w / 2 - sw / 2 + 20 * u, y, 20 * u, sw - 40 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        y += 30 * u;
        for (String l : modalLines) {
            neontd.ui.Draw.fit(g, l, vp.w / 2 - sw / 2 + 20 * u, y, 14 * u, sw - 40 * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
            y += 21 * u;
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
        double r = Math.min(18 * app.vp.u, h * 0.24);
        neontd.ui.Draw.card(g, x, y, w, h, r, dim ? 0 : hv);
        if (!dim && color != Theme.TEXT) {
            g.fillRoundRect(x, y, w, h, r, Colors.withAlpha(color, 0.035 + 0.03 * hv));
        }
    }

    /** Fortschrittsbalken. */
    protected void bar(Gfx g, double x, double y, double w, double h, double f, int color) {
        neontd.ui.Draw.bar(g, x, y, w, h, f, color);
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

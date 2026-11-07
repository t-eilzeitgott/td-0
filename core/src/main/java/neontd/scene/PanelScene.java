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
    private double tabsY;
    private double tabsH;
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
            double k = Math.max(b.hover.value, b.press.value);
            if (k > 0) {
                g.fillCircle(0, 0, r, Colors.withAlpha(0xFFFFFF, 0.08 * k));
            }
            neontd.ui.Draw.chevron(g, -1, 0, r * 0.36, Theme.TEXT, false);
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
        tabSel.target = 0;
        tabSel.value = 0;
        refreshTabs();
    }

    protected void selectTab(int i) {
        if (i == tab) {
            return;
        }
        tab = i;
        tabSel.target = i;
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

    /** Reiter werden gemeinsam von {@link neontd.ui.Kit#tabs} gezeichnet; die Schaltflächen sind nur Trefferflächen. */
    private void paintTab(Gfx g, Button b) {
    }

    private final Smooth tabSel = new Smooth(0, 14);

    private void paintTabOld(Gfx g, Button b) {
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
        double m = neontd.ui.Kit.MARGIN * u;
        double bs = 44 * u;
        contentW = Math.min(vp.safeW() - 2 * m, 640 * u);
        contentX = vp.insetL + (vp.safeW() - contentW) / 2;
        double top = vp.insetT + 10 * u;
        back.bounds(contentX - 12 * u, top, bs, bs);
        titleY = top + bs / 2;
        double y = top + bs + 14 * u;
        chipRight = contentX + contentW;
        if (!tabButtons.isEmpty()) {
            double th = 44 * u;
            double tw = contentW / tabButtons.size();
            for (int i = 0; i < tabButtons.size(); i++) {
                tabButtons.get(i).bounds(contentX + i * tw, y, tw, th);
            }
            tabsY = y;
            tabsH = th;
            y += th + 18 * u;
        }
        contentTop = y;
        contentBottom = vp.h - vp.insetB - 8 * u;
        layoutModal();
    }

    private void layoutModal() {
        Viewport vp = app.vp;
        double u = vp.u;
        double sw = Math.min(vp.w, 520 * u);
        double bw = Math.min(sw, vp.safeW()) - 2 * neontd.ui.Kit.MARGIN * u;
        double bh = neontd.ui.Kit.BUTTON * u;
        int n = modalUi.buttons.size();
        double bottom = vp.h - Math.max(vp.insetB, 8 * u) - 18 * u;
        for (int i = 0; i < n; i++) {
            Button b = modalUi.buttons.get(i);
            if (i > 0 && b.style == Button.Style.SECONDARY) {
                b.style = Button.Style.TEXT;
            }
            double y = bottom - (n - i) * (bh + 8 * u) + 8 * u;
            b.bounds(vp.w / 2 - bw / 2, y, bw, bh);
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

    /** Mitte der zuletzt gezeichneten Trefferfläche mit {@code tag} (für Tests) oder {@code null}. */
    public double[] hitAnchor(Object tag) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.tag != null && h.tag.equals(tag)) {
                return new double[] {h.x + h.w / 2, h.y + h.h / 2};
            }
        }
        return null;
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
        tabSel.update(dt);
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
        if (scrollMax > 0 && scroll < scrollMax - 1) {
            // weicher Auslauf am unteren Rand, damit nichts hart abgeschnitten wirkt
            double fh = 36 * u;
            for (int i = 0; i < 12; i++) {
                double t = (i + 1) / 12.0;
                g.fillRect(0, contentBottom - fh + fh * i / 12.0, vp.w, fh / 12.0 + 1, Colors.withAlpha(0x000000, 0.9 * t * t));
            }
        }
        drawScrollBar(g, u);

        // Kopf: Zurück, großer Titel, Kristalle rechts (die Inhalte scrollen darunter hindurch, mit weichem Verlauf)
        double headBottom = contentTop - 4 * u;
        g.fillRect(0, 0, vp.w, headBottom, Theme.BG);
        for (int i = 0; i < 6; i++) {
            g.fillRect(0, headBottom + i * 2 * u, vp.w, 2 * u, Colors.withAlpha(0x000000, 0.85 * (1 - i / 6.0)));
        }
        if (scroll > 2 && tabButtons.isEmpty()) {
            g.fillRect(0, headBottom, vp.w, 1, Colors.withAlpha(0xFFFFFF, 0.06 * Mathx.clamp01(scroll / 30)));
        }
        if (!tabButtons.isEmpty()) {
            String[] labels = new String[tabButtons.size()];
            for (int i = 0; i < labels.length; i++) {
                labels[i] = tabButtons.get(i).label;
            }
            neontd.ui.Kit.tabs(g, labels, contentX, tabsY, contentW, tabsH, tabSel.value, accent, u);
        }
        double tx = back.visible ? back.x + back.w + 2 * u : contentX;
        double titleMax = chipRight - 96 * u - tx;
        neontd.ui.Draw.fit(g, title, tx, titleY + 1 * u, neontd.ui.Kit.T_TITLE * 0.9 * u, titleMax, Theme.TEXT,
                Gfx.ALIGN_LEFT, true);
        drawCrystalChip(g, u);
        ui.render(g);

        if (toastTime > 0 && toast != null) {
            double a = Mathx.clamp01(toastTime / 0.4);
            double fs = 16 * u;
            double w = Math.min(vp.safeW() - 24 * u, g.textWidth(toast, fs, true) + 60 * u);
            double hh = 44 * u;
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
        g.fillRoundRect(app.vp.w - app.vp.insetR - 5 * u, y, 2.5 * u, barH, 1.5 * u, Colors.withAlpha(0xFFFFFF, 0.22));
    }

    /** Kristall-Guthaben oben rechts. */
    protected void drawCrystalChip(Gfx g, double u) {
        neontd.ui.Kit.iconValueRight(g, chipRight, titleY, Icon.CRYSTAL, Fmt.compact(app.progress.crystals()),
                Theme.CRYSTAL, Theme.TEXT, 18 * u, u);
    }

    private void drawModal(Gfx g, double u) {
        Viewport vp = app.vp;
        double a = Easing.outCubic(Mathx.clamp01(modalIn.value));
        g.save();
        g.alpha(a);
        g.fillRect(0, 0, vp.w, vp.h, Colors.withAlpha(0x000000, 0.6));
        g.restore();
        double sw = Math.min(vp.w, 520 * u);
        int n = modalUi.buttons.size();
        double m = neontd.ui.Kit.MARGIN * u;
        double textH = modalLines.size() * 22 * u;
        double bottomPad = Math.max(vp.insetB, 8 * u) + 18 * u;
        double sh = 30 * u + 34 * u + textH + 22 * u + n * (neontd.ui.Kit.BUTTON * u + 8 * u) + bottomPad;
        double sy = vp.h - sh + (1 - a) * 60 * u;
        double sx = vp.w / 2 - sw / 2;
        g.save();
        g.alpha(a);
        // Blatt von unten: oben gerundet, unten bündig mit dem Bildschirmrand
        g.fillRoundRect(sx, sy, sw, sh + 40 * u, 28 * u, Theme.SURFACE);
        g.fillRoundRect(vp.w / 2 - 18 * u, sy + 8 * u, 36 * u, 4 * u, 2 * u, Colors.withAlpha(0xFFFFFF, 0.18));
        double y = sy + 30 * u + 14 * u;
        neontd.ui.Draw.fit(g, modalTitle, sx + m, y, 24 * u, sw - 2 * m, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        y += 34 * u;
        for (String l : modalLines) {
            neontd.ui.Draw.fit(g, l, sx + m, y, neontd.ui.Kit.T_BODY * u, sw - 2 * m, Theme.MUTED, Gfx.ALIGN_LEFT, false);
            y += 22 * u;
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
    public boolean swipeBack() {
        return !modalOpen;
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

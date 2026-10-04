package neontd.scene;

import java.util.ArrayList;
import neontd.app.App;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Icons.Icon;
import neontd.gfx.Neon;
import neontd.gfx.NeonText;
import neontd.gfx.Theme;
import neontd.level.LevelCodec;
import neontd.level.LevelDef;
import neontd.math.Mathx;
import neontd.physics.Collision;
import neontd.physics.Path;
import neontd.physics.Simplify;
import neontd.render.PathArt;
import neontd.render.WorldView;
import neontd.sim.World;
import neontd.ui.Button;
import neontd.ui.Easing;
import neontd.ui.Smooth;
import neontd.ui.Ui;
import neontd.ui.Viewport;

/**
 * Level-Editor: Pfade aus Kontrollpunkten bauen (Punkte setzen, ziehen, löschen) oder freihändig zeichnen.
 * Die Kurve wird live als glatter Spline mit der echten Spurbreite gezeigt. Bis zu drei Pfade, Rückgängig,
 * Probespielen und Speichern.
 */
public final class EditorScene extends Scene {
    private enum Tool { POINT, DRAW, ERASE }

    private static final int MAX_UNDO = 80;
    private static final double SNAP = 10;

    private final LevelDef level;
    private boolean saved;
    private boolean dirty;

    /** Kontrollpunkte je Pfad (x0,y0,x1,y1,… in Arrays exakter Länge). */
    private ArrayList<double[]> paths = new ArrayList<>();
    private int active;
    private Tool tool = Tool.POINT;
    private final ArrayList<ArrayList<double[]>> undo = new ArrayList<>();
    private final ArrayList<ArrayList<double[]>> redo = new ArrayList<>();
    private final Path[] built = new Path[LevelDef.MAX_PATHS];
    private String problem;
    private double totalLength;

    // Bedienung
    private final Ui ui = new Ui();
    private final Button[] toolBtns = new Button[3];
    private final Button[] chipBtns = new Button[LevelDef.MAX_PATHS + 1];
    private Button undoBtn;
    private Button redoBtn;
    private Button clearBtn;
    private Button testBtn;
    private Button saveBtn;
    private Button backBtn;
    private final Ui confirmUi = new Ui();
    private final Smooth confirmIn = new Smooth(0, 12);
    private boolean confirming;

    // Layout
    private double mapX;
    private double mapY;
    private double mapScale = 1;
    private double panelX;
    private double panelY;
    private double panelW;
    private double panelH;
    private double headerH;
    private boolean portrait;

    // Gestenzustand
    private boolean pressing;
    private int dragPath = -1;
    private int dragIndex = -1;
    private boolean dragMoved;
    private double pressX;
    private double pressY;
    private double[] stroke = new double[256];
    private int strokeN;
    private boolean drawing;
    private double hoverX = -1;
    private double hoverY = -1;
    private boolean hoverOnMap;
    private boolean touchMode;

    private double time;
    private String toast;
    private int toastColor;
    private double toastTime;
    private double hintTime = 6;

    /**
     * @param existing das zu bearbeitende Level oder {@code null} für ein neues
     */
    public EditorScene(App app, LevelDef existing) {
        super(app);
        this.level = existing == null ? new LevelDef("", "Neues Level") : existing.copy();
        for (double[] p : level.paths) {
            paths.add(p.clone());
        }
        if (paths.isEmpty()) {
            paths.add(new double[0]);
        }
        LevelDef stored = level.id.isEmpty() ? null : app.levels.load(level.id);
        saved = stored != null;
        dirty = stored == null ? existing != null && !level.paths.isEmpty() : !LevelCodec.encode(stored).equals(
                LevelCodec.encode(level));
        buildUi();
        refresh();
    }

    // ------------------------------------------------------------------------------------------ Aufbau

    private void buildUi() {
        String[] toolLabels = {"PUNKTE", "ZEICHNEN", "LÖSCHEN"};
        Icon[] toolIcons = {Icon.NODE, Icon.DRAW, Icon.TRASH};
        int[] toolColors = {Theme.CYAN, Theme.MAGENTA, Theme.RED};
        for (int i = 0; i < 3; i++) {
            final Tool t = Tool.values()[i];
            final int idx = i;
            Button b = new Button(toolLabels[i], toolIcons[i], toolColors[i], () -> setTool(t));
            b.painter = (g, btn) -> paintTool(g, btn, idx, toolLabels[idx], toolIcons[idx], toolColors[idx]);
            toolBtns[i] = ui.add(b);
        }
        for (int i = 0; i < chipBtns.length; i++) {
            final int idx = i;
            Button b = new Button("", null, Theme.YELLOW, () -> chipPressed(idx));
            b.painter = (g, btn) -> paintChip(g, btn, idx);
            chipBtns[i] = ui.add(b);
        }
        undoBtn = ui.add(new Button("", Icon.UNDO, Theme.TEXT_DIM, this::undo));
        redoBtn = ui.add(new Button("", Icon.UNDO, Theme.TEXT_DIM, this::redo));
        redoBtn.painter = (g, b) -> {
            g.save();
            g.scale(-1, 1);
            paintIconButton(g, b, Icon.UNDO);
            g.restore();
        };
        undoBtn.painter = (g, b) -> paintIconButton(g, b, Icon.UNDO);
        clearBtn = ui.add(new Button("", Icon.CROSS, Theme.RED, this::clearActive));
        clearBtn.painter = (g, b) -> paintIconButton(g, b, Icon.CROSS);
        testBtn = ui.add(new Button("TESTEN", Icon.PLAY, Theme.GREEN, this::testPlay));
        saveBtn = ui.add(new Button("SPEICHERN", Icon.SAVE, Theme.CYAN, () -> save(true)));
        backBtn = ui.add(new Button("ZURÜCK", Icon.BACK, Theme.TEXT_DIM, this::leave));
        testBtn.fontScale = 0.8;
        saveBtn.fontScale = 0.8;
        backBtn.fontScale = 0.8;
        undoBtn.color = Theme.CYAN;
        redoBtn.color = Theme.CYAN;
        for (int i = 0; i < ui.buttons.size(); i++) {
            ui.buttons.get(i).appearAfter(0.05 + i * 0.03);
        }
    }

    private void paintTool(Gfx g, Button b, int idx, String label, Icon icon, int color) {
        boolean sel = tool.ordinal() == idx;
        double w = b.w;
        double h = b.h;
        double hv = Math.max(b.hover.value, sel ? 1 : 0);
        g.fillRoundRect(-w / 2, -h / 2, w, h, h * 0.2, Colors.withAlpha(Theme.PANEL, 0.95));
        g.fillRoundRect(-w / 2, -h / 2, w, h, h * 0.2, Colors.withAlpha(color, (sel ? 0.16 : 0.03) + 0.1 * b.hover.value + 0.15 * b.press.value));
        Neon.roundRect(g, -w / 2, -h / 2, w, h, h * 0.2, sel ? 2.4 : 1.4, Colors.withAlpha(color, sel ? 1 : 0.55), sel ? 9 : 2 + 5 * hv);
        double is = Math.min(h * 0.27, w * 0.2);
        Icons.draw(g, icon, 0, -h * 0.13, is, color, sel ? 5 : 2);
        g.text(label, 0, h * 0.30, Math.max(8, Math.min(h * 0.19, w * 0.13)), sel ? Theme.TEXT : Theme.TEXT_DIM, Gfx.ALIGN_CENTER, true);
    }

    private void paintChip(Gfx g, Button b, int idx) {
        boolean isPlus = idx == chipBtns.length - 1;
        boolean exists = idx < paths.size();
        boolean sel = !isPlus && idx == active;
        double w = b.w;
        double h = b.h;
        int c = isPlus ? Theme.GREEN : Theme.YELLOW;
        double hv = Math.max(b.hover.value, sel ? 1 : 0);
        g.fillRoundRect(-w / 2, -h / 2, w, h, h * 0.3, Colors.withAlpha(Theme.PANEL, 0.95));
        g.fillRoundRect(-w / 2, -h / 2, w, h, h * 0.3, Colors.withAlpha(c, (sel ? 0.16 : 0.03) + 0.12 * b.press.value));
        double a = (exists || isPlus) ? 1 : 0.28;
        Neon.roundRect(g, -w / 2, -h / 2, w, h, h * 0.3, sel ? 2.4 : 1.4, Colors.withAlpha(c, (sel ? 1 : 0.6) * a), sel ? 8 : 2 + 4 * hv);
        if (isPlus) {
            Icons.draw(g, Icon.PLUS, 0, 0, h * 0.24, c, 3);
        } else {
            g.text(Integer.toString(idx + 1), 0, 0, h * 0.42, Colors.withAlpha(Theme.TEXT, a), Gfx.ALIGN_CENTER, true);
        }
    }

    private void paintIconButton(Gfx g, Button b, Icon icon) {
        double w = b.w;
        double h = b.h;
        double hv = b.hover.value;
        int c = b.color;
        boolean on = b.enabled;
        g.fillRoundRect(-w / 2, -h / 2, w, h, h * 0.25, Colors.withAlpha(Theme.PANEL, 0.95));
        g.fillRoundRect(-w / 2, -h / 2, w, h, h * 0.25, Colors.withAlpha(c, 0.03 + 0.1 * hv + 0.15 * b.press.value));
        Neon.roundRect(g, -w / 2, -h / 2, w, h, h * 0.25, 1.5, Colors.withAlpha(c, on ? 0.75 : 0.3), 2 + 5 * hv);
        Icons.draw(g, icon, 0, 0, Math.min(h * 0.3, w * 0.2), c, on ? 3 : 0);
    }

    // ---------------------------------------------------------------------------------------- Layout

    @Override
    public void layout() {
        Viewport vp = app.vp;
        double u = vp.u;
        double m = 8 * u;
        portrait = !vp.landscape();
        double mapAvX;
        double mapAvY;
        double mapAvW;
        double mapAvH;
        if (!portrait) {
            double pw = Mathx.clamp(vp.safeW() * 0.27, 215 * Math.min(1, u * 1.1), 420);
            panelX = vp.w - vp.insetR - m - pw;
            panelY = vp.insetT + m;
            panelW = pw;
            panelH = vp.safeH() - 2 * m;
            mapAvX = vp.insetL + m;
            mapAvY = vp.insetT + m;
            mapAvW = panelX - m - mapAvX;
            mapAvH = panelH;
        } else {
            mapAvX = vp.insetL + m;
            mapAvY = vp.insetT + m;
            mapAvW = vp.safeW() - 2 * m;
            mapAvH = mapAvW * level.height / level.width;
            panelX = mapAvX;
            panelY = mapAvY + mapAvH + m;
            panelW = mapAvW;
            panelH = vp.h - vp.insetB - m - panelY;
        }
        mapScale = Math.min(mapAvW / level.width, mapAvH / level.height);
        mapX = mapAvX + (mapAvW - level.width * mapScale) / 2;
        mapY = mapAvY + (mapAvH - level.height * mapScale) / 2;

        // Panel: Kopf, Werkzeuge, Pfad-Chips, Aktionen, Test/Speichern, Zurück
        double gap = 6 * u;
        double rows = 1.15 + 1.05 + 0.8 + 0.8 + 1 + 0.8;
        double rh = Math.min(54 * u, (panelH - gap * 5) / rows);
        double y = panelY;
        headerH = rh * 1.15;
        y += headerH + gap;
        double tw = (panelW - gap * 2) / 3;
        for (int i = 0; i < 3; i++) {
            toolBtns[i].bounds(panelX + i * (tw + gap), y, tw, rh * 1.05);
        }
        y += rh * 1.05 + gap;
        double cw = (panelW - gap * (chipBtns.length - 1)) / chipBtns.length;
        for (int i = 0; i < chipBtns.length; i++) {
            chipBtns[i].bounds(panelX + i * (cw + gap), y, cw, rh * 0.8);
        }
        y += rh * 0.8 + gap;
        undoBtn.bounds(panelX, y, tw, rh * 0.8);
        redoBtn.bounds(panelX + tw + gap, y, tw, rh * 0.8);
        clearBtn.bounds(panelX + 2 * (tw + gap), y, tw, rh * 0.8);
        y += rh * 0.8 + gap;
        double hw = (panelW - gap) / 2;
        testBtn.bounds(panelX, y, hw, rh);
        saveBtn.bounds(panelX + hw + gap, y, hw, rh);
        y += rh + gap;
        backBtn.bounds(panelX, y, panelW, rh * 0.8);
        layoutConfirm();
    }

    private void layoutConfirm() {
        Viewport vp = app.vp;
        double u = vp.u;
        double bw = Math.min(vp.safeW() * 0.84, 340 * u);
        double bh = 52 * u;
        for (int i = 0; i < confirmUi.buttons.size(); i++) {
            confirmUi.buttons.get(i).bounds(vp.w / 2 - bw / 2, vp.h / 2 - 10 * u + i * (bh + 10 * u), bw, bh);
        }
    }

    private double toWorldX(double sx) {
        return (sx - mapX) / mapScale;
    }

    private double toWorldY(double sy) {
        return (sy - mapY) / mapScale;
    }

    private boolean onMap(double sx, double sy) {
        return sx >= mapX && sy >= mapY && sx <= mapX + level.width * mapScale && sy <= mapY + level.height * mapScale;
    }

    // ----------------------------------------------------------------------------- Daten und Bearbeiten

    private ArrayList<double[]> snapshot() {
        ArrayList<double[]> copy = new ArrayList<>();
        for (double[] p : paths) {
            copy.add(p.clone());
        }
        return copy;
    }

    /** Muss vor jeder verändernden Geste aufgerufen werden. */
    private void pushUndo() {
        undo.add(snapshot());
        if (undo.size() > MAX_UNDO) {
            undo.remove(0);
        }
        redo.clear();
        dirty = true;
    }

    private void undo() {
        if (undo.isEmpty()) {
            return;
        }
        redo.add(snapshot());
        paths = undo.remove(undo.size() - 1);
        active = Math.min(active, paths.size() - 1);
        dirty = true;
        refresh();
    }

    private void redo() {
        if (redo.isEmpty()) {
            return;
        }
        undo.add(snapshot());
        paths = redo.remove(redo.size() - 1);
        active = Math.min(active, paths.size() - 1);
        dirty = true;
        refresh();
    }

    private int count(int p) {
        return paths.get(p).length / 2;
    }

    private void insertPoint(int p, int index, double x, double y) {
        double[] old = paths.get(p);
        double[] now = new double[old.length + 2];
        System.arraycopy(old, 0, now, 0, index * 2);
        now[index * 2] = x;
        now[index * 2 + 1] = y;
        System.arraycopy(old, index * 2, now, index * 2 + 2, old.length - index * 2);
        paths.set(p, now);
    }

    private void removePoint(int p, int index) {
        double[] old = paths.get(p);
        double[] now = new double[old.length - 2];
        System.arraycopy(old, 0, now, 0, index * 2);
        System.arraycopy(old, index * 2 + 2, now, index * 2, old.length - index * 2 - 2);
        paths.set(p, now);
    }

    private void setPoint(int p, int index, double x, double y) {
        double[] a = paths.get(p);
        a[index * 2] = Mathx.clamp(x, 0, level.width);
        a[index * 2 + 1] = Mathx.clamp(y, 0, level.height);
    }

    private static double snap(double v) {
        return Math.round(v / SNAP) * SNAP;
    }

    /** Aktualisiert Spline-Cache, Länge und Gültigkeitsmeldung. */
    private void refresh() {
        totalLength = 0;
        for (int i = 0; i < built.length; i++) {
            built[i] = null;
            if (i < paths.size()) {
                double[] pts = paths.get(i);
                int n = pts.length / 2;
                if (Path.distinctCount(pts, n) >= 2) {
                    built[i] = Path.fromControlPoints(pts, n, LevelDef.PATH_SAMPLE_STEP);
                    totalLength += built[i].length();
                }
            }
        }
        problem = currentLevel(false).validate();
    }

    /** Das Level aus dem aktuellen Bearbeitungsstand; leere Pfade bleiben nur bei {@code keepEmpty}. */
    private LevelDef currentLevel(boolean keepEmpty) {
        LevelDef l = level.copy();
        l.paths.clear();
        for (double[] p : paths) {
            if (p.length > 0 || keepEmpty) {
                l.paths.add(p.clone());
            }
        }
        return l;
    }

    private void setTool(Tool t) {
        tool = t;
        drawing = false;
        strokeN = 0;
    }

    private void chipPressed(int idx) {
        if (idx == chipBtns.length - 1) {
            if (paths.size() >= LevelDef.MAX_PATHS) {
                notify("Höchstens " + LevelDef.MAX_PATHS + " Pfade", Theme.RED);
            } else if (count(active) < 2) {
                notify("Beende erst den aktuellen Pfad (mindestens 2 Punkte)", Theme.RED);
            } else {
                pushUndo();
                paths.add(new double[0]);
                active = paths.size() - 1;
                refresh();
                notify("Neuer Pfad " + (active + 1), Theme.YELLOW);
            }
        } else if (idx < paths.size()) {
            active = idx;
        }
    }

    private void clearActive() {
        if (count(active) == 0 && paths.size() == 1) {
            return;
        }
        pushUndo();
        if (paths.size() > 1) {
            paths.remove(active);
            active = Math.max(0, active - 1);
        } else {
            paths.set(0, new double[0]);
        }
        refresh();
    }

    private void notify(String msg, int color) {
        toast = msg;
        toastColor = color;
        toastTime = 2.4;
    }

    private void testPlay() {
        refresh();
        if (problem != null) {
            notify(problem, Theme.RED);
            return;
        }
        LevelDef play = currentLevel(false);
        if (play.name.isEmpty()) {
            play.name = "Testlevel";
        }
        app.goTo(new GameScene(app, play, currentLevel(true)));
    }

    private boolean save(boolean announce) {
        LevelDef def = currentLevel(false);
        if (def.paths.isEmpty()) {
            notify("Zeichne zuerst einen Pfad", Theme.RED);
            return false;
        }
        if (level.id.isEmpty()) {
            LevelDef fresh = app.levels.createNew();
            level.id = fresh.id;
            level.name = fresh.name;
            def.id = fresh.id;
            def.name = fresh.name;
        }
        app.levels.save(def);
        saved = true;
        dirty = false;
        if (announce) {
            String p = def.validate();
            notify(p == null ? "Gespeichert – bereit zum Spielen" : "Gespeichert – noch nicht spielbar: " + p,
                    p == null ? Theme.GREEN : Theme.YELLOW);
        }
        return true;
    }

    private void leave() {
        if (dirty && !(count(active) == 0 && paths.size() == 1 && !saved)) {
            askLeave();
        } else {
            app.goTo(new LevelSelectScene(app));
        }
    }

    private void askLeave() {
        confirming = true;
        confirmUi.clear();
        Button s = new Button("SPEICHERN", Icon.SAVE, Theme.CYAN, () -> {
            if (save(false)) {
                app.goTo(new LevelSelectScene(app));
            }
            confirming = false;
        });
        Button d = new Button("VERWERFEN", Icon.TRASH, Theme.RED, () -> app.goTo(new LevelSelectScene(app)));
        Button c = new Button("WEITER BEARBEITEN", Icon.PENCIL, Theme.TEXT_DIM, () -> confirming = false);
        s.neonFont = true;
        d.neonFont = true;
        c.neonFont = true;
        confirmUi.add(s).appearAfter(0.08);
        confirmUi.add(d).appearAfter(0.16);
        confirmUi.add(c).appearAfter(0.24);
        layoutConfirm();
    }

    // ---------------------------------------------------------------------------------------- Update

    @Override
    public void update(double dt) {
        time += dt;
        ui.update(dt);
        confirmUi.update(dt);
        confirmIn.target = confirming ? 1 : 0;
        confirmIn.update(dt);
        if (toastTime > 0) {
            toastTime -= dt;
        }
        if (hintTime > 0 && count(active) > 0) {
            hintTime = Math.max(0, hintTime - dt * 3);
        } else if (hintTime > 0) {
            hintTime -= dt * 0.15;
        }
        undoBtn.enabled = !undo.isEmpty();
        redoBtn.enabled = !redo.isEmpty();
        testBtn.enabled = problem == null;
        saveBtn.enabled = dirty || !saved;
        for (int i = 0; i < chipBtns.length - 1; i++) {
            chipBtns[i].enabled = i < paths.size();
        }
    }

    // ----------------------------------------------------------------------------------------- Zeichnen

    @Override
    public void render(Gfx g) {
        Viewport vp = app.vp;
        double u = vp.u;
        g.fillRect(0, 0, vp.w, vp.h, Theme.BLACK);

        g.save();
        g.translate(mapX, mapY);
        g.scale(mapScale, mapScale);
        g.clipRect(0, 0, level.width, level.height);
        drawEditorGrid(g);
        for (int i = 0; i < paths.size(); i++) {
            if (built[i] != null) {
                PathArt.drawLane(g, built[i], time, i == active ? 1 : 0.45);
            }
        }
        if (built[active] != null) {
            PathArt.drawEndpoints(g, built[active], time, 0);
        }
        drawRubberBand(g);
        drawHandles(g);
        drawStroke(g);
        g.restore();
        g.save();
        g.translate(mapX, mapY);
        g.scale(mapScale, mapScale);
        WorldView.drawFrame(g, level.width, level.height);
        g.restore();

        drawHint(g, u);
        drawPanelHeader(g, u);
        ui.render(g);
        drawToast(g, u);
        if (confirmIn.value > 0.01) {
            drawConfirm(g, u);
        }
    }

    private void drawEditorGrid(Gfx g) {
        double w = level.width;
        double h = level.height;
        g.fillRect(0, 0, w, h, Theme.BLACK);
        g.beginPath();
        for (double x = 40; x < w; x += 40) {
            if (((int) Math.round(x / 40)) % 4 != 0) {
                g.moveTo(x, 0);
                g.lineTo(x, h);
            }
        }
        for (double y = 40; y < h; y += 40) {
            if (((int) Math.round(y / 40)) % 4 != 0) {
                g.moveTo(0, y);
                g.lineTo(w, y);
            }
        }
        g.stroke(1, Colors.withAlpha(Theme.GRID, 0.9));
        g.beginPath();
        for (double x = 160; x < w; x += 160) {
            g.moveTo(x, 0);
            g.lineTo(x, h);
        }
        for (double y = 160; y < h; y += 160) {
            g.moveTo(0, y);
            g.lineTo(w, y);
        }
        g.stroke(1.2, Colors.withAlpha(0x2A2A55, 0.9));
    }

    private double handleRadius() {
        return 11 * app.vp.u / mapScale * 0.8;
    }

    private void drawHandles(Gfx g) {
        double hr = handleRadius();
        for (int p = 0; p < paths.size(); p++) {
            double[] pts = paths.get(p);
            int n = pts.length / 2;
            boolean act = p == active;
            for (int i = 0; i < n; i++) {
                double x = pts[i * 2];
                double y = pts[i * 2 + 1];
                int col = i == 0 ? PathArt.SPAWN : (i == n - 1 && n > 1 ? PathArt.BASE : Theme.CYAN);
                boolean dragged = p == dragPath && i == dragIndex;
                boolean near = hoverOnMap && !touchMode && Mathx.dist(hoverX, hoverY, x, y) < hr * 1.6;
                double r = hr * (dragged ? 1.45 : (near ? 1.2 : 1));
                g.save();
                g.alpha(act ? 1 : 0.5);
                if (dragged || near) {
                    Neon.halo(g, x, y, r * 3.2, col, 0.5);
                }
                g.fillCircle(x, y, r, Colors.withAlpha(0x000000, 0.9));
                Neon.circle(g, x, y, r, 2.4 / mapScale * 0.75, col, 5 / mapScale * 0.75);
                g.fillCircle(x, y, r * 0.38, col);
                g.restore();
            }
        }
    }

    /** Gestrichelte Hilfslinie vom Pfadende zur Zeigerposition (nur Maus, Punkte-Werkzeug). */
    private void drawRubberBand(Gfx g) {
        if (tool != Tool.POINT || !hoverOnMap || touchMode || pressing) {
            return;
        }
        double[] pts = paths.get(active);
        int n = pts.length / 2;
        double hr = handleRadius();
        double x = Mathx.clamp(snap(hoverX), 0, level.width);
        double y = Mathx.clamp(snap(hoverY), 0, level.height);
        if (n > 0) {
            g.beginPath();
            g.moveTo(pts[(n - 1) * 2], pts[(n - 1) * 2 + 1]);
            g.lineTo(x, y);
            g.strokeDashed(2 / mapScale * 0.8, Colors.withAlpha(Theme.CYAN, 0.55), 8 / mapScale, 8 / mapScale, -time * 14);
        }
        g.strokeCircle(x, y, hr, 1.6 / mapScale * 0.8, Colors.withAlpha(Theme.CYAN, 0.6));
    }

    private void drawStroke(Gfx g) {
        if (!drawing || strokeN < 2) {
            return;
        }
        g.beginPath();
        g.moveTo(stroke[0], stroke[1]);
        for (int i = 1; i < strokeN; i++) {
            g.lineTo(stroke[i * 2], stroke[i * 2 + 1]);
        }
        Neon.stroke(g, 3 / mapScale * 0.8, Theme.MAGENTA, 8 / mapScale * 0.8);
    }

    private void drawHint(Gfx g, double u) {
        if (hintTime <= 0 || count(active) > 0 && count(active) >= 2) {
            return;
        }
        double a = Mathx.clamp01(hintTime) * 0.9;
        double cx = mapX + level.width * mapScale / 2;
        double cy = mapY + level.height * mapScale / 2;
        g.save();
        g.alpha(a);
        String l1 = tool == Tool.DRAW ? "ZEICHNE MIT DEM FINGER ODER DER MAUS" : "TIPPE AUF DIE KARTE";
        String l2 = tool == Tool.DRAW ? "Der Strich wird zu einem glatten Pfad" : "Setze Punkte – der Pfad verbindet sie glatt";
        double h = Math.min(30 * u, level.width * mapScale * 0.05);
        NeonText.draw(g, l1, cx, cy - h * 0.4, h, Theme.CYAN, 8, 0.2, time);
        g.text(l2, cx, cy + h * 0.9, Math.max(11, h * 0.5), Theme.TEXT_DIM, Gfx.ALIGN_CENTER, true);
        g.restore();
    }

    private void drawPanelHeader(Gfx g, double u) {
        double x = panelX;
        double y = panelY;
        double w = panelW;
        double h = headerH;
        boolean ok = problem == null;
        int accent = ok ? Theme.GREEN : Theme.YELLOW;
        g.fillRoundRect(x, y, w, h, h * 0.2, Colors.withAlpha(Theme.PANEL, 0.95));
        g.strokeRoundRect(x, y, w, h, h * 0.2, 1.4, Colors.withAlpha(accent, 0.6));
        String name = level.id.isEmpty() ? "Neues Level" : level.name;
        g.text(name + (dirty ? " *" : ""), x + h * 0.25, y + h * 0.3, Math.max(11, h * 0.3), Theme.TEXT, Gfx.ALIGN_LEFT, true);
        Icons.draw(g, ok ? Icon.CHECK : Icon.TARGET, x + h * 0.45, y + h * 0.72, h * 0.17, accent, 2);
        String status = ok ? "Spielbar · Länge " + (int) Math.round(totalLength) : problem;
        double size = Math.max(9, h * 0.22);
        double avail = w - h * 0.85;
        while (size > 8 && g.textWidth(status, size, false) > avail) {
            size -= 0.5;
        }
        g.text(status, x + h * 0.72, y + h * 0.72, size, ok ? Theme.TEXT_DIM : accent, Gfx.ALIGN_LEFT, false);
    }

    private void drawToast(Gfx g, double u) {
        if (toastTime <= 0 || toast == null) {
            return;
        }
        double a = Mathx.clamp01(toastTime / 0.4);
        double size = 14 * u;
        double w = Math.min(app.vp.safeW() - 20 * u, g.textWidth(toast, size, true) + 34 * u);
        double h = 34 * u;
        double cx = mapX + level.width * mapScale / 2;
        double y = mapY + level.height * mapScale - h - 12 * u;
        g.save();
        g.alpha(a);
        g.fillRoundRect(cx - w / 2, y, w, h, h / 2, Colors.withAlpha(0x000000, 0.9));
        Neon.roundRect(g, cx - w / 2, y, w, h, h / 2, 1.6, toastColor, 5);
        g.text(toast, cx, y + h / 2, size, Theme.TEXT, Gfx.ALIGN_CENTER, true);
        g.restore();
    }

    private void drawConfirm(Gfx g, double u) {
        Viewport vp = app.vp;
        double a = Easing.outCubic(Mathx.clamp01(confirmIn.value));
        g.save();
        g.alpha(a);
        g.fillRect(0, 0, vp.w, vp.h, Colors.withAlpha(0x000000, 0.82));
        NeonText.draw(g, "ÄNDERUNGEN?", vp.w / 2, vp.h / 2 - 90 * u, Math.min(36 * u, vp.safeW() * 0.84 / 8), Theme.YELLOW, 10, 0.3, time);
        g.text("Dein Level hat ungespeicherte Änderungen.", vp.w / 2, vp.h / 2 - 48 * u, Math.max(12, 14 * u), Theme.TEXT, Gfx.ALIGN_CENTER, true);
        confirmUi.render(g);
        g.restore();
    }

    // ------------------------------------------------------------------------------------------ Eingabe

    /** Index des nächstgelegenen Kontrollpunkts (aktiver Pfad bevorzugt) oder {-1,-1}. */
    private int[] findPoint(double wx, double wy, double radius) {
        int bestP = -1;
        int bestI = -1;
        double best = radius;
        for (int pass = 0; pass < 2; pass++) {
            for (int p = 0; p < paths.size(); p++) {
                if ((pass == 0) != (p == active)) {
                    continue;
                }
                double[] pts = paths.get(p);
                for (int i = 0; i < pts.length / 2; i++) {
                    double d = Mathx.dist(wx, wy, pts[i * 2], pts[i * 2 + 1]);
                    if (d < best) {
                        best = d;
                        bestP = p;
                        bestI = i;
                    }
                }
            }
            if (bestP >= 0) {
                break;
            }
        }
        return new int[] {bestP, bestI};
    }

    /** Index des Kontrollsegments (zwischen i und i+1) des aktiven Pfads nahe der Position oder -1. */
    private int segmentNear(double wx, double wy, double radius) {
        double[] pts = paths.get(active);
        int n = pts.length / 2;
        if (n < 2 || built[active] == null) {
            return -1;
        }
        if (built[active].distanceTo(wx, wy) > radius) {
            return -1;
        }
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < n - 1; i++) {
            double d = Collision.pointSegmentDistSq(wx, wy, pts[i * 2], pts[i * 2 + 1], pts[i * 2 + 2], pts[i * 2 + 3]);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    @Override
    public void pointerDown(double x, double y, boolean touch) {
        touchMode = touch;
        if (confirming) {
            confirmUi.pointerDown(x, y);
            return;
        }
        if (ui.pointerDown(x, y)) {
            return;
        }
        if (!onMap(x, y)) {
            return;
        }
        pressing = true;
        pressX = x;
        pressY = y;
        dragMoved = false;
        double wx = toWorldX(x);
        double wy = toWorldY(y);
        double hit = 22 * app.vp.u / mapScale * (touch ? 1.15 : 0.9);
        hintTime = 0;
        switch (tool) {
            case POINT: {
                int[] f = findPoint(wx, wy, hit);
                if (f[0] >= 0) {
                    active = f[0];
                    pushUndo();
                    dragPath = f[0];
                    dragIndex = f[1];
                    return;
                }
                if (paths.get(active).length / 2 >= 120) {
                    notify("Zu viele Punkte in diesem Pfad", Theme.RED);
                    pressing = false;
                    return;
                }
                pushUndo();
                int seg = segmentNear(wx, wy, hit * 0.75);
                int index = seg >= 0 ? seg + 1 : count(active);
                insertPoint(active, index, Mathx.clamp(snap(wx), 0, level.width), Mathx.clamp(snap(wy), 0, level.height));
                dragPath = active;
                dragIndex = index;
                refresh();
                return;
            }
            case ERASE: {
                int[] f = findPoint(wx, wy, hit);
                if (f[0] >= 0) {
                    active = f[0];
                    pushUndo();
                    removePoint(f[0], f[1]);
                    if (count(f[0]) == 0 && paths.size() > 1) {
                        paths.remove(f[0]);
                        active = Math.max(0, Math.min(active, paths.size() - 1));
                    }
                    refresh();
                }
                return;
            }
            case DRAW:
            default:
                drawing = true;
                strokeN = 0;
                addStrokePoint(wx, wy);
                return;
        }
    }

    private void addStrokePoint(double wx, double wy) {
        if (strokeN > 0) {
            double d = Mathx.dist(wx, wy, stroke[(strokeN - 1) * 2], stroke[(strokeN - 1) * 2 + 1]);
            if (d < 5) {
                return;
            }
        }
        if ((strokeN + 1) * 2 > stroke.length) {
            stroke = java.util.Arrays.copyOf(stroke, stroke.length * 2);
        }
        stroke[strokeN * 2] = Mathx.clamp(wx, 0, level.width);
        stroke[strokeN * 2 + 1] = Mathx.clamp(wy, 0, level.height);
        strokeN++;
    }

    @Override
    public void pointerMove(double x, double y, boolean pressed, boolean touch) {
        if (!pressed) {
            touchMode = touch;
        }
        if (confirming) {
            confirmUi.pointerMove(x, y, !touch);
            return;
        }
        ui.pointerMove(x, y, !touch);
        hoverOnMap = onMap(x, y);
        hoverX = toWorldX(x);
        hoverY = toWorldY(y);
        if (!pressing || !pressed) {
            return;
        }
        if (Mathx.dist(x, y, pressX, pressY) > 4) {
            dragMoved = true;
        }
        if (tool == Tool.POINT && dragPath >= 0) {
            setPoint(dragPath, dragIndex, snap(hoverX), snap(hoverY));
            refresh();
        } else if (tool == Tool.DRAW && drawing) {
            addStrokePoint(hoverX, hoverY);
        }
    }

    @Override
    public void pointerUp(double x, double y, boolean touch) {
        if (confirming) {
            confirmUi.pointerUp(x, y);
            return;
        }
        if (ui.pointerUp(x, y)) {
            return;
        }
        if (tool == Tool.DRAW && drawing) {
            finishStroke();
        }
        pressing = false;
        dragPath = -1;
        dragIndex = -1;
        if (touch) {
            hoverOnMap = false;
        }
    }

    private void finishStroke() {
        drawing = false;
        if (strokeN >= 3) {
            double[] simplified = new double[strokeN * 2];
            int n = Simplify.rdp(stroke, strokeN, 9, simplified);
            n = Simplify.enforceMinGap(simplified, n, 46);
            if (n >= 2) {
                pushUndo();
                double[] pts = new double[n * 2];
                for (int i = 0; i < n * 2; i += 2) {
                    pts[i] = Mathx.clamp(simplified[i], 0, level.width);
                    pts[i + 1] = Mathx.clamp(simplified[i + 1], 0, level.height);
                }
                paths.set(active, pts);
                refresh();
            }
        }
        strokeN = 0;
    }

    @Override
    public void pointerCancel() {
        ui.cancel();
        confirmUi.cancel();
        pressing = false;
        drawing = false;
        dragPath = -1;
        dragIndex = -1;
        strokeN = 0;
    }

    @Override
    public boolean key(String code, boolean ctrl) {
        if (confirming) {
            return false;
        }
        switch (code) {
            case "KeyZ":
                if (ctrl) {
                    undo();
                    return true;
                }
                return false;
            case "KeyY":
                if (ctrl) {
                    redo();
                    return true;
                }
                return false;
            case "KeyS":
                if (ctrl) {
                    save(true);
                    return true;
                }
                return false;
            case "KeyV":
            case "Digit1":
                setTool(Tool.POINT);
                return true;
            case "KeyD":
            case "Digit2":
                setTool(Tool.DRAW);
                return true;
            case "KeyX":
            case "Delete":
            case "Digit3":
                setTool(Tool.ERASE);
                return true;
            case "KeyT":
                testPlay();
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean back() {
        if (confirming) {
            confirming = false;
            return true;
        }
        leave();
        return true;
    }
}

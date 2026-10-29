package neontd.scene;

import neontd.app.App;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Icons.Icon;
import neontd.gfx.Theme;
import neontd.gfx.Wordmark;
import neontd.math.Mathx;
import neontd.platform.TextInput;
import neontd.save.Leaderboard;
import neontd.ui.Button;
import neontd.ui.Draw;
import neontd.ui.Easing;
import neontd.ui.Ui;
import neontd.ui.Viewport;

/**
 * Spielername wählen: beim ersten Start direkt nach dem Intro (ohne Namen geht es nicht weiter) und später aus dem
 * Profil heraus zum Ändern. Der Name ist weltweit eindeutig – der Server prüft ihn, bevor er gilt. Ohne Netz wird
 * er vorgemerkt und beim nächsten Kontakt bestätigt.
 *
 * <p>Im Browser liegt ein echtes Eingabefeld über der gezeichneten Box (iPhone-Tastatur, Einfügen, Autokorrektur).
 * Ohne Plattform-Feld (Desktop, Tests) tippt man direkt; ein Klick auf das Feld öffnet den Eingabedialog.
 */
public final class NameScene extends Scene {
    private final boolean firstStart;
    private final Ui ui = new Ui();
    private final Button go;
    private final Button cancel;
    private TextInput input;
    private String text = "";
    private String error;
    private boolean checking;
    private double time;
    /** Zeit seit dem Ende der Überblendung: Erst dann kommt der Inhalt – über einer völlig ruhigen Wortmarke. */
    private double reveal = -1;
    private double shake;
    private boolean done;
    /** Die Szene wird gerade ausgeblendet (das echte Eingabefeld blendet mit). */
    private boolean exiting;
    /** Höhe, nach der das Layout ausgerichtet ist – ändert sich nicht, wenn die Tastatur aufgeht. */
    private double layoutW = -1;
    private double stableH;

    // Lage (wird in layout berechnet)
    private double fieldX;
    private double fieldY;
    private double fieldW;
    private double fieldH;
    private double titleY;

    public NameScene(App app, boolean firstStart) {
        super(app);
        this.firstStart = firstStart;
        String known = app.board.playerName();
        if (known == null || known.isEmpty()) {
            known = app.progress.name == null ? "" : app.progress.name.trim();
        }
        text = Leaderboard.cleanName(known);
        go = ui.add(new Button(firstStart ? "Los geht's" : "Speichern", null, Theme.ACCENT, this::submit));
        go.painter = this::paintGo;
        cancel = ui.add(new Button("Abbrechen", null, Theme.MUTED, () -> app.goTo(new ProfileScene(app))));
        cancel.style = Button.Style.GHOST;
        cancel.visible = !firstStart;
    }

    @Override
    public void onEnter() {
        input = app.platform.createTextInput("Spielername", Leaderboard.MAX_NAME, this::submit);
        if (input != null) {
            input.setText(text);
            placeInput();
            if (!app.platform.touchPrimary()) {
                input.focus();
            }
        }
    }

    @Override
    public void onExit() {
        // Das Feld verschwindet nicht schlagartig, sondern blendet mit der Szene aus (siehe render/onHidden).
        exiting = true;
        if (input != null) {
            input.blur();
        }
    }

    @Override
    public void onHidden() {
        if (input != null) {
            input.dispose();
            input = null;
        }
    }

    @Override
    public void layout() {
        Viewport vp = app.vp;
        double u = vp.u;
        double m = 16 * u;
        double w = Math.min(vp.safeW() - 2 * m, 440 * u);
        double x = vp.insetL + (vp.safeW() - w) / 2;
        // Die Höhe zählt nur, solange sie wächst (oder sich die Breite ändert): Geht auf dem iPhone die Tastatur
        // auf und das Fenster wird niedriger, bleibt alles genau, wo es ist.
        if (vp.w != layoutW) {
            layoutW = vp.w;
            stableH = vp.safeH();
        } else {
            stableH = Math.max(stableH, vp.safeH());
        }
        // Inhalt in der oberen Hälfte: Auf dem iPhone deckt die Tastatur die untere ab.
        titleY = vp.insetT + Math.max(96 * u, Math.min(stableH * 0.16, 150 * u));
        fieldX = x;
        fieldW = w;
        fieldH = 56 * u;
        fieldY = titleY + 92 * u;
        double by = fieldY + fieldH + 44 * u;
        go.bounds(x, by, w, 52 * u);
        cancel.bounds(x, by + 60 * u, w, 44 * u);
        placeInput();
    }

    /** Einblend-Fortschritt eines Elements (gestaffelt nach {@code delay} Sekunden). */
    private double part(double delay) {
        return reveal < 0 ? 0 : Easing.outCubic(Mathx.clamp01((reveal - delay) / 0.5));
    }

    private static final double FIELD_DELAY = 0.12;

    private void placeInput() {
        if (input == null) {
            return;
        }
        double a = part(FIELD_DELAY);
        if (exiting) {
            a *= 1 - app.transitionProgress();
        }
        double u = app.vp.u;
        double dy = (1 - a) * 10 * u;
        input.place(fieldX + 2 * u, fieldY + 2 * u + dy, fieldW - 4 * u - 56 * u, fieldH - 4 * u, 17 * u, a);
    }

    @Override
    public void update(double dt) {
        time += dt;
        if (reveal < 0 && !app.transitioning()) {
            reveal = 0;
        } else if (reveal >= 0) {
            reveal += Math.min(dt, 1.0 / 30);
        }
        shake = Math.max(0, shake - dt * 2.5);
        ui.update(dt);
        if (input != null) {
            String now = input.text();
            if (!now.equals(text)) {
                text = now;
                error = null;
            }
            placeInput();
        }
    }

    private String problem() {
        return Leaderboard.nameProblem(text);
    }

    private void submit() {
        if (checking || done) {
            return;
        }
        String p = problem();
        if (p != null) {
            error = p;
            shake = 1;
            return;
        }
        final String name = Leaderboard.cleanName(text);
        checking = true;
        error = null;
        app.board.register(name, (ok, err) -> {
            checking = false;
            if (ok) {
                done = true;
                if (input != null) {
                    input.blur();
                }
                app.progress.name = name;
                app.commitProgress();
                app.goTo(firstStart ? new MenuScene(app) : new ProfileScene(app));
                return;
            }
            shake = 1;
            if ("vergeben".equals(err)) {
                error = "„" + name + "“ ist schon vergeben";
            } else if ("ungueltig".equals(err)) {
                error = "Nur Buchstaben, Zahlen, Leerzeichen und . _ -";
            } else if ("zu_oft".equals(err)) {
                error = "Zu viele Änderungen – bitte später noch einmal";
            } else {
                error = "Das hat nicht geklappt – bitte noch einmal";
            }
            if (input != null) {
                input.focus();
            }
        });
    }

    /** Für Tests und das Screenshot-Werkzeug: Text eintragen, als hätte man ihn getippt. */
    public void typeForTest(String s) {
        text = s == null ? "" : s;
        error = null;
        if (input != null) {
            input.setText(text);
        }
    }

    /** Für Tests: Bestätigen wie mit der Taste. */
    public void submitForTest() {
        submit();
    }

    /** Für Tests: der aktuelle Hinweis/Fehler. */
    public String errorForTest() {
        return error;
    }

    public double[] goAnchor() {
        return new double[] {go.cx(), go.cy()};
    }

    public double[] fieldAnchor() {
        return new double[] {fieldX + fieldW / 2, fieldY + fieldH / 2};
    }

    // ---------------------------------------------------------------------------------------- Zeichnen

    @Override
    public void render(Gfx g) {
        Viewport vp = app.vp;
        double u = vp.u;
        g.fillRect(0, 0, vp.w, vp.h, Theme.BG);
        double[] box = MenuScene.wordmarkBox(vp);
        Wordmark.draw(g, box[0], box[1], box[2], 4, null, app.time); // gemeinsame Uhr: Flackern läuft über Szenenwechsel durch

        if (exiting) {
            placeInput(); // beim Ausblenden wird nur noch gezeichnet, nicht mehr aktualisiert
        }
        double x = fieldX;
        double y = titleY;
        double r0 = part(0);
        double r1 = part(0.06);
        double rf = part(FIELD_DELAY);
        double r3 = part(0.18);
        g.save();
        g.alpha(r0);
        g.translate(0, (1 - r0) * 10 * u);
        g.text(firstStart ? "WILLKOMMEN" : "PROFIL", x, y, 11 * u, Theme.ACCENT, Gfx.ALIGN_LEFT, true);
        g.text(firstStart ? "Wie heißt du?" : "Neuer Name", x, y + 34 * u, 30 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        g.restore();
        g.save();
        g.alpha(r1);
        g.translate(0, (1 - r1) * 10 * u);
        Draw.fit(g, "Dein Name steht in der Arena und allen Bestenlisten.", x, y + 64 * u, 14 * u, fieldW, Theme.MUTED,
                Gfx.ALIGN_LEFT, false);
        g.restore();

        // Feld (das echte Eingabefeld liegt mit genau derselben Bewegung darüber, siehe placeInput)
        g.save();
        g.alpha(rf);
        g.translate(0, (1 - rf) * 10 * u);
        double sx = shake > 0 ? Math.sin(shake * 40) * 6 * u * shake : 0;
        double fx = x + sx;
        boolean valid = problem() == null;
        int edge = error != null ? Theme.NEG : (text.isEmpty() ? Theme.LINE : Colors.withAlpha(Theme.ACCENT, 0.7));
        g.fillRoundRect(fx, fieldY, fieldW, fieldH, 16 * u, Theme.SURFACE2);
        g.strokeRoundRect(fx, fieldY, fieldW, fieldH, 16 * u, error != null || !text.isEmpty() ? 1.5 * u : 1, edge);
        if (input == null) {
            // Ohne Plattform-Feld: Text und Cursor selbst zeichnen
            double tx = fx + 18 * u;
            double cy = fieldY + fieldH / 2;
            if (text.isEmpty()) {
                g.text("Spielername", tx, cy, 17 * u, Theme.MUTED, Gfx.ALIGN_LEFT, true);
            } else {
                g.text(text, tx, cy, 17 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
            }
            if ((time % 1.0) < 0.55) {
                double cw = text.isEmpty() ? 0 : g.textWidth(text, 17 * u, true);
                g.fillRect(tx + cw + 2 * u, cy - 11 * u, 2 * u, 22 * u, Theme.ACCENT);
            }
        }
        // Häkchen, sobald der Name passt
        double ix = fx + fieldW - 30 * u;
        double iy = fieldY + fieldH / 2;
        if (checking) {
            double ang = time * 7;
            g.beginPath();
            g.arc(ix, iy, 9 * u, ang, ang + Math.PI * 1.4);
            g.stroke(2 * u, Theme.ACCENT);
        } else if (valid && error == null) {
            g.fillCircle(ix, iy, 11 * u, Colors.withAlpha(Theme.POS, 0.18));
            Icons.draw(g, Icon.CHECK, ix, iy, 6 * u, Theme.POS, 0);
        }
        g.restore();

        // Hinweis, Zähler und Taste
        g.save();
        g.alpha(r3);
        g.translate(0, (1 - r3) * 10 * u);
        double hy = fieldY + fieldH + 18 * u;
        String hint = error != null ? error
                : (text.isEmpty() ? "2–16 Zeichen · Buchstaben, Zahlen, Leerzeichen, . _ -"
                        : (valid ? "Sieht gut aus" : problem()));
        int hc = error != null ? Theme.NEG : (valid && !text.isEmpty() ? Theme.POS : Theme.MUTED);
        Draw.fit(g, hint, x + 4 * u, hy, 12 * u, fieldW - 60 * u, hc, Gfx.ALIGN_LEFT, false);
        g.text(Leaderboard.cleanName(text).length() + "/" + Leaderboard.MAX_NAME, x + fieldW - 4 * u, hy, 12 * u,
                Theme.MUTED, Gfx.ALIGN_RIGHT, false);
        ui.render(g);
        g.restore();

        // Fußnote
        String note = app.board.available() ? "Gespeichert werden nur dein Name und eine zufällige Gerätekennung."
                : "Ohne Verbindung: Der Name gilt erst einmal nur auf diesem Gerät.";
        g.save();
        g.alpha(part(0.3) * 0.9);
        // feste Lage (nicht vp.h): Geht die Tastatur auf, rutscht nichts nach
        Draw.fit(g, note, vp.w / 2, vp.insetT + stableH - 24 * u, 11 * u, fieldW, Theme.MUTED, Gfx.ALIGN_CENTER, false);
        g.restore();
    }

    private void paintGo(Gfx g, Button b) {
        double w = b.w;
        double h = b.h;
        boolean valid = problem() == null && !checking;
        double hv = Math.max(b.hover.value, b.press.value);
        int fill = valid ? Colors.lerp(Theme.ACCENT, 0xFFFFFF, 0.12 * hv) : Theme.SURFACE3;
        g.fillRoundRect(-w / 2, -h / 2, w, h, 16 * app.vp.u, fill);
        String label = checking ? "Prüfe …" : b.label;
        g.text(label, 0, 0, 17 * app.vp.u, valid ? 0x000000 : Theme.MUTED, Gfx.ALIGN_CENTER, true);
    }

    // ---------------------------------------------------------------------------------------- Eingabe

    @Override
    public void pointerDown(double x, double y, boolean touch) {
        if (reveal < 0.15) {
            return; // noch nicht eingeblendet
        }
        ui.pointerDown(x, y);
    }

    @Override
    public void pointerMove(double x, double y, boolean pressed, boolean touch) {
        ui.pointerMove(x, y, !touch);
    }

    @Override
    public void pointerUp(double x, double y, boolean touch) {
        if (ui.pointerUp(x, y)) {
            return;
        }
        boolean inField = x >= fieldX && x <= fieldX + fieldW && y >= fieldY && y <= fieldY + fieldH;
        if (inField && input == null) {
            app.platform.prompt("Spielername (2–16 Zeichen)", text, s -> {
                if (s != null) {
                    typeForTest(Leaderboard.cleanName(s));
                }
            });
        } else if (inField) {
            input.focus();
        } else if (input != null) {
            input.blur();
        }
    }

    @Override
    public void pointerCancel() {
        ui.cancel();
    }

    @Override
    public boolean key(String code, boolean ctrl) {
        if (code.equals("Enter") || code.equals("NumpadEnter")) {
            submit();
            return true;
        }
        if (input != null) {
            return false; // das echte Feld bekommt die Tasten selbst
        }
        if (code.equals("Backspace")) {
            if (!text.isEmpty()) {
                text = text.substring(0, text.length() - 1);
                error = null;
            }
            return true;
        }
        String ch = null;
        if (code.startsWith("Key") && code.length() == 4) {
            ch = text.isEmpty() || text.endsWith(" ") ? code.substring(3) : code.substring(3).toLowerCase();
        } else if (code.startsWith("Digit") && code.length() == 6) {
            ch = code.substring(5);
        } else if (code.equals("Space")) {
            ch = " ";
        } else if (code.equals("Minus")) {
            ch = "-";
        } else if (code.equals("Period")) {
            ch = ".";
        }
        if (ch != null && text.length() < Leaderboard.MAX_NAME) {
            text = text + ch;
            error = null;
            return true;
        }
        return false;
    }

    @Override
    public boolean back() {
        if (firstStart) {
            return true; // ohne Namen geht es nicht weiter
        }
        app.goTo(new ProfileScene(app));
        return true;
    }
}

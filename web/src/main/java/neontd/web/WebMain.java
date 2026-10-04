package neontd.web;

import neontd.app.App;
import neontd.platform.KeyValueStore;
import neontd.platform.Platform;
import neontd.save.Http;
import java.util.function.Consumer;
import org.teavm.jso.browser.AnimationFrameCallback;
import org.teavm.jso.browser.Window;
import org.teavm.jso.canvas.CanvasRenderingContext2D;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLDocument;

/** Startpunkt im Browser: verbindet Canvas, Zeiger-/Tastatur-Ereignisse und die Bildschleife mit {@link App}. */
public final class WebMain {
    /**
     * Obergrenze für die Canvas-Fläche in Gerätepixeln: Handys rendern bis ca. 3 Mio. Pixel (iPhone ≈ 3×),
     * größere Bildschirme bis ca. 5,5 Mio. Das hält Linien scharf, ohne ältere Geräte beim Füllen zu überfordern.
     */
    private static final double PIXEL_BUDGET_PHONE = 3_200_000;
    private static final double PIXEL_BUDGET_LARGE = 5_500_000;

    private final HTMLCanvasElement canvas;
    private final CanvasGfx gfx;
    private final App app;
    private double dpr = 1;
    private double lastW = -1;
    private double lastH = -1;
    private double lastInsets = -1;
    private int activePointer = -1;
    private boolean firstFrame = true;
    private int frameCounter;
    private int errorCount;

    private WebMain(HTMLCanvasElement canvas, boolean touch) {
        this.canvas = canvas;
        CanvasRenderingContext2D ctx = Js.context2d(canvas);
        this.gfx = new CanvasGfx(ctx);
        final KeyValueStore store = new WebStore();
        final Http http = new WebHttp();
        this.app = new App(new Platform() {
            @Override
            public KeyValueStore store() {
                return store;
            }

            @Override
            public boolean touchPrimary() {
                return touch;
            }

            @Override
            public double nowMillis() {
                return Js.nowMillis();
            }

            @Override
            public Http http() {
                return http;
            }

            @Override
            public void prompt(String title, String initial, Consumer<String> result) {
                result.accept(Js.prompt(title, initial == null ? "" : initial));
            }

            @Override
            public void copyText(String text, Consumer<Boolean> done) {
                Js.copy(text, ok -> done.accept(ok));
            }

            @Override
            public void openUrl(String url) {
                Js.openUrl(url);
            }

            @Override
            public void requestPersistentStorage() {
                Js.persistStorage();
            }
        });
    }

    public static void main(String[] args) {
        HTMLDocument doc = Window.current().getDocument();
        HTMLCanvasElement canvas = (HTMLCanvasElement) doc.getElementById("game");
        WebMain game = new WebMain(canvas, Js.coarsePointer());
        game.installInput();
        game.resize();
        game.app.platform.requestPersistentStorage();
        if (Js.debugRequested()) {
            Js.exposeDebug(cmd -> {
                try {
                    return DebugApi.handle(game.app, cmd);
                } catch (Throwable t) {
                    return "error: " + t;
                }
            });
        }
        // Beim Wegwischen/Wechseln der App: Stand sichern und in die Cloud schieben (oft die einzige Gelegenheit am iPhone).
        doc.addEventListener("visibilitychange", e -> {
            if (Js.documentHidden()) {
                game.suspend();
            }
        });
        Window.current().addEventListener("pagehide", e -> game.suspend());
        Window.current().addEventListener("resize", e -> game.resize());
        Window.current().addEventListener("orientationchange", e -> game.resize());
        game.loop();
    }

    private void suspend() {
        try {
            app.suspend();
        } catch (Throwable t) {
            Js.log("Fehler beim Sichern: " + t);
        }
    }

    // -------------------------------------------------------------------------------------------- Größe

    private void resize() {
        double w = Js.innerWidth();
        double h = Js.innerHeight();
        double l = Js.safeInset(0);
        double t = Js.safeInset(1);
        double r = Js.safeInset(2);
        double b = Js.safeInset(3);
        double area = Math.max(1, w * h);
        double budget = area < 900_000 ? PIXEL_BUDGET_PHONE : PIXEL_BUDGET_LARGE;
        double d = Math.max(1, Math.min(3, Math.min(Js.devicePixelRatio(), Math.sqrt(budget / area))));
        double insetKey = l + t * 7 + r * 13 + b * 29;
        if (w == lastW && h == lastH && d == dpr && insetKey == lastInsets) {
            return;
        }
        lastW = w;
        lastH = h;
        dpr = d;
        lastInsets = insetKey;
        Js.setCssSize(canvas, w, h);
        canvas.setWidth((int) Math.floor(w * d + 0.5));
        canvas.setHeight((int) Math.floor(h * d + 0.5));
        app.resize(w, h, l, t, r, b);
    }

    // ------------------------------------------------------------------------------------------- Eingabe

    private void installInput() {
        // Finger: klassische Touch-Ereignisse (siehe Js.installTouch); Maus und Stift laufen über Zeiger-Ereignisse.
        Js.installTouch(canvas, (type, id, x, y) -> {
            switch (type) {
                case 0:
                    if (activePointer >= 0) {
                        return; // zweiter Finger wird ignoriert
                    }
                    activePointer = id;
                    app.pointerDown(x, y, true);
                    break;
                case 1:
                    if (id == activePointer) {
                        app.pointerMove(x, y, true, true);
                    }
                    break;
                case 2:
                    if (id == activePointer) {
                        activePointer = -1;
                        app.pointerUp(x, y, true);
                    }
                    break;
                default:
                    if (id == activePointer) {
                        activePointer = -1;
                        app.pointerCancel();
                    }
                    break;
            }
        });
        canvas.addEventListener("pointerdown", (EventListener<Js.PointerEv>) e -> {
            if ("touch".equals(e.getPointerType())) {
                return; // Finger kommen über Touch-Ereignisse
            }
            if (activePointer >= 0 && e.getPointerId() != activePointer) {
                return; // zweiter Finger wird ignoriert
            }
            boolean mouse = "mouse".equals(e.getPointerType());
            if (mouse && e.getButton() == 2) {
                e.preventDefault();
                app.key("RightClick", false);
                return;
            }
            if (mouse && e.getButton() != 0) {
                return;
            }
            e.preventDefault();
            activePointer = e.getPointerId();
            Js.capturePointer(canvas, activePointer);
            app.pointerDown(e.getClientX(), e.getClientY(), !mouse);
        });
        canvas.addEventListener("pointermove", (EventListener<Js.PointerEv>) e -> {
            if ("touch".equals(e.getPointerType())) {
                return; // Finger kommen über Touch-Ereignisse
            }
            boolean mouse = "mouse".equals(e.getPointerType());
            if (activePointer >= 0 && e.getPointerId() != activePointer) {
                return;
            }
            boolean pressed = activePointer >= 0;
            if (!pressed && !mouse) {
                return;
            }
            app.pointerMove(e.getClientX(), e.getClientY(), pressed, !mouse);
        });
        canvas.addEventListener("pointerup", (EventListener<Js.PointerEv>) e -> {
            if ("touch".equals(e.getPointerType())) {
                return; // Finger kommen über Touch-Ereignisse
            }
            if (e.getPointerId() != activePointer) {
                return;
            }
            activePointer = -1;
            app.pointerUp(e.getClientX(), e.getClientY(), !"mouse".equals(e.getPointerType()));
        });
        canvas.addEventListener("pointercancel", (EventListener<Js.PointerEv>) e -> {
            if ("touch".equals(e.getPointerType())) {
                return; // Finger kommen über Touch-Ereignisse
            }
            if (e.getPointerId() != activePointer) {
                return;
            }
            activePointer = -1;
            app.pointerCancel();
        });
        canvas.addEventListener("contextmenu", e -> e.preventDefault());
        canvas.addEventListener("wheel", (EventListener<Js.WheelEv>) e -> {
            e.preventDefault();
            double scale = e.getDeltaMode() == 1 ? 32 : 1;
            app.wheel(e.getDeltaY() * scale);
        });
        Window.current().addEventListener("keydown", (EventListener<Js.KeyEv>) e -> {
            if (app.key(e.getCode(), e.isCtrlKey() || e.isMetaKey())) {
                e.preventDefault();
            }
        });
        Window.current().addEventListener("blur", e -> {
            if (activePointer >= 0) {
                activePointer = -1;
                app.pointerCancel();
            }
        });
    }

    // ----------------------------------------------------------------------------------------- Bildschleife

    private void loop() {
        Window.requestAnimationFrame(new AnimationFrameCallback() {
            @Override
            public void onAnimationFrame(double timestampMs) {
                // Den nächsten Frame zuerst einplanen: Ein einzelner Fehler darf das Spiel nicht einfrieren lassen.
                Window.requestAnimationFrame(this);
                try {
                    frame(timestampMs);
                } catch (Throwable t) {
                    if (errorCount++ < 5) {
                        Js.log("Fehler im Frame: " + t);
                    }
                }
            }
        });
    }

    private void frame(double timestampMs) {
        // Größenänderungen ohne Ereignis abfangen (z. B. einklappende Safari-Leisten): Die Fenstergröße prüfen wir
        // jeden Frame (billig), die Sicherheitsränder – dafür muss der Browser Stile auswerten – nur gelegentlich.
        frameCounter++;
        if (Js.innerWidth() != lastW || Js.innerHeight() != lastH || frameCounter % 30 == 0) {
            resize();
        }
        gfx.begin(lastW, lastH, dpr);
        gfx.fillRect(0, 0, lastW, lastH, 0x000000);
        app.update(timestampMs / 1000.0);
        app.render(gfx);
        if (firstFrame) {
            firstFrame = false;
            Js.hideBoot();
        }
    }
}

package neontd.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSProperty;
import org.teavm.jso.canvas.CanvasRenderingContext2D;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.html.HTMLCanvasElement;

/** Dünne Brücken zu Browser-Funktionen, die TeaVMs typisierte APIs nicht (bequem) anbieten. */
final class Js {
    private Js() {
    }

    /** Zeiger-Ereignis (Maus, Finger, Stift). */
    interface PointerEv extends Event {
        @JSProperty
        double getClientX();

        @JSProperty
        double getClientY();

        @JSProperty
        int getPointerId();

        @JSProperty
        String getPointerType();

        @JSProperty
        int getButton();

        @JSProperty
        int getButtons();

        @JSProperty
        boolean isPrimary();
    }

    /** Mausrad-Ereignis. */
    interface WheelEv extends Event {
        @JSProperty
        double getDeltaY();

        @JSProperty
        int getDeltaMode();
    }

    /** Tastatur-Ereignis. */
    interface KeyEv extends Event {
        @JSProperty
        String getCode();

        @JSProperty
        boolean isCtrlKey();

        @JSProperty
        boolean isMetaKey();
    }

    @JSBody(params = {"canvas"}, script = "return canvas.getContext('2d', {alpha: false});")
    static native CanvasRenderingContext2D context2d(HTMLCanvasElement canvas);

    /** Legt die Anzeigegröße des Canvas in CSS-Pixeln fest (genau passend zu innerWidth/innerHeight). */
    @JSBody(params = {"canvas", "w", "h"}, script = "canvas.style.width = w + 'px'; canvas.style.height = h + 'px';")
    static native void setCssSize(HTMLCanvasElement canvas, double w, double h);

    @JSBody(params = {"canvas", "id"}, script = "try { canvas.setPointerCapture(id); } catch (e) {}")
    static native void capturePointer(HTMLCanvasElement canvas, int id);

    @JSBody(params = {}, script = "return window.innerWidth;")
    static native double innerWidth();

    @JSBody(params = {}, script = "return window.innerHeight;")
    static native double innerHeight();

    @JSBody(params = {}, script = "return window.devicePixelRatio || 1;")
    static native double devicePixelRatio();

    /** Sicherheitsabstand (iPhone-Notch, Home-Indikator) aus einem versteckten Element mit env(safe-area-inset-*). */
    @JSBody(params = {"side"}, script = "var e = document.getElementById('safe'); if (!e) return 0;"
            + "var s = getComputedStyle(e); var v = side === 0 ? s.paddingLeft : side === 1 ? s.paddingTop"
            + " : side === 2 ? s.paddingRight : s.paddingBottom; return parseFloat(v) || 0;")
    static native double safeInset(int side);

    @JSBody(params = {}, script = "return window.matchMedia && window.matchMedia('(pointer: coarse)').matches;")
    static native boolean coarsePointer();

    @JSBody(params = {"ctx", "dash", "gap", "phase"}, script = "ctx.setLineDash([dash, gap]); ctx.lineDashOffset = phase;")
    static native void setDash(CanvasRenderingContext2D ctx, double dash, double gap, double phase);

    @JSBody(params = {"ctx"}, script = "ctx.setLineDash([]);")
    static native void clearDash(CanvasRenderingContext2D ctx);

    @JSBody(params = {"key"}, script = "try { return window.localStorage.getItem(key); } catch (e) { return null; }")
    static native String storageGet(String key);

    @JSBody(params = {"key", "value"}, script = "try { window.localStorage.setItem(key, value); return true; }"
            + " catch (e) { return false; }")
    static native boolean storageSet(String key, String value);

    @JSBody(params = {"key"}, script = "try { window.localStorage.removeItem(key); } catch (e) {}")
    static native void storageRemove(String key);

    /** Blendet den Ladebildschirm aus, sobald das Spiel das erste Bild gezeichnet hat. */
    @JSBody(params = {}, script = "var b = document.getElementById('boot'); if (b) { b.classList.add('done');"
            + " setTimeout(function () { if (b.parentNode) b.parentNode.removeChild(b); }, 600); }")
    static native void hideBoot();

    @JSBody(params = {"msg"}, script = "console.log(msg);")
    static native void log(String msg);
}

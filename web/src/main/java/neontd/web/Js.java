package neontd.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
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

    /** Rückruf für {@link #fetch}: HTTP-Status (0 = Netzfehler) und Antworttext bzw. Fehlermeldung. */
    @JSFunctor
    interface FetchDone extends JSObject {
        void done(int status, String body);
    }

    /** Rückruf für Ja/Nein-Ergebnisse (Zwischenablage). */
    @JSFunctor
    interface BoolDone extends JSObject {
        void done(boolean ok);
    }

    /**
     * Ruft {@code fetch} auf. {@code headers} sind Name/Wert-Paare, durch Zeilenumbrüche getrennt. Mit
     * {@code keepalive} läuft die Anfrage auch weiter, wenn die Seite gerade verlassen wird (nur kleine Texte).
     */
    @JSBody(params = {"method", "url", "headers", "body", "keepalive", "cb"}, script =
            "var h = {}; var p = headers.split('\\n'); for (var i = 0; i + 1 < p.length; i += 2) { h[p[i]] = p[i + 1]; }"
            + "var o = {method: method, headers: h}; if (body !== null) { o.body = body; } if (keepalive) { o.keepalive = true; }"
            + "try { fetch(url, o).then(function (r) { return r.text().then(function (t) { cb(r.status, t); }); })"
            + ".catch(function (e) { cb(0, String(e)); }); } catch (e) { cb(0, String(e)); }")
    static native void fetch(String method, String url, String headers, String body, boolean keepalive, FetchDone cb);

    @JSBody(params = {}, script = "return Date.now();")
    static native double nowMillis();

    /** Eingabefeld des Browsers; {@code null} bei Abbruch. */
    @JSBody(params = {"title", "initial"}, script = "var r = window.prompt(title, initial); return r === null ? null : r;")
    static native String prompt(String title, String initial);

    @JSBody(params = {"text", "cb"}, script =
            "function legacy() { try { var t = document.createElement('textarea'); t.value = text;"
            + "t.style.position = 'fixed'; t.style.opacity = '0'; document.body.appendChild(t); t.focus(); t.select();"
            + "var ok = document.execCommand('copy'); document.body.removeChild(t); cb(ok); } catch (e) { cb(false); } }"
            + "if (navigator.clipboard && navigator.clipboard.writeText) {"
            + "navigator.clipboard.writeText(text).then(function () { cb(true); }, legacy); } else { legacy(); }")
    static native void copy(String text, BoolDone cb);

    @JSBody(params = {"url"}, script = "try { window.open(url, '_blank', 'noopener'); } catch (e) {}")
    static native void openUrl(String url);

    /** Bittet den Browser, den Speicher nicht automatisch zu räumen (hilft gegen das Löschen nach Inaktivität). */
    @JSBody(params = {}, script = "try { if (navigator.storage && navigator.storage.persist) { navigator.storage.persist(); } }"
            + " catch (e) {}")
    static native void persistStorage();

    /** Befehlsschnittstelle für Browser-Tests (nur mit {@code ?debug} in der Adresse). */
    @JSFunctor
    interface StringFn extends JSObject {
        String call(String arg);
    }

    @JSBody(params = {}, script = "return /[?&]debug(&|$)/.test(location.search);")
    static native boolean debugRequested();

    @JSBody(params = {"fn"}, script = "window.__ntd = { call: fn };")
    static native void exposeDebug(StringFn fn);

    @JSBody(params = {}, script = "return document.hidden === true;")
    static native boolean documentHidden();
}

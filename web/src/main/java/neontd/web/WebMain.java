package neontd.web;

import neontd.Hello;
import org.teavm.jso.browser.Window;
import org.teavm.jso.canvas.CanvasRenderingContext2D;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLDocument;

public final class WebMain {
    public static void main(String[] args) {
        HTMLDocument doc = Window.current().getDocument();
        HTMLCanvasElement canvas = (HTMLCanvasElement) doc.getElementById("game");
        canvas.setWidth(400);
        canvas.setHeight(200);
        CanvasRenderingContext2D ctx = (CanvasRenderingContext2D) canvas.getContext("2d");
        ctx.setFillStyle("#000");
        ctx.fillRect(0, 0, 400, 200);
        ctx.setFillStyle("#0ff");
        ctx.setFont("bold 28px sans-serif");
        ctx.fillText(Hello.greet(3), 20, 100);
    }
}

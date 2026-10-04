package neontd.web;

import neontd.save.Http;

/** HTTP über {@code fetch} des Browsers (GitHubs API erlaubt Zugriffe von fremden Seiten mit Token ausdrücklich). */
final class WebHttp implements Http {
    /** Grenze, bis zu der {@code keepalive} erlaubt ist (der Browser lässt nur rund 64 KB zu). */
    private static final int KEEPALIVE_MAX = 60_000;

    @Override
    public void request(String method, String url, String[] headers, String body, Callback cb) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < headers.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(headers[i]);
        }
        boolean keepalive = body == null || body.length() < KEEPALIVE_MAX;
        Js.fetch(method, url, sb.toString(), body, keepalive, (status, text) -> {
            try {
                cb.done(status, text);
            } catch (Throwable t) {
                Js.log("Fehler beim Abgleich: " + t);
            }
        });
    }
}

package neontd.desktop;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import javax.swing.SwingUtilities;
import neontd.save.Http;

/** HTTP über {@code java.net.http}; Antworten werden im Oberflächen-Thread zugestellt. */
final class JavaHttp implements Http {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public void request(String method, String url, String[] headers, String body, Callback cb) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20));
            for (int i = 0; i + 1 < headers.length; i += 2) {
                b.header(headers[i], headers[i + 1]);
            }
            b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            client.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString()).whenComplete((res, err) ->
                    SwingUtilities.invokeLater(() -> {
                        if (err != null) {
                            cb.done(0, String.valueOf(err.getMessage()));
                        } else {
                            cb.done(res.statusCode(), res.body());
                        }
                    }));
        } catch (RuntimeException e) {
            SwingUtilities.invokeLater(() -> cb.done(0, String.valueOf(e.getMessage())));
        }
    }
}

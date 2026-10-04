package neontd.save;

import java.nio.charset.StandardCharsets;

/** Base64 (URL-sicher, ohne Auffüllzeichen) für Text – ohne Abhängigkeit von der Laufzeitumgebung. */
public final class Base64Lite {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    private Base64Lite() {
    }

    public static String encode(String text) {
        byte[] in = text.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder((in.length * 4 + 2) / 3);
        for (int i = 0; i < in.length; i += 3) {
            int b0 = in[i] & 0xFF;
            int b1 = i + 1 < in.length ? in[i + 1] & 0xFF : 0;
            int b2 = i + 2 < in.length ? in[i + 2] & 0xFF : 0;
            sb.append(ALPHABET.charAt(b0 >> 2));
            sb.append(ALPHABET.charAt(((b0 & 3) << 4) | (b1 >> 4)));
            if (i + 1 < in.length) {
                sb.append(ALPHABET.charAt(((b1 & 15) << 2) | (b2 >> 6)));
            }
            if (i + 2 < in.length) {
                sb.append(ALPHABET.charAt(b2 & 63));
            }
        }
        return sb.toString();
    }

    /** @return der Text oder {@code null}, wenn die Eingabe kein gültiges Base64 ist */
    public static String decode(String code) {
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '+') {
                c = '-';
            } else if (c == '/') {
                c = '_';
            }
            if (c == '=' || c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                continue;
            }
            if (ALPHABET.indexOf(c) < 0) {
                return null;
            }
            clean.append(c);
        }
        int n = clean.length();
        if (n % 4 == 1) {
            return null;
        }
        byte[] out = new byte[n * 3 / 4];
        int o = 0;
        for (int i = 0; i < n; i += 4) {
            int c0 = ALPHABET.indexOf(clean.charAt(i));
            int c1 = ALPHABET.indexOf(clean.charAt(i + 1));
            int c2 = i + 2 < n ? ALPHABET.indexOf(clean.charAt(i + 2)) : 0;
            int c3 = i + 3 < n ? ALPHABET.indexOf(clean.charAt(i + 3)) : 0;
            out[o++] = (byte) ((c0 << 2) | (c1 >> 4));
            if (i + 2 < n) {
                out[o++] = (byte) (((c1 & 15) << 4) | (c2 >> 2));
            }
            if (i + 3 < n) {
                out[o++] = (byte) (((c2 & 3) << 6) | c3);
            }
        }
        return new String(out, 0, o, StandardCharsets.UTF_8);
    }
}

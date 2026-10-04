package neontd.save;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ein kleiner JSON-Leser und -Schreiber ohne Abhängigkeiten (läuft auch im Browser). Werte sind
 * {@code Map<String,Object>}, {@code List<Object>}, {@code String}, {@code Double}, {@code Boolean} oder {@code null}.
 */
public final class Json {
    private static final int MAX_DEPTH = 64;

    private Json() {
    }

    // ------------------------------------------------------------------------------------------ Schreiben

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder sb, Object v, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("zu tief verschachtelt");
        }
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            quote(sb, (String) v);
        } else if (v instanceof Boolean) {
            sb.append(((Boolean) v) ? "true" : "false");
        } else if (v instanceof Number) {
            number(sb, ((Number) v).doubleValue());
        } else if (v instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                quote(sb, e.getKey());
                sb.append(':');
                write(sb, e.getValue(), depth + 1);
            }
            sb.append('}');
        } else if (v instanceof List) {
            sb.append('[');
            List<Object> list = (List<Object>) v;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                write(sb, list.get(i), depth + 1);
            }
            sb.append(']');
        } else {
            throw new IllegalArgumentException("Typ nicht darstellbar: " + v.getClass().getName());
        }
    }

    private static void number(StringBuilder sb, double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            sb.append('0');
        } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            sb.append(formatWhole(d));
        } else {
            sb.append(Double.toString(d));
        }
    }

    /** Ganzzahl ohne Nachkommastellen und ohne long (TeaVM: BigInt). */
    private static String formatWhole(double d) {
        if (Math.abs(d) < 2_000_000_000.0) {
            return Integer.toString((int) d);
        }
        boolean neg = d < 0;
        double a = Math.abs(d);
        StringBuilder digits = new StringBuilder();
        while (a >= 1) {
            double q = Math.floor(a / 10);
            int digit = (int) (a - q * 10);
            digits.append((char) ('0' + digit));
            a = q;
        }
        if (digits.length() == 0) {
            return "0";
        }
        if (neg) {
            digits.append('-');
        }
        return digits.reverse().toString();
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append("\\u00");
                        sb.append(Character.forDigit((c >> 4) & 15, 16));
                        sb.append(Character.forDigit(c & 15, 16));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------------------------------- Lesen

    /** @throws IllegalArgumentException bei ungültigem JSON */
    public static Object parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("kein Text");
        }
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.value(0);
        p.skipWs();
        if (p.pos != text.length()) {
            throw p.error("unerwartete Zeichen nach dem Wert");
        }
        return v;
    }

    /** Wie {@link #parse}, liefert aber {@code null} statt einer Ausnahme. */
    public static Object tryParse(String text) {
        try {
            return parse(text);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) {
            this.s = s;
        }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("JSON-Fehler an Stelle " + pos + ": " + msg);
        }

        void skipWs() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '﻿') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object value(int depth) {
            if (depth > MAX_DEPTH) {
                throw error("zu tief verschachtelt");
            }
            if (pos >= s.length()) {
                throw error("unerwartetes Ende");
            }
            char c = s.charAt(pos);
            switch (c) {
                case '{':
                    return object(depth);
                case '[':
                    return array(depth);
                case '"':
                    return string();
                case 't':
                    return literal("true", Boolean.TRUE);
                case 'f':
                    return literal("false", Boolean.FALSE);
                case 'n':
                    return literal("null", null);
                default:
                    return number();
            }
        }

        Object literal(String word, Object result) {
            if (!s.startsWith(word, pos)) {
                throw error("unbekannter Wert");
            }
            pos += word.length();
            return result;
        }

        Object number() {
            int start = pos;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                    pos++;
                } else {
                    break;
                }
            }
            if (start == pos) {
                throw error("Wert erwartet");
            }
            try {
                return Double.valueOf(Double.parseDouble(s.substring(start, pos)));
            } catch (NumberFormatException e) {
                pos = start;
                throw error("ungültige Zahl");
            }
        }

        String string() {
            pos++; // "
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= s.length()) {
                    throw error("Text nicht beendet");
                }
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (pos >= s.length()) {
                    throw error("Text nicht beendet");
                }
                char e = s.charAt(pos++);
                switch (e) {
                    case '"':
                    case '\\':
                    case '/':
                        sb.append(e);
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'u':
                        if (pos + 4 > s.length()) {
                            throw error("ungültiges \\u");
                        }
                        int code = 0;
                        for (int i = 0; i < 4; i++) {
                            int d = Character.digit(s.charAt(pos + i), 16);
                            if (d < 0) {
                                throw error("ungültiges \\u");
                            }
                            code = code * 16 + d;
                        }
                        pos += 4;
                        sb.append((char) code);
                        break;
                    default:
                        throw error("ungültige Escape-Folge");
                }
            }
        }

        List<Object> array(int depth) {
            pos++; // [
            List<Object> list = new ArrayList<>();
            skipWs();
            if (pos < s.length() && s.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                skipWs();
                list.add(value(depth + 1));
                skipWs();
                if (pos >= s.length()) {
                    throw error("Liste nicht beendet");
                }
                char c = s.charAt(pos++);
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    pos--;
                    throw error("',' oder ']' erwartet");
                }
            }
        }

        Map<String, Object> object(int depth) {
            pos++; // {
            Map<String, Object> map = new LinkedHashMap<>();
            skipWs();
            if (pos < s.length() && s.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                if (pos >= s.length() || s.charAt(pos) != '"') {
                    throw error("Schlüssel erwartet");
                }
                String key = string();
                skipWs();
                if (pos >= s.length() || s.charAt(pos) != ':') {
                    throw error("':' erwartet");
                }
                pos++;
                skipWs();
                map.put(key, value(depth + 1));
                skipWs();
                if (pos >= s.length()) {
                    throw error("Objekt nicht beendet");
                }
                char c = s.charAt(pos++);
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    pos--;
                    throw error("',' oder '}' erwartet");
                }
            }
        }
    }

    // ----------------------------------------------------------------------------------- Zugriffshelfer

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object o) {
        return o instanceof List ? (List<Object>) o : null;
    }

    public static Map<String, Object> map(Map<String, Object> m, String key) {
        return m == null ? null : asMap(m.get(key));
    }

    public static List<Object> list(Map<String, Object> m, String key) {
        return m == null ? null : asList(m.get(key));
    }

    public static String str(Map<String, Object> m, String key, String def) {
        Object v = m == null ? null : m.get(key);
        return v instanceof String ? (String) v : def;
    }

    public static double num(Map<String, Object> m, String key, double def) {
        Object v = m == null ? null : m.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : def;
    }

    public static int integer(Map<String, Object> m, String key, int def) {
        double d = num(m, key, def);
        return d >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (d <= Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) d);
    }

    public static boolean bool(Map<String, Object> m, String key, boolean def) {
        Object v = m == null ? null : m.get(key);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    /** Zahl aus einer Liste an Position {@code i} (0, wenn nicht vorhanden). */
    public static double at(List<Object> l, int i) {
        Object v = l != null && i >= 0 && i < l.size() ? l.get(i) : null;
        return v instanceof Number ? ((Number) v).doubleValue() : 0;
    }
}

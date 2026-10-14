package com.cybershield.util;

import java.util.*;

/** Minimal dependency-free JSON reader/writer used by the API layer and the persisted history file. */
public final class Json {
    private Json() {}

    public static final class JsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public JsonException(String m) { super(m); }
    }

    // ------------------------------------------------------------------ writer
    public static String write(Object o) {
        StringBuilder sb = new StringBuilder(256);
        write(sb, o);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object o) {
        if (o == null) sb.append("null");
        else if (o instanceof String s) str(sb, s);
        else if (o instanceof Character c) str(sb, String.valueOf(c));
        else if (o instanceof Boolean || o instanceof Integer || o instanceof Long || o instanceof Short || o instanceof Byte) sb.append(o);
        else if (o instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) sb.append("null");
            else if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append((long) d);
            else sb.append(d);
        } else if (o instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                str(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (o instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object x : it) { if (!first) sb.append(','); first = false; write(sb, x); }
            sb.append(']');
        } else if (o instanceof int[] a) {
            sb.append('[');
            for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(a[i]); }
            sb.append(']');
        } else if (o instanceof long[] a) {
            sb.append('[');
            for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(a[i]); }
            sb.append(']');
        } else if (o instanceof double[] a) {
            sb.append('[');
            for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); write(sb, a[i]); }
            sb.append(']');
        } else if (o instanceof boolean[] a) {
            sb.append('[');
            for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(a[i]); }
            sb.append(']');
        } else if (o instanceof long[][] a) {
            write(sb, Arrays.asList((Object[]) a));
        } else if (o instanceof int[][] a) {
            write(sb, Arrays.asList((Object[]) a));
        } else if (o instanceof Object[] a) {
            write(sb, Arrays.asList(a));
        } else if (o instanceof Enum<?> e) str(sb, e.name());
        else str(sb, o.toString());
    }

    private static void str(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------ parser
    public static Object parse(String s) {
        Parser p = new Parser(s);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != s.length()) throw new JsonException("Unexpected trailing data at " + p.i);
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object o) {
        if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
        throw new JsonException("Expected a JSON object");
    }

    private static final class Parser {
        final String s;
        int i;
        Parser(String s) { this.s = s; }

        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        char peek() {
            if (i >= s.length()) throw new JsonException("Unexpected end of JSON");
            return s.charAt(i);
        }

        Object value() {
            char c = peek();
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            return number();
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; ws();
            if (peek() == '}') { i++; return m; }
            while (true) {
                ws();
                if (peek() != '"') throw new JsonException("Expected string key at " + i);
                String k = string();
                ws();
                if (peek() != ':') throw new JsonException("Expected ':' at " + i);
                i++; ws();
                m.put(k, value());
                ws();
                char c = peek(); i++;
                if (c == '}') return m;
                if (c != ',') throw new JsonException("Expected ',' or '}' at " + (i - 1));
            }
        }

        List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++; ws();
            if (peek() == ']') { i++; return l; }
            while (true) {
                ws();
                l.add(value());
                ws();
                char c = peek(); i++;
                if (c == ']') return l;
                if (c != ',') throw new JsonException("Expected ',' or ']' at " + (i - 1));
            }
        }

        String string() {
            StringBuilder sb = new StringBuilder();
            i++;
            while (true) {
                char c = peek(); i++;
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                char e = peek(); i++;
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (i + 4 > s.length()) throw new JsonException("Bad unicode escape");
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> sb.append(e);
                }
            }
        }

        Double number() {
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (st == i) throw new JsonException("Unexpected character '" + s.charAt(i) + "' at " + i);
            try { return Double.parseDouble(s.substring(st, i)); }
            catch (NumberFormatException ex) { throw new JsonException("Bad number at " + st); }
        }
    }
}

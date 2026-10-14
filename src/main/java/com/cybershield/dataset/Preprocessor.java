package com.cybershield.dataset;

import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Pipeline stage "Preprocessing": URL-decode and lower-case every log line so signatures match obfuscated payloads. */
public final class Preprocessor {
    private Preprocessor() {}

    public static String normalize(String raw) {
        String s = raw;
        if (s.indexOf('%') >= 0 || s.indexOf('+') >= 0) {
            try { s = URLDecoder.decode(s, StandardCharsets.UTF_8); }
            catch (IllegalArgumentException malformed) { s = lenientDecode(s); }
        }
        return s.replace('\n', ' ').replace('\r', ' ').toLowerCase(Locale.ROOT);
    }

    /** Decodes valid %XX sequences and leaves malformed ones untouched (attackers often send broken encodings). */
    static String lenientDecode(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length() + 0 && hex(s.charAt(i + 1)) >= 0 && hex(s.charAt(i + 2)) >= 0) {
                out.write(hex(s.charAt(i + 1)) * 16 + hex(s.charAt(i + 2)));
                i += 2;
            } else if (c == '+') out.write(' ');
            else out.writeBytes(String.valueOf(c).getBytes(StandardCharsets.UTF_8));
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static int hex(char c) { return Character.digit(c, 16); }
}

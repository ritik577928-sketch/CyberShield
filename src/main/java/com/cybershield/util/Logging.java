package com.cybershield.util;

import java.util.logging.*;

/** One-line console logging for the whole application. */
public final class Logging {
    private Logging() {}

    public static void setup(Level level) {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$-7s %3$s - %5$s%6$s%n");
        Logger root = Logger.getLogger("");
        for (Handler h : root.getHandlers()) root.removeHandler(h);
        ConsoleHandler ch = new ConsoleHandler();
        ch.setLevel(level);
        ch.setFormatter(new SimpleFormatter());
        root.addHandler(ch);
        root.setLevel(level);
    }
}

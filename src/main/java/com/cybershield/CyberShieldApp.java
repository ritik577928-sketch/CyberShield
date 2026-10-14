package com.cybershield;

import com.cybershield.api.ApiServer;
import com.cybershield.service.AppContext;
import com.cybershield.util.Logging;
import java.nio.file.Paths;
import java.util.logging.*;

/** Entry point: {@code java -jar cybershield.jar [port]} (default 8080, or CYBERSHIELD_PORT). */
public final class CyberShieldApp {
    private CyberShieldApp() {}

    public static void main(String[] args) throws Exception {
        Logging.setup(Level.INFO);
        Logger log = Logger.getLogger(CyberShieldApp.class.getName());
        int port = 8080;
        try {
            String p = args.length > 0 ? args[0] : System.getenv("CYBERSHIELD_PORT");
            if (p != null && !p.isBlank()) port = Integer.parseInt(p.trim());
        } catch (NumberFormatException e) {
            log.severe("Invalid port - use a number such as 8080");
            System.exit(2);
        }
        AppContext app = new AppContext(Paths.get("data", "history.jsonl"));
        ApiServer server = new ApiServer(app);
        try {
            server.start(port);
        } catch (java.net.BindException e) {
            log.severe("Port " + port + " is already in use. Start with another port: start.sh 9090");
            System.exit(1);
        }
        log.info("Warming up detection pipeline on the bundled dataset...");
        app.detection.scan(app.datasets.get(null));
        log.info("Ready. Open http://localhost:" + port + " in your browser.");
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        Thread.currentThread().join();
    }
}

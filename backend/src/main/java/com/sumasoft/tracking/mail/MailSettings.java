package com.sumasoft.tracking.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Reads config/mail-and-reports.properties (Step 43): email and report settings in ONE file you can edit.
 * Where the file is looked for (first one found wins):
 *   1. the path in SUMA_CONFIG_FILE
 *   2. config/mail-and-reports.properties next to where the backend runs
 *   3. ../config/mail-and-reports.properties (when the backend runs from the backend folder)
 *   4. /app/config/mail-and-reports.properties (inside Docker)
 * The file is read again when it changes, at most once a minute. The email PASSWORD is never in this file.
 */
@Component
public class MailSettings {

    private static final Logger log = LoggerFactory.getLogger(MailSettings.class);
    public static final String FILE_NAME = "mail-and-reports.properties";

    private Properties props = new Properties();
    private Path loadedFrom;
    private FileTime loadedTime;
    private long checkedAt;

    /** The file in use, or null when none was found (then the defaults below apply). */
    public synchronized String path() {
        refresh();
        return loadedFrom == null ? null : loadedFrom.toAbsolutePath().toString();
    }

    public synchronized String text(String key, String fallback) {
        refresh();
        String v = props.getProperty(key);
        return v == null || v.isBlank() ? fallback : v.trim();
    }

    public int number(String key, int fallback, int min, int max) {
        try {
            int n = Integer.parseInt(text(key, String.valueOf(fallback)));
            return Math.max(min, Math.min(max, n));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public boolean flag(String key, boolean fallback) {
        String v = text(key, String.valueOf(fallback)).toLowerCase();
        return v.equals("true") || v.equals("yes") || v.equals("1") || v.equals("on");
    }

    /** A comma list of email addresses; wrong ones are left out. */
    public List<String> emails(String key) {
        List<String> out = new ArrayList<>();
        for (String part : text(key, "").split("[,;]")) {
            String e = part.trim().toLowerCase();
            if (!e.isEmpty() && MailService.EMAIL.matcher(e).matches() && !out.contains(e)) {
                out.add(e);
            }
        }
        return out;
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        if (now - checkedAt < 60_000 && checkedAt != 0) {
            return;
        }
        checkedAt = now;
        Path found = locate();
        if (found == null) {
            if (loadedFrom != null) {
                log.warn("{} is no longer found; using the built-in defaults", FILE_NAME);
            }
            loadedFrom = null;
            props = new Properties();
            return;
        }
        try {
            FileTime t = Files.getLastModifiedTime(found);
            if (found.equals(loadedFrom) && t.equals(loadedTime)) {
                return;
            }
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(found)) {
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
            props = p;
            loadedFrom = found;
            loadedTime = t;
            log.info("Email and report settings read from {}", found.toAbsolutePath());
        } catch (Exception e) {
            log.warn("Could not read {}: {}", found, e.toString());
        }
    }

    private static Path locate() {
        List<Path> candidates = new ArrayList<>();
        String env = System.getenv("SUMA_CONFIG_FILE");
        if (env != null && !env.isBlank()) {
            candidates.add(Path.of(env.trim()));
        }
        candidates.add(Path.of("config", FILE_NAME));
        candidates.add(Path.of("..", "config", FILE_NAME));
        candidates.add(Path.of("/app/config", FILE_NAME));
        for (Path p : candidates) {
            try {
                if (Files.isRegularFile(p)) {
                    return p;
                }
            } catch (RuntimeException ignored) {
                // a path this system cannot use
            }
        }
        return null;
    }
}

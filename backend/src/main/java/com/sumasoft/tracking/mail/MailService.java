package com.sumasoft.tracking.mail;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Sends email (Step 43).
 * - The server, port, sender and user name come from config/mail-and-reports.properties (see MailSettings).
 * - The email PASSWORD is never stored: it comes from SUMA_MAIL_PASSWORD, typed when the backend starts.
 * - Every email is written to email_log: sent, failed, or "not set up" (no password given).
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    public static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");

    private final MailSettings settings;
    private final JdbcTemplate jdbc;
    private final ExecutorService background = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "suma-mail");
        t.setDaemon(true);
        return t;
    });

    public MailService(MailSettings settings, DataSource dataSource) {
        this.settings = settings;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** One file attached to an email. */
    public record Attachment(String fileName, byte[] data, String contentType) {
    }

    /** What happened. status: SENT, FAILED or NOT_SET_UP. */
    public record Result(String status, String message) {
        public boolean sent() {
            return "SENT".equals(status);
        }
    }

    public record Status(boolean ready, boolean enabled, String from, String host, int port, String username,
                         String configFile, String publicUrl, String problem) {
    }

    public Status status() {
        String password = System.getenv("SUMA_MAIL_PASSWORD");
        boolean enabled = settings.flag("mail.enabled", true);
        String host = settings.text("mail.host", "");
        String from = settings.text("mail.from", "");
        String problem = null;
        if (settings.path() == null) {
            problem = "The file config/" + MailSettings.FILE_NAME + " was not found.";
        } else if (!enabled) {
            problem = "Email is switched off (mail.enabled=false in " + MailSettings.FILE_NAME + ").";
        } else if (host.isBlank() || !EMAIL.matcher(from).matches()) {
            problem = "mail.host or mail.from is empty or wrong in " + MailSettings.FILE_NAME + ".";
        } else if (password == null || password.isBlank()) {
            problem = "The email password was not typed when the backend was started.";
        }
        return new Status(problem == null, enabled, from, host, port(), settings.text("mail.username", from),
                settings.path(), publicUrl(), problem);
    }

    public boolean ready() {
        return status().ready();
    }

    /** Sends now and waits for the answer. Safe to call: it never throws. */
    public Result send(String kind, List<String> to, List<String> cc, String subject, String text, String html,
                       Attachment attachment, Long sentByUserId) {
        String toText = String.join(", ", to) + (cc == null || cc.isEmpty() ? "" : " | cc: " + String.join(", ", cc));
        String attachName = attachment == null ? null : attachment.fileName();
        Status st = status();
        if (!st.ready()) {
            logRow(kind, toText, subject, attachName, "NOT_SET_UP", st.problem(), sentByUserId);
            return new Result("NOT_SET_UP", "Email is not set up yet: " + st.problem());
        }
        try {
            JavaMailSenderImpl sender = sender(st);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(st.from(), settings.text("mail.fromName", "Suma Tracking"));
            helper.setTo(to.toArray(new String[0]));
            if (cc != null && !cc.isEmpty()) {
                helper.setCc(cc.toArray(new String[0]));
            }
            helper.setSubject(subject);
            helper.setText(text, html);
            if (attachment != null) {
                helper.addAttachment(attachment.fileName(), new ByteArrayResource(attachment.data()), attachment.contentType());
            }
            sender.send(message);
            logRow(kind, toText, subject, attachName, "SENT", null, sentByUserId);
            return new Result("SENT", "Email sent");
        } catch (Exception e) {
            String why = friendly(e);
            log.warn("Email '{}' to {} failed: {}", subject, toText, e.toString());
            logRow(kind, toText, subject, attachName, "FAILED", why, sentByUserId);
            return new Result("FAILED", "The email could not be sent: " + why);
        }
    }

    /** Sends in the background (the caller does not wait). Used for password reset links. */
    public void sendLater(String kind, List<String> to, String subject, String text, String html) {
        background.submit(() -> send(kind, to, List.of(), subject, text, html, null, null));
    }

    private JavaMailSenderImpl sender(Status st) {
        JavaMailSenderImpl s = new JavaMailSenderImpl();
        s.setHost(st.host());
        s.setPort(st.port());
        s.setUsername(st.username());
        s.setPassword(System.getenv("SUMA_MAIL_PASSWORD"));
        s.setDefaultEncoding("UTF-8");
        Properties p = s.getJavaMailProperties();
        p.put("mail.transport.protocol", "smtp");
        p.put("mail.smtp.auth", "true");
        if (st.port() == 465) {
            p.put("mail.smtp.ssl.enable", "true");
        } else {
            p.put("mail.smtp.starttls.enable", "true");
            p.put("mail.smtp.starttls.required", "true");
        }
        p.put("mail.smtp.connectiontimeout", "15000");
        p.put("mail.smtp.timeout", "20000");
        p.put("mail.smtp.writetimeout", "30000");
        return s;
    }

    private int port() {
        return settings.number("mail.port", 587, 1, 65535);
    }

    /** The website address used in links inside emails (no slash at the end). */
    public String publicUrl() {
        String u = settings.text("app.publicUrl", "http://localhost:3000");
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    private static String friendly(Exception e) {
        String all = String.valueOf(e) + " " + (e.getCause() == null ? "" : e.getCause());
        if (all.contains("Authentication") || all.contains("535") || all.contains("AUTH")) {
            return "the email server did not accept the user name or password (for Gmail or Office 365 you may need an app password)";
        }
        if (all.contains("UnknownHost") || all.contains("Couldn't connect") || all.contains("ConnectException")
                || all.contains("timed out")) {
            return "cannot reach the email server. Check mail.host and mail.port in " + MailSettings.FILE_NAME + ", and the internet";
        }
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return m.length() > 300 ? m.substring(0, 300) : m;
    }

    private void logRow(String kind, String to, String subject, String attach, String status, String error, Long by) {
        try {
            jdbc.update("INSERT INTO email_log (kind, to_addresses, subject, attachment_name, status, error, sent_by_user_id) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)", kind, clip(to, 2000), clip(subject, 300), clip(attach, 200), status,
                    clip(error, 500), by);
        } catch (RuntimeException e) {
            log.warn("Could not write the email log", e);
        }
    }

    public static String clip(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    /** Escapes text for HTML emails. */
    public static String html(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** A simple branded email body. */
    public static String wrap(String title, String bodyHtml) {
        return "<div style=\"font-family:Segoe UI,Arial,sans-serif;background:#F4F5F7;padding:24px\">"
                + "<div style=\"max-width:560px;margin:0 auto;background:#ffffff;border-radius:12px;padding:24px;border:1px solid #E3E6EB\">"
                + "<div style=\"font-weight:700;color:#0B8A71;letter-spacing:.08em;font-size:12px\">SUMA TRACKING</div>"
                + "<h2 style=\"margin:8px 0 16px;color:#14161B\">" + html(title) + "</h2>"
                + "<div style=\"color:#14161B;font-size:14px;line-height:1.6\">" + bodyHtml + "</div>"
                + "<p style=\"color:#5E6573;font-size:12px;margin-top:24px\">This email was sent by Suma Tracking, the internal tool of Suma Soft.</p>"
                + "</div></div>";
    }
}

package com.sumasoft.tracking.auth;

import com.sumasoft.tracking.audit.AuditService;
import com.sumasoft.tracking.mail.MailService;
import com.sumasoft.tracking.mail.MailSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Forgot password?" by email (Step 43).
 * - The answer is the same whether or not the email has an account, so nobody can test which emails exist.
 * - The link holds a random token; only its SHA-256 fingerprint is saved. It works once, for a short time.
 * - Asking is limited per email address and per IP address.
 * - After a reset: the account is unlocked, every open session is ended, and a "password changed" email is sent.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_PER_IP_PER_HOUR = 20;

    public static final String SENT_MESSAGE = "If this email belongs to an active account, a reset link has been sent. "
            + "Check your inbox and the Spam folder.";

    private final MailService mail;
    private final MailSettings settings;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public PasswordResetService(MailService mail, MailSettings settings, PasswordEncoder passwordEncoder,
                                AuditService audit, DataSource dataSource) {
        this.mail = mail;
        this.settings = settings;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public record CheckResult(boolean valid, String email) {
    }

    @Transactional
    public String requestLink(String rawEmail, String ip) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        if (!MailService.EMAIL.matcher(email).matches() || email.length() > 254) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address, for example name@sumasoft.com");
        }
        if (!mail.ready()) {
            log.warn("Password reset asked, but email is not set up: {}", mail.status().problem());
            return "Email is not set up yet, so a reset link cannot be sent. Please ask your Super Admin for a temporary password.";
        }
        Instant now = Instant.now();
        Timestamp hourAgo = Timestamp.from(now.minus(Duration.ofHours(1)));
        Long fromIp = jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE requested_ip = ? AND created_at > ?",
                Long.class, ip, hourAgo);
        if (fromIp != null && fromIp >= MAX_PER_IP_PER_HOUR) {
            log.warn("Too many password reset requests from {}", ip);
            return SENT_MESSAGE;
        }
        List<Map<String, Object>> users = jdbc.queryForList(
                "SELECT id, full_name, email FROM users WHERE lower(email) = ? AND status = 'ACTIVE'", email);
        if (users.isEmpty()) {
            return SENT_MESSAGE;
        }
        long userId = ((Number) users.get(0).get("id")).longValue();
        String name = (String) users.get(0).get("full_name");
        String to = (String) users.get(0).get("email");
        Long recent = jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ? AND created_at > ?",
                Long.class, userId, hourAgo);
        int max = settings.number("mail.resetMaxPerHour", 3, 1, 20);
        if (recent != null && recent >= max) {
            log.info("Password reset limit reached for user {}", userId);
            return SENT_MESSAGE;
        }
        int minutes = settings.number("mail.resetLinkMinutes", 30, 5, 1440);
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        // an older link stops working when a new one is asked for
        jdbc.update("UPDATE password_reset_tokens SET used_at = now() WHERE user_id = ? AND used_at IS NULL", userId);
        jdbc.update("INSERT INTO password_reset_tokens (user_id, token_hash, expires_at, requested_ip) VALUES (?, ?, ?, ?)",
                userId, sha256(token), Timestamp.from(now.plus(Duration.ofMinutes(minutes))), MailService.clip(ip, 64));
        String link = mail.publicUrl() + "/#/reset-password?token=" + token;
        String text = "Hello " + name + ",\n\nSomeone (hopefully you) asked to reset your Suma Tracking password.\n"
                + "Open this link to make a new password. It works for " + minutes + " minutes, one time only:\n\n" + link
                + "\n\nIf you did not ask for this, ignore this email. Your password stays the same.\n";
        String html = MailService.wrap("Reset your password",
                "<p>Hello " + MailService.html(name) + ",</p>"
                        + "<p>Someone (hopefully you) asked to reset your Suma Tracking password.</p>"
                        + "<p style=\"margin:24px 0\"><a href=\"" + MailService.html(link) + "\" style=\"background:#0B8A71;color:#ffffff;"
                        + "padding:12px 20px;border-radius:8px;text-decoration:none;font-weight:700\">Reset my password</a></p>"
                        + "<p>The link works for " + minutes + " minutes, one time only.</p>"
                        + "<p style=\"color:#5E6573\">If you did not ask for this, ignore this email. Your password stays the same. "
                        + "Never forward this email to anyone.</p>");
        mail.sendLater("PASSWORD_RESET", List.of(to), "Reset your Suma Tracking password", text, html);
        return SENT_MESSAGE;
    }

    @Transactional(readOnly = true)
    public CheckResult check(String token) {
        Map<String, Object> row = findValid(token);
        return row == null ? new CheckResult(false, null) : new CheckResult(true, mask((String) row.get("email")));
    }

    @Transactional
    public String reset(String token, String newPassword, String ip, String userAgent) {
        Map<String, Object> row = findValid(token);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This reset link is not valid any more (used or too old). Ask for a new one.");
        }
        long tokenId = ((Number) row.get("token_id")).longValue();
        long userId = ((Number) row.get("user_id")).longValue();
        String email = (String) row.get("email");
        AuthSupport.checkPasswordRules(newPassword, email);
        int claimed = jdbc.update("UPDATE password_reset_tokens SET used_at = now() WHERE id = ? AND used_at IS NULL", tokenId);
        if (claimed != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This reset link was already used. Ask for a new one.");
        }
        jdbc.update("UPDATE users SET password_hash = ?, must_change_password = FALSE, password_changed_at = now(), "
                        + "failed_login_count = 0, locked_until = NULL, updated_at = now(), version = version + 1 WHERE id = ?",
                passwordEncoder.encode(newPassword), userId);
        jdbc.update("UPDATE password_reset_tokens SET used_at = now() WHERE user_id = ? AND used_at IS NULL", userId);
        int ended = jdbc.update("UPDATE user_sessions SET revoked_at = now() WHERE user_id = ? AND revoked_at IS NULL", userId);
        jdbc.update("INSERT INTO login_history (user_id, email_attempted, event, ip_address, user_agent) VALUES (?, ?, 'PASSWORD_RESET', ?, ?)",
                userId, email, MailService.clip(ip, 45), MailService.clip(userAgent, 300));
        audit.record("USER_PASSWORD_RESET_BY_EMAIL", "USER", userId, null, null, null, Map.of("sessionsEnded", ended));
        mail.sendLater("PASSWORD_CHANGED", List.of(email), "Your Suma Tracking password was changed",
                "Your Suma Tracking password was just changed using a reset link. If this was not you, tell your Super Admin at once.",
                MailService.wrap("Your password was changed",
                        "<p>Your Suma Tracking password was just changed using a reset link.</p>"
                                + "<p><strong>If this was not you, tell your Super Admin at once.</strong></p>"));
        return "Your new password is saved. Sign in with it now.";
    }

    private Map<String, Object> findValid(String token) {
        if (token == null || !token.matches("^[A-Za-z0-9_-]{30,64}$")) {
            return null;
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT t.id AS token_id, t.user_id, u.email FROM password_reset_tokens t "
                + "JOIN users u ON u.id = t.user_id WHERE t.token_hash = ? AND t.used_at IS NULL AND t.expires_at > now() "
                + "AND u.status = 'ACTIVE'", sha256(token));
        return rows.isEmpty() ? null : rows.get(0);
    }

    static String mask(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at < 1) {
            return "";
        }
        String name = email.substring(0, at);
        String shown = name.length() <= 2 ? name.substring(0, 1) : name.substring(0, 2);
        return shown + "*****" + email.substring(at);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

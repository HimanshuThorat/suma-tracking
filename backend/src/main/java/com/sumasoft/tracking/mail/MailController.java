package com.sumasoft.tracking.mail;

import com.sumasoft.tracking.auth.AccessService;
import com.sumasoft.tracking.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * Email set-up checks (Step 43). Super Admin only.
 *   GET  /api/mail/status   is email ready, and from which address
 *   POST /api/mail/test     sends a test email to yourself
 *   GET  /api/mail/log      the last 100 emails (sent or failed)
 */
@RestController
@RequestMapping("/api/mail")
public class MailController {

    private final MailService mail;
    private final AccessService access;
    private final JdbcTemplate jdbc;

    public MailController(MailService mail, AccessService access, DataSource dataSource) {
        this.mail = mail;
        this.access = access;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @GetMapping("/status")
    public MailService.Status status() {
        requireSuperAdmin();
        return mail.status();
    }

    @PostMapping("/test")
    public MailService.Result test() {
        AuthenticatedUser me = requireSuperAdmin();
        return mail.send("TEST", List.of(me.email()), List.of(), "Suma Tracking test email",
                "This is a test email from Suma Tracking. Email is working.",
                MailService.wrap("Email is working", "<p>This is a test email from Suma Tracking.</p>"), null, me.userId());
    }

    @GetMapping("/log")
    public List<Map<String, Object>> log() {
        requireSuperAdmin();
        return jdbc.queryForList("SELECT id, kind, to_addresses AS \"to\", subject, attachment_name AS \"attachment\", status, error, "
                + "created_at AS \"at\" FROM email_log ORDER BY created_at DESC LIMIT 100");
    }

    private AuthenticatedUser requireSuperAdmin() {
        AuthenticatedUser me = access.current();
        if (!access.isSuperAdmin(me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Super Admin can do this");
        }
        return me;
    }
}

package com.sumasoft.tracking.attendance;

import com.sumasoft.tracking.attendance.AttendanceDtos.BreakItem;
import com.sumasoft.tracking.attendance.AttendanceDtos.Day;
import com.sumasoft.tracking.audit.AuditService;
import com.sumasoft.tracking.auth.AccessService;
import com.sumasoft.tracking.auth.AuthenticatedUser;
import com.sumasoft.tracking.mail.MailService;
import com.sumasoft.tracking.mail.MailSettings;
import com.sumasoft.tracking.notification.NotificationService;
import com.sumasoft.tracking.rbac.RbacService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Attendance reports (Step 43).
 * - Daily: login, logout, working time, total break, every break with its timing, idle checks, alerts.
 * - Monthly: days present / not marked, total and average working time and break, average login and logout.
 * - Download as Excel (CSV), or send by email with the report attached.
 * - The monthly report goes by itself to the Project Manager and Team Leader of each project (see
 *   config/mail-and-reports.properties for the day, the hour and extra people).
 * Who may: Super Admin always; any role given ATTENDANCE_REPORTS. People are limited to shared projects.
 */
@Service
public class AttendanceReportService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceReportService.class);
    public static final String PERMISSION = "ATTENDANCE_REPORTS";
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter NICE_DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter NICE_MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final Map<String, String> BREAK_NAMES = Map.of("TEA", "Tea", "LUNCH", "Lunch", "DINNER", "Dinner",
            "REFRESHMENT", "Refreshment");
    private static final Map<String, String> FLAG_NAMES = Map.ofEntries(
            Map.entry("BREAK_OVERTIME", "Break over time"), Map.entry("NO_BREAK", "Compulsory break not taken"),
            Map.entry("SHORT_BREAK", "Break too short"), Map.entry("SHORT_HOURS", "Fewer hours"),
            Map.entry("FORGOT_PUNCH_OUT", "Forgot to punch out"), Map.entry("NEW_DEVICE", "New device"),
            Map.entry("NEW_IP", "New IP address"), Map.entry("SHARED_DEVICE", "Shared computer"),
            Map.entry("UNKNOWN_DEVICE", "Device not recognised"), Map.entry("IDLE", "Idle check"),
            Map.entry("BREAK_DUE", "Break due"));

    private final AttendanceService attendance;
    private final AccessService access;
    private final RbacService rbac;
    private final MailService mail;
    private final MailSettings settings;
    private final NotificationService notifications;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public AttendanceReportService(AttendanceService attendance, AccessService access, RbacService rbac, MailService mail,
                                   MailSettings settings, NotificationService notifications, AuditService audit,
                                   DataSource dataSource) {
        this.attendance = attendance;
        this.access = access;
        this.rbac = rbac;
        this.mail = mail;
        this.settings = settings;
        this.notifications = notifications;
        this.audit = audit;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ================================================================= what the screen and the files show

    public record DailyRow(long userId, String employeeCode, String fullName, String roleName, String status,
                           String punchIn, String punchOut, long workedMinutes, long breakMinutes, int breakAllowedMinutes,
                           int breakCount, String breaks, int idleChecks, List<String> alerts) {
    }

    public record DailyReport(LocalDate date, List<DailyRow> rows, int present, int notMarked, int absent, int withAlerts) {
    }

    public record MonthlyRow(long userId, String employeeCode, String fullName, String roleName, int daysWorked,
                             int daysPresent, int daysNotMarked, long workedMinutes, long avgWorkedMinutes,
                             long breakMinutes, long avgBreakMinutes, String avgPunchIn, String avgPunchOut,
                             int breakOvertimeDays, int noBreakDays, int forgotPunchOutDays, int idleChecks,
                             int deviceAlerts) {
    }

    public record MonthlyReport(YearMonth month, List<MonthlyRow> rows, int people, long totalWorkedMinutes) {
    }

    public record Recipient(long userId, String fullName, String email, String roleName) {
    }

    public record SendForm(
            @NotBlank @Pattern(regexp = "^(DAILY|MONTHLY)$", message = "Choose daily or monthly") String type,
            @Pattern(regexp = "^\\d{4}-\\d{2}(-\\d{2})?$", message = "Pick a date or a month") String period,
            @NotNull(message = "Add at least one person in To") @Size(min = 1, max = 50, message = "Add 1 to 50 people in To") List<Long> to,
            @Size(max = 50) List<Long> cc,
            @NotBlank(message = "Write a subject") @Size(max = 200, message = "Subject: at most 200 characters") String subject,
            @Size(max = 3000, message = "Message: at most 3000 characters") String message) {
    }

    public record SendResult(String status, String message, List<String> to, String attachment) {
    }

    public record CsvFile(String fileName, byte[] data) {
    }

    // ================================================================= for the signed-in person

    @Transactional(readOnly = true)
    public DailyReport daily(LocalDate date) {
        AuthenticatedUser me = require();
        return buildDaily(date == null ? LocalDate.now(AttendanceService.ZONE) : date, attendance.visibleUsers(me));
    }

    @Transactional(readOnly = true)
    public MonthlyReport monthly(YearMonth month) {
        AuthenticatedUser me = require();
        return buildMonthly(month == null ? YearMonth.now(AttendanceService.ZONE) : month, attendance.visibleUsers(me));
    }

    @Transactional(readOnly = true)
    public CsvFile dailyCsv(LocalDate date) {
        DailyReport r = daily(date);
        audit.record("ATTENDANCE_REPORT_DOWNLOADED", "ATTENDANCE_REPORT", null, null, null, null,
                Map.of("type", "DAILY", "period", r.date().toString()));
        return new CsvFile("attendance-daily-" + r.date() + ".csv", dailyCsvBytes(r));
    }

    @Transactional(readOnly = true)
    public CsvFile monthlyCsv(YearMonth month) {
        MonthlyReport r = monthly(month);
        audit.record("ATTENDANCE_REPORT_DOWNLOADED", "ATTENDANCE_REPORT", null, null, null, null,
                Map.of("type", "MONTHLY", "period", r.month().toString()));
        return new CsvFile("attendance-monthly-" + r.month() + ".csv", monthlyCsvBytes(r, null));
    }

    @Transactional(readOnly = true)
    public List<Recipient> recipients(String q) {
        require();
        String like = "%" + (q == null ? "" : q.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "")) + "%";
        return jdbc.query("SELECT u.id, u.full_name, u.email, r.name AS role_name FROM users u JOIN roles r ON r.id = u.role_id "
                        + "WHERE u.status = 'ACTIVE' AND (lower(u.full_name) LIKE ? OR lower(u.email) LIKE ?) ORDER BY u.full_name LIMIT 20",
                (rs, n) -> new Recipient(rs.getLong("id"), rs.getString("full_name"), rs.getString("email"), rs.getString("role_name")),
                like, like);
    }

    /** "Send report": the report is attached by itself; To and Cc must be active people of the tool. */
    @Transactional
    public SendResult send(SendForm form) {
        AuthenticatedUser me = require();
        int max = settings.number("reports.maxRecipients", 10, 1, 50);
        List<Long> toIds = distinct(form.to());
        List<Long> ccIds = distinct(form.cc());
        ccIds.removeAll(toIds);
        if (toIds.size() + ccIds.size() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can send to at most " + max + " people at a time");
        }
        List<String> to = emailsOf(toIds);
        List<String> cc = emailsOf(ccIds);
        if (to.size() != toIds.size() || cc.size() != ccIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Some people are not active users of Suma Tracking");
        }
        Set<Long> scope = attendance.visibleUsers(me);
        String title;
        CsvFile file;
        if ("DAILY".equals(form.type())) {
            LocalDate date = parseDate(form.period());
            DailyReport r = buildDaily(date, scope);
            title = "Daily attendance report " + date.format(NICE_DAY);
            file = new CsvFile("attendance-daily-" + date + ".csv", dailyCsvBytes(r));
        } else {
            YearMonth month = parseMonth(form.period());
            MonthlyReport r = buildMonthly(month, scope);
            title = "Monthly attendance report " + month.format(NICE_MONTH);
            file = new CsvFile("attendance-monthly-" + month + ".csv", monthlyCsvBytes(r, null));
        }
        String message = form.message() == null ? "" : form.message().trim();
        String html = MailService.wrap(title, (message.isEmpty() ? "" : "<p>" + MailService.html(message).replace("\n", "<br>") + "</p>")
                + "<p>The report is attached (" + MailService.html(file.fileName()) + "). It opens in Excel.</p>"
                + "<p style=\"color:#5E6573\">Sent by " + MailService.html(nameOf(me.userId())) + ".</p>");
        String text = (message.isEmpty() ? "" : message + "\n\n") + "The report is attached: " + file.fileName()
                + "\n\nSent by " + nameOf(me.userId()) + ".";
        MailService.Result res = mail.send("ATTENDANCE_REPORT", to, cc, form.subject().trim(), text, html,
                new MailService.Attachment(file.fileName(), file.data(), "text/csv"), me.userId());
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("type", form.type());
        facts.put("period", form.period());
        facts.put("to", String.join(", ", to));
        facts.put("cc", String.join(", ", cc));
        facts.put("status", res.status());
        audit.record("ATTENDANCE_REPORT_EMAILED", "ATTENDANCE_REPORT", null, null, null, null, facts);
        List<String> all = new ArrayList<>(to);
        all.addAll(cc);
        return new SendResult(res.status(), res.message(), all, file.fileName());
    }

    // ================================================================= automatic emails (called every 10 minutes)

    public void autoSend() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            ZonedDateTime now = ZonedDateTime.now(AttendanceService.ZONE);
            if (settings.flag("reports.monthly.enabled", true)) {
                YearMonth current = YearMonth.from(now);
                int day = Math.min(settings.number("reports.monthly.day", 1, 1, 31), current.lengthOfMonth());
                int hour = settings.number("reports.monthly.hour", 9, 0, 23);
                if (now.getDayOfMonth() >= day && (now.getDayOfMonth() > day || now.getHour() >= hour)) {
                    monthlyToLeads(current.minusMonths(1));
                }
            }
            if (settings.flag("reports.daily.enabled", false) && now.getHour() >= settings.number("reports.daily.hour", 20, 0, 23)) {
                dailyToList(now.toLocalDate());
            }
        } catch (RuntimeException e) {
            log.warn("Automatic attendance report failed", e);
        } finally {
            running.set(false);
        }
    }

    private void monthlyToLeads(YearMonth month) {
        if (!mail.ready()) {
            return; // tried again later, when email is set up
        }
        Date monthDate = Date.valueOf(month.atDay(1));
        List<Map<String, Object>> projects = jdbc.queryForList("SELECT p.id, p.project_name FROM projects p WHERE p.status = 'ACTIVE' "
                + "AND EXISTS (SELECT 1 FROM project_assignments pa WHERE pa.project_id = p.id AND pa.active) ORDER BY p.id");
        for (Map<String, Object> p : projects) {
            long projectId = ((Number) p.get("id")).longValue();
            String projectName = (String) p.get("project_name");
            if (!claim("INSERT INTO attendance_report_runs (report_month, project_id, status) VALUES (?, ?, 'SENDING') ON CONFLICT DO NOTHING",
                    "UPDATE attendance_report_runs SET status = 'SENDING', attempts = attempts + 1, updated_at = now() "
                            + "WHERE report_month = ? AND project_id = ? AND status = 'FAILED' AND attempts < 3 "
                            + "AND updated_at < now() - interval '30 minutes'", monthDate, projectId)) {
                continue;
            }
            List<String> to = emailsOf(new ArrayList<>(notifications.projectLeads(projectId)));
            for (String extra : settings.emails("reports.monthly.alsoSendTo")) {
                if (!to.contains(extra)) {
                    to.add(extra);
                }
            }
            if (to.isEmpty()) {
                jdbc.update("UPDATE attendance_report_runs SET status = 'NO_LEADS', updated_at = now() WHERE report_month = ? AND project_id = ?",
                        monthDate, projectId);
                continue;
            }
            Set<Long> members = new LinkedHashSet<>(jdbc.queryForList("SELECT DISTINCT pa.user_id FROM project_assignments pa "
                    + "JOIN users u ON u.id = pa.user_id WHERE pa.project_id = ? AND pa.active AND u.status = 'ACTIVE'", Long.class, projectId));
            MonthlyReport r = buildMonthly(month, members);
            String fileName = "attendance-monthly-" + month + "-" + safe(projectName) + ".csv";
            String title = "Monthly attendance report " + month.format(NICE_MONTH) + " - " + projectName;
            MailService.Result res = mail.send("MONTHLY_REPORT", to, List.of(), subject(title),
                    "Hello,\n\nThe attendance report of " + projectName + " for " + month.format(NICE_MONTH) + " is attached ("
                            + fileName + "). It opens in Excel.\n\nThis email is sent by itself on the first day of every month.",
                    MailService.wrap(title, "<p>Hello,</p><p>The attendance report of <strong>" + MailService.html(projectName) + "</strong> for "
                            + month.format(NICE_MONTH) + " is attached. It opens in Excel.</p>"
                            + "<p>People: " + r.people() + " &middot; Total working time: " + hours(r.totalWorkedMinutes()) + "</p>"
                            + "<p style=\"color:#5E6573\">This email is sent by itself on the first day of every month.</p>"),
                    new MailService.Attachment(fileName, monthlyCsvBytes(r, projectName), "text/csv"), null);
            jdbc.update("UPDATE attendance_report_runs SET status = ?, last_error = ?, updated_at = now() WHERE report_month = ? AND project_id = ?",
                    res.sent() ? "SENT" : "FAILED", res.sent() ? null : MailService.clip(res.message(), 500), monthDate, projectId);
        }
    }

    private void dailyToList(LocalDate date) {
        List<String> to = settings.emails("reports.daily.sendTo");
        if (to.isEmpty() || !mail.ready()) {
            return;
        }
        Date d = Date.valueOf(date);
        if (!claim("INSERT INTO attendance_daily_report_runs (report_date, status) VALUES (?, 'SENDING') ON CONFLICT DO NOTHING",
                "UPDATE attendance_daily_report_runs SET status = 'SENDING', attempts = attempts + 1, updated_at = now() "
                        + "WHERE report_date = ? AND status = 'FAILED' AND attempts < 3 AND updated_at < now() - interval '30 minutes'", d)) {
            return;
        }
        Set<Long> everyone = new LinkedHashSet<>(jdbc.queryForList("SELECT id FROM users WHERE status = 'ACTIVE' ORDER BY full_name", Long.class));
        DailyReport r = buildDaily(date, everyone);
        String fileName = "attendance-daily-" + date + ".csv";
        String title = "Daily attendance report " + date.format(NICE_DAY);
        MailService.Result res = mail.send("DAILY_REPORT", to, List.of(), subject(title),
                "The attendance report of " + date.format(NICE_DAY) + " is attached (" + fileName + ").",
                MailService.wrap(title, "<p>The attendance report is attached. It opens in Excel.</p>"
                        + "<p>Present: " + r.present() + " &middot; Not marked: " + r.notMarked() + " &middot; Absent: " + r.absent()
                        + " &middot; With alerts: " + r.withAlerts() + "</p>"),
                new MailService.Attachment(fileName, dailyCsvBytes(r), "text/csv"), null);
        jdbc.update("UPDATE attendance_daily_report_runs SET status = ?, last_error = ?, updated_at = now() WHERE report_date = ?",
                res.sent() ? "SENT" : "FAILED", res.sent() ? null : MailService.clip(res.message(), 500), d);
    }

    /** True when this run is ours to send: a new row, or a failed one that may be tried again. */
    private boolean claim(String insertSql, String retrySql, Object... key) {
        if (jdbc.update(insertSql, key) == 1) {
            return true;
        }
        return jdbc.update(retrySql, key) == 1;
    }

    // ================================================================= building the reports

    DailyReport buildDaily(LocalDate date, Collection<Long> userIds) {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(AttendanceService.ZONE);
        List<DailyRow> rows = new ArrayList<>();
        int present = 0;
        int notMarked = 0;
        int absent = 0;
        int alerts = 0;
        for (Long id : userIds) {
            Map<String, Object> u = attendance.person(id);
            if (u == null) {
                continue;
            }
            List<AttendanceService.DayRow> found = jdbc.query("SELECT " + AttendanceService.DAY_COLS
                    + " FROM attendance_days WHERE user_id = ? AND work_date = ?", AttendanceService::dayRow, id, Date.valueOf(date));
            String code = (String) u.get("employee_code");
            String name = (String) u.get("full_name");
            String role = (String) u.get("role_name");
            if (found.isEmpty()) {
                absent++;
                rows.add(new DailyRow(id, code, name, role, date.equals(today) ? "Not punched in" : "Absent", "", "", 0, 0,
                        attendance.schedule(id).breakMinutes(), 0, "", 0, List.of()));
                continue;
            }
            Day d = attendance.toDay(found.get(0), now);
            String status;
            if (!"CLOSED".equals(d.status())) {
                status = "ON_BREAK".equals(d.status()) ? "On break" : "Working";
            } else if (Boolean.TRUE.equals(d.marked())) {
                status = "Present";
                present++;
            } else {
                status = "Not marked";
                notMarked++;
            }
            List<String> breakText = new ArrayList<>();
            int count = 0;
            for (BreakItem b : d.breaks()) {
                if ("OFF".equals(b.kind())) {
                    breakText.add("Away (punched out) " + time(b.startedAt()) + "-" + time(b.endedAt()));
                    continue;
                }
                count++;
                String type = b.breakType() == null ? "Break" : BREAK_NAMES.getOrDefault(b.breakType(), "Break");
                breakText.add(type + " " + time(b.startedAt()) + "-" + (b.endedAt() == null ? "now" : time(b.endedAt()))
                        + " (" + (b.seconds() / 60) + " min)");
            }
            Integer idle = jdbc.queryForObject("SELECT COUNT(*) FROM attendance_idle WHERE day_id = ?", Integer.class, d.id());
            List<String> flagNames = new ArrayList<>();
            for (String f : d.flags()) {
                flagNames.add(FLAG_NAMES.getOrDefault(f, f));
            }
            if (!flagNames.isEmpty()) {
                alerts++;
            }
            rows.add(new DailyRow(id, code, name, role, status, time(d.punchInAt()), d.punchOutAt() == null ? "" : time(d.punchOutAt()),
                    d.workedSeconds() / 60, d.breakSeconds() / 60, d.breakMinutes(), count, String.join("; ", breakText),
                    idle == null ? 0 : idle, flagNames));
        }
        return new DailyReport(date, rows, present, notMarked, absent, alerts);
    }

    MonthlyReport buildMonthly(YearMonth month, Collection<Long> userIds) {
        Instant now = Instant.now();
        List<MonthlyRow> rows = new ArrayList<>();
        long total = 0;
        for (Long id : userIds) {
            Map<String, Object> u = attendance.person(id);
            if (u == null) {
                continue;
            }
            List<AttendanceService.DayRow> days = jdbc.query("SELECT " + AttendanceService.DAY_COLS
                            + " FROM attendance_days WHERE user_id = ? AND work_date BETWEEN ? AND ? ORDER BY work_date",
                    AttendanceService::dayRow, id, Date.valueOf(month.atDay(1)), Date.valueOf(month.atEndOfMonth()));
            int present = 0;
            int notMarked = 0;
            long worked = 0;
            long brk = 0;
            long inSum = 0;
            long outSum = 0;
            int outCount = 0;
            int overtime = 0;
            int noBreak = 0;
            int forgot = 0;
            int device = 0;
            for (AttendanceService.DayRow row : days) {
                Day d = attendance.toDay(row, now);
                if ("CLOSED".equals(d.status())) {
                    if (Boolean.TRUE.equals(d.marked())) {
                        present++;
                    } else {
                        notMarked++;
                    }
                }
                worked += d.workedSeconds() / 60;
                brk += d.breakSeconds() / 60;
                inSum += minuteOfDay(d.punchInAt());
                if (d.punchOutAt() != null) {
                    outSum += minuteOfDay(d.punchOutAt());
                    outCount++;
                }
                List<String> f = d.flags();
                overtime += f.contains("BREAK_OVERTIME") ? 1 : 0;
                noBreak += f.contains("NO_BREAK") || f.contains("SHORT_BREAK") ? 1 : 0;
                forgot += f.contains("FORGOT_PUNCH_OUT") ? 1 : 0;
                device += f.contains("NEW_DEVICE") || f.contains("SHARED_DEVICE") || f.contains("NEW_IP") ? 1 : 0;
            }
            Integer idle = jdbc.queryForObject("SELECT COUNT(*) FROM attendance_idle i JOIN attendance_days d ON d.id = i.day_id "
                            + "WHERE i.user_id = ? AND d.work_date BETWEEN ? AND ?", Integer.class, id,
                    Date.valueOf(month.atDay(1)), Date.valueOf(month.atEndOfMonth()));
            int n = days.size();
            total += worked;
            rows.add(new MonthlyRow(id, (String) u.get("employee_code"), (String) u.get("full_name"), (String) u.get("role_name"),
                    n, present, notMarked, worked, n == 0 ? 0 : worked / n, brk, n == 0 ? 0 : brk / n,
                    n == 0 ? "" : clock(inSum / n), outCount == 0 ? "" : clock(outSum / outCount),
                    overtime, noBreak, forgot, idle == null ? 0 : idle, device));
        }
        return new MonthlyReport(month, rows, rows.size(), total);
    }

    // ================================================================= Excel (CSV) files

    byte[] dailyCsvBytes(DailyReport r) {
        Csv c = new Csv();
        c.line("Suma Tracking - Daily attendance report", r.date().format(NICE_DAY));
        c.line("Present", String.valueOf(r.present()), "Not marked", String.valueOf(r.notMarked()),
                "Absent", String.valueOf(r.absent()), "With alerts", String.valueOf(r.withAlerts()));
        c.line();
        c.line("Employee ID", "Name", "Role", "Status", "Login (punch in)", "Logout (punch out)", "Working time",
                "Total break", "Break allowed", "Number of breaks", "Break timings", "Idle checks", "Alerts");
        for (DailyRow x : r.rows()) {
            c.line(x.employeeCode(), x.fullName(), x.roleName(), x.status(), x.punchIn(), x.punchOut(),
                    x.punchIn().isEmpty() ? "" : hours(x.workedMinutes()), x.punchIn().isEmpty() ? "" : hours(x.breakMinutes()),
                    hours(x.breakAllowedMinutes()), String.valueOf(x.breakCount()), x.breaks(), String.valueOf(x.idleChecks()),
                    String.join("; ", x.alerts()));
        }
        return c.bytes();
    }

    byte[] monthlyCsvBytes(MonthlyReport r, String projectName) {
        Csv c = new Csv();
        c.line("Suma Tracking - Monthly attendance report", r.month().format(NICE_MONTH), projectName == null ? "" : projectName);
        c.line("People", String.valueOf(r.people()), "Total working time", hours(r.totalWorkedMinutes()));
        c.line();
        c.line("Employee ID", "Name", "Role", "Days worked", "Days present", "Days not marked", "Total working time",
                "Average working time per day", "Total break", "Average break per day", "Average login", "Average logout",
                "Days with break over time", "Days without full break", "Days forgot punch-out", "Idle checks", "Days with device alerts");
        for (MonthlyRow x : r.rows()) {
            c.line(x.employeeCode(), x.fullName(), x.roleName(), String.valueOf(x.daysWorked()), String.valueOf(x.daysPresent()),
                    String.valueOf(x.daysNotMarked()), hours(x.workedMinutes()), hours(x.avgWorkedMinutes()), hours(x.breakMinutes()),
                    hours(x.avgBreakMinutes()), x.avgPunchIn(), x.avgPunchOut(), String.valueOf(x.breakOvertimeDays()),
                    String.valueOf(x.noBreakDays()), String.valueOf(x.forgotPunchOutDays()), String.valueOf(x.idleChecks()),
                    String.valueOf(x.deviceAlerts()));
        }
        return c.bytes();
    }

    /** A CSV that Excel opens correctly (UTF-8 with BOM, quoted cells, formulas made harmless). */
    static final class Csv {
        private final StringBuilder sb = new StringBuilder("\ufeff");

        void line(String... cells) {
            for (int i = 0; i < cells.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(cell(cells[i]));
            }
            sb.append("\r\n");
        }

        static String cell(String v) {
            String s = v == null ? "" : v;
            if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
                s = "'" + s; // stops Excel from treating the text as a formula
            }
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }

        byte[] bytes() {
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    // ================================================================= small helpers

    private AuthenticatedUser require() {
        AuthenticatedUser me = access.current();
        rbac.require(me, PERMISSION);
        return me;
    }

    private List<String> emailsOf(List<Long> ids) {
        List<String> out = new ArrayList<>();
        for (Long id : ids) {
            List<String> e = jdbc.queryForList("SELECT email FROM users WHERE id = ? AND status = 'ACTIVE'", String.class, id);
            if (!e.isEmpty() && !out.contains(e.get(0).toLowerCase(Locale.ROOT))) {
                out.add(e.get(0).toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static List<Long> distinct(List<Long> ids) {
        List<Long> out = new ArrayList<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null && !out.contains(id)) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    private String nameOf(long id) {
        List<String> n = jdbc.queryForList("SELECT full_name FROM users WHERE id = ?", String.class, id);
        return n.isEmpty() ? "Suma Tracking" : n.get(0);
    }

    private String subject(String title) {
        String prefix = settings.text("reports.subjectPrefix", "[Suma Tracking]");
        return MailService.clip((prefix.isEmpty() ? "" : prefix + " ") + title, 300);
    }

    static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a date, for example 2026-11-24");
        }
    }

    static YearMonth parseMonth(String s) {
        try {
            return YearMonth.parse(s.length() > 7 ? s.substring(0, 7) : s);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a month, for example 2026-11");
        }
    }

    private static String time(Instant t) {
        return t == null ? "" : t.atZone(AttendanceService.ZONE).format(HM);
    }

    private static long minuteOfDay(Instant t) {
        LocalTime lt = t.atZone(AttendanceService.ZONE).toLocalTime();
        return lt.getHour() * 60L + lt.getMinute();
    }

    private static String clock(long minuteOfDay) {
        return String.format("%02d:%02d", (minuteOfDay / 60) % 24, minuteOfDay % 60);
    }

    static String hours(long minutes) {
        return (minutes / 60) + "h " + String.format("%02d", minutes % 60) + "m";
    }

    private static String safe(String s) {
        String v = s == null ? "project" : s.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return v.isEmpty() ? "project" : v.toLowerCase(Locale.ROOT);
    }
}

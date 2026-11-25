package com.sumasoft.tracking.attendance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/** Checks open working days every minute (Step 41), and sends the automatic report emails (Step 43). */
@Configuration
public class AttendanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(AttendanceScheduler.class);

    private final AttendanceService service;
    private final AttendanceReportService reports;

    public AttendanceScheduler(AttendanceService service, AttendanceReportService reports) {
        this.service = service;
        this.reports = reports;
    }

    /** Step 43: the automatic monthly (and, when switched on, daily) report emails. Checked every 10 minutes. */
    @Scheduled(initialDelay = 120_000, fixedDelay = 600_000)
    public void reports() {
        try {
            reports.autoSend();
        } catch (RuntimeException e) {
            log.warn("Automatic report check failed", e);
        }
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void tick() {
        try {
            service.monitor();
        } catch (RuntimeException e) {
            log.warn("Attendance check failed", e);
        }
    }
}

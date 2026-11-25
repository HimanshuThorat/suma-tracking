package com.sumasoft.tracking.attendance;

import com.sumasoft.tracking.attendance.AttendanceReportService.CsvFile;
import com.sumasoft.tracking.attendance.AttendanceReportService.DailyReport;
import com.sumasoft.tracking.attendance.AttendanceReportService.MonthlyReport;
import com.sumasoft.tracking.attendance.AttendanceReportService.Recipient;
import com.sumasoft.tracking.attendance.AttendanceReportService.SendForm;
import com.sumasoft.tracking.attendance.AttendanceReportService.SendResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Attendance reports (Step 43). Needs ATTENDANCE_REPORTS (Super Admin always has it).
 *   GET  /api/attendance/reports/daily?date=2026-11-24             the report on screen
 *   GET  /api/attendance/reports/daily/download?date=2026-11-24    Excel (CSV) file
 *   GET  /api/attendance/reports/monthly?month=2026-11
 *   GET  /api/attendance/reports/monthly/download?month=2026-11
 *   GET  /api/attendance/reports/recipients?q=rav                  people for the To box
 *   POST /api/attendance/reports/send                              {type, period, to, cc, subject, message}
 */
@RestController
@RequestMapping("/api/attendance/reports")
public class AttendanceReportController {

    private final AttendanceReportService service;

    public AttendanceReportController(AttendanceReportService service) {
        this.service = service;
    }

    @GetMapping("/daily")
    public DailyReport daily(@RequestParam(required = false) String date) {
        return service.daily(date == null || date.isBlank() ? null : AttendanceReportService.parseDate(date));
    }

    @GetMapping("/daily/download")
    public ResponseEntity<byte[]> dailyDownload(@RequestParam(required = false) String date) {
        return file(service.dailyCsv(date == null || date.isBlank() ? null : AttendanceReportService.parseDate(date)));
    }

    @GetMapping("/monthly")
    public MonthlyReport monthly(@RequestParam(required = false) String month) {
        return service.monthly(month == null || month.isBlank() ? null : AttendanceReportService.parseMonth(month));
    }

    @GetMapping("/monthly/download")
    public ResponseEntity<byte[]> monthlyDownload(@RequestParam(required = false) String month) {
        return file(service.monthlyCsv(month == null || month.isBlank() ? null : AttendanceReportService.parseMonth(month)));
    }

    @GetMapping("/recipients")
    public List<Recipient> recipients(@RequestParam(required = false) String q) {
        return service.recipients(q);
    }

    @PostMapping("/send")
    public SendResult send(@Valid @RequestBody SendForm form) {
        return service.send(form);
    }

    private static ResponseEntity<byte[]> file(CsvFile f) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + f.fileName() + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(f.data());
    }
}

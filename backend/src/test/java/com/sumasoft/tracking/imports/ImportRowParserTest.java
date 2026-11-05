package com.sumasoft.tracking.imports;

import com.sumasoft.tracking.ticket.ImportedTicketValues;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Automatic checks of the import row checker and the CSV reader.
 * ONLY made-up data is used here (no real customer data in the code or on GitHub).
 */
class ImportRowParserTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** Same mappings as the Codility project (V7 migration). */
    private static final ImportRowParser PARSER = new ImportRowParser(new ImportRowParser.Context(
            IST,
            Map.of("solved", "RESOLVED", "pending", "PENDING_USER", "open", "PENDING_L2",
                    "merged", "MERGED", "hold", "ON_HOLD", "closed", "CLOSED"),
            Map.ofEntries(
                    Map.entry("OPEN", "OPEN"), Map.entry("IN_PROGRESS", "IN_PROGRESS"),
                    Map.entry("PENDING_USER", "PENDING"), Map.entry("PENDING_L1", "PENDING"),
                    Map.entry("PENDING_L2", "PENDING"), Map.entry("PENDING_THIRD_PARTY", "PENDING"),
                    Map.entry("ON_HOLD", "PENDING"), Map.entry("RESOLVED", "RESOLVED"),
                    Map.entry("CLOSED", "CLOSED"), Map.entry("MERGED", "CLOSED")),
            Map.of("urgent", "CRITICAL", "high", "HIGH", "normal", "MEDIUM", "low", "LOW"),
            Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW")));

    /** A clean, made-up row. Each test changes only what it needs. */
    private static Map<String, String> goodRow() {
        Map<String, String> m = new HashMap<>();
        m.put(ImportColumns.SR_NO, "1");
        m.put(ImportColumns.TICKET_ID, "900001");
        m.put(ImportColumns.CREATED_DATE, "5/1/2026");        // 1 May 2026 = Friday
        m.put(ImportColumns.DAY, "Friday");
        m.put(ImportColumns.CREATED_TIME, "2:05:10 PM");
        m.put(ImportColumns.TIMEFRAME, "2-3pm");
        m.put(ImportColumns.ASSIGNEE, "Test Agent");
        m.put(ImportColumns.ESCALATED, "No");
        m.put(ImportColumns.CLOSED_DATE, "5/1/2026");
        m.put(ImportColumns.CLOSED_TIME, "3:10:10 PM");
        m.put(ImportColumns.RESOLUTION_TIME, "1:05:00");
        m.put(ImportColumns.STATUS, "Solved");
        m.put(ImportColumns.PRIORITY, "Normal");
        m.put(ImportColumns.SOURCE, "Email");
        return m;
    }

    private static ImportRowParser.Result parse(Map<String, String> m) {
        return PARSER.parse(new RawRow(2, m));
    }

    private static Instant ist(int y, int mo, int d, int h, int mi, int s) {
        return LocalDateTime.of(y, mo, d, h, mi, s).atZone(IST).toInstant();
    }

    private static boolean hasIssue(ImportRowParser.Result r, String severity, String column, String text) {
        return r.issues().stream().anyMatch(i -> i.severity().equals(severity)
                && java.util.Objects.equals(i.columnName(), column) && i.message().contains(text));
    }

    @Test
    void cleanSolvedRow() {
        ImportRowParser.Result r = parse(goodRow());
        ImportedTicketValues v = r.values();
        assertNotNull(v, "row should be imported");
        assertEquals(List.of(), r.issues(), "no issues expected");
        assertEquals("900001", v.externalTicketId());
        assertEquals(1, v.importRowNumber());
        assertEquals(ist(2026, 5, 1, 14, 5, 10), v.createdAtSource());
        assertEquals(ist(2026, 5, 1, 15, 10, 10), v.resolvedAt());
        assertNull(v.closedAt(), "Solved is resolved, not closed");
        assertEquals("RESOLVED", v.statusCode());
        assertEquals("MEDIUM", v.priorityCode());
        assertEquals("Email", v.channel());
        assertFalse(v.escalated(), "escalated");
    }

    @Test
    void firstNumberAbove12MeansDayMonth() {
        Map<String, String> m = goodRow();
        m.put(ImportColumns.CREATED_DATE, "30/04/2026");
        m.put(ImportColumns.DAY, "Thursday");
        m.put(ImportColumns.CLOSED_DATE, "30/04/2026");
        ImportRowParser.Result r = parse(m);
        assertEquals(ist(2026, 4, 30, 14, 5, 10), r.values().createdAtSource());
        assertEquals(List.of(), r.issues(), "no issues expected");
    }

    @Test
    void dayColumnDecidesAnUnclearDate() {
        Map<String, String> m = goodRow();
        m.put(ImportColumns.DAY, "Monday");                   // 5 Jan 2026 is a Monday, 1 May is a Friday
        m.remove(ImportColumns.CLOSED_DATE);
        m.remove(ImportColumns.CLOSED_TIME);
        m.remove(ImportColumns.RESOLUTION_TIME);
        m.put(ImportColumns.STATUS, "Pending");
        ImportRowParser.Result r = parse(m);
        assertEquals(ist(2026, 1, 5, 14, 5, 10), r.values().createdAtSource());
        assertTrue(hasIssue(r, "WARNING", ImportColumns.CREATED_DATE, "day/month"), "warning expected");
    }

    @Test
    void dayThatFitsNeitherReadingIsReported() {
        Map<String, String> m = goodRow();
        m.put(ImportColumns.DAY, "Sunday");
        ImportRowParser.Result r = parse(m);
        assertEquals(ist(2026, 5, 1, 14, 5, 10), r.values().createdAtSource());
        assertTrue(hasIssue(r, "WARNING", ImportColumns.DAY, "fits neither"), "warning expected");
    }

    @Test
    void closureBeforeCreationIsNotUsed() {
        Map<String, String> m = goodRow();
        m.put(ImportColumns.CLOSED_TIME, "1:00:00 PM");
        m.remove(ImportColumns.RESOLUTION_TIME);
        ImportRowParser.Result r = parse(m);
        assertNotNull(r.values(), "row is still imported");
        assertNull(r.values().resolvedAt(), "closure must not be used");
        assertTrue(hasIssue(r, "WARNING", ImportColumns.CLOSED_DATE, "before creation"), "warning expected");
    }

    @Test
    void openWithClosureDateButNoTime() {
        Map<String, String> m = goodRow();
        m.put(ImportColumns.STATUS, "Open");
        m.remove(ImportColumns.CLOSED_TIME);
        m.remove(ImportColumns.RESOLUTION_TIME);
        ImportRowParser.Result r = parse(m);
        assertEquals("PENDING_L2", r.values().statusCode());
        assertNull(r.values().resolvedAt(), "resolvedAt");
        assertNull(r.values().closedAt(), "closedAt");
        assertTrue(hasIssue(r, "WARNING", ImportColumns.CLOSED_TIME, "needs both"), "warning expected");
    }

    @Test
    void pendingWithFullClosureDropsTheClosure() {
        Map<String, String> m = goodRow();
        m.put(ImportColumns.STATUS, "Pending");
        ImportRowParser.Result r = parse(m);
        assertEquals("PENDING_USER", r.values().statusCode());
        assertNull(r.values().resolvedAt(), "resolvedAt");
        assertTrue(hasIssue(r, "WARNING", ImportColumns.STATUS, "not finished"), "warning expected");
    }

    @Test
    void mergedIsClosed() {
        Map<String, String> m = goodRow();
        m.put(ImportColumns.STATUS, "Merged");
        ImportRowParser.Result r = parse(m);
        assertEquals("MERGED", r.values().statusCode());
        assertEquals(ist(2026, 5, 1, 15, 10, 10), r.values().closedAt());
        assertNull(r.values().resolvedAt(), "resolvedAt");
    }

    @Test
    void holdIsOnHold() {
        Map<String, String> m = goodRow();
        m.put(ImportColumns.STATUS, "hold");
        m.remove(ImportColumns.CLOSED_DATE);
        m.remove(ImportColumns.CLOSED_TIME);
        m.remove(ImportColumns.RESOLUTION_TIME);
        ImportRowParser.Result r = parse(m);
        assertEquals("ON_HOLD", r.values().statusCode());
        assertEquals(List.of(), r.issues(), "no issues expected");
    }

    @Test
    void unknownStatusIsAnError() {
        Map<String, String> m = goodRow();
        m.put(ImportColumns.STATUS, "Waiting for moon");
        ImportRowParser.Result r = parse(m);
        assertTrue(r.hasError(), "row must not be imported");
        assertTrue(hasIssue(r, "ERROR", ImportColumns.STATUS, "Unknown status"), "error expected");
    }

    @Test
    void emptyTicketIdIsAnError() {
        Map<String, String> m = goodRow();
        m.remove(ImportColumns.TICKET_ID);
        assertTrue(parse(m).hasError(), "row must not be imported");
    }

    @Test
    void excelNumberTicketIdIsCleaned() {
        Map<String, String> m = goodRow();
        m.put(ImportColumns.TICKET_ID, "900002.0");
        assertEquals("900002", parse(m).values().externalTicketId());
    }

    @Test
    void timeframeThatDoesNotMatchIsReported() {
        Map<String, String> m = goodRow();
        m.put(ImportColumns.TIMEFRAME, "9-10am");
        ImportRowParser.Result r = parse(m);
        assertEquals("9-10am", r.values().timeframeRaw());
        assertTrue(hasIssue(r, "WARNING", ImportColumns.TIMEFRAME, "does not match"), "warning expected");
    }

    @Test
    void excelErrorInResolutionTime() {
        Map<String, String> m = goodRow();
        m.put(ImportColumns.RESOLUTION_TIME, "#VALUE!");
        ImportRowParser.Result r = parse(m);
        assertNotNull(r.values().resolvedAt(), "closure still used");
        assertTrue(hasIssue(r, "WARNING", ImportColumns.RESOLUTION_TIME, "Excel error"), "warning expected");
    }

    @Test
    void wrongResolutionTimeIsReported() {
        Map<String, String> m = goodRow();
        m.put(ImportColumns.RESOLUTION_TIME, "-3:00:00");
        ImportRowParser.Result r = parse(m);
        assertTrue(hasIssue(r, "WARNING", ImportColumns.RESOLUTION_TIME, "1:05:00"), "warning expected");
    }

    @Test
    void realExcelDateCells() {
        Map<String, String> m = goodRow();
        m.put(ImportColumns.CREATED_DATE, "@D:2026-05-01T00:00:00");
        m.put(ImportColumns.CREATED_TIME, "@D:1899-12-31T14:05:10");
        ImportRowParser.Result r = parse(m);
        assertEquals(ist(2026, 5, 1, 14, 5, 10), r.values().createdAtSource());
        assertEquals(List.of(), r.issues(), "no issues expected");
    }

    @Test
    void missingTimeUsesMidnightWithWarning() {
        Map<String, String> m = goodRow();
        m.remove(ImportColumns.CREATED_TIME);
        m.remove(ImportColumns.TIMEFRAME);
        m.remove(ImportColumns.RESOLUTION_TIME);
        ImportRowParser.Result r = parse(m);
        assertEquals(ist(2026, 5, 1, 0, 0, 0), r.values().createdAtSource());
        assertTrue(hasIssue(r, "WARNING", ImportColumns.CREATED_TIME, "00:00"), "warning expected");
    }

    @Test
    void unknownPriorityIsLeftEmpty() {
        Map<String, String> m = goodRow();
        m.put(ImportColumns.PRIORITY, "Super");
        ImportRowParser.Result r = parse(m);
        assertNull(r.values().priorityCode(), "priority");
        assertTrue(hasIssue(r, "WARNING", ImportColumns.PRIORITY, "Unknown priority"), "warning expected");
    }

    // ---------------------------------------------------------------- CSV reader

    @Test
    void csvWithTitleRowAndQuotes() {
        String csv = "\uFEFFMonthly report,,,\n"
                + "Sr.no,Ticket ID,Date of Creation,Status,Topic,My Notes\n"
                + "1,900010,5/1/2026,Solved,\"Login, password\",x\n"
                + ",,,,,\n"
                + "2,900011,5/2/2026,Pending,\"Says \"\"hi\"\"\",\n";
        SpreadsheetReader.ReadResult res = SpreadsheetReader.read("test.csv", csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(2, res.rows().size(), "empty line skipped");
        RawRow first = res.rows().get(0);
        assertEquals(3, first.rowNumber(), "row number as seen in the file");
        assertEquals("Login, password", first.get(ImportColumns.TOPIC));
        assertEquals("Says \"hi\"", res.rows().get(1).get(ImportColumns.TOPIC));
        assertEquals(1, res.fileIssues().size(), "one unknown column");
        assertEquals("My Notes", res.fileIssues().get(0).rawValue());
    }

    @Test
    void csvWithoutRequiredHeadingsIsRefused() {
        String csv = "Ticket ID,Topic\n1,abc\n";
        assertThrows(ImportFileException.class,
                () -> SpreadsheetReader.read("test.csv", csv.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void otherFileTypesAreRefused() {
        assertThrows(ImportFileException.class,
                () -> SpreadsheetReader.read("notes.txt", "hello".getBytes(StandardCharsets.UTF_8)));
    }
}
/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.auditing.trail;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.helper.Constants;

import java.time.LocalDateTime;

/**
 * The Audit Trails page's API.
 *
 * <p>Read-only, plus one endpoint for reporting an export. There is deliberately no way to create,
 * amend or delete an arbitrary event through HTTP — events are written by the services that perform
 * the actions, never by a client claiming an action happened.
 */
@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class AuditTrailController {

    private final AuditTrailService auditTrailService;
    private final AuditService auditService;

    /**
     * The activity log, filtered and paged in the database.
     *
     * @param background set by the page's auto-refresh tick. Those are not recorded as access —
     *                   a tab left open on a 30-second refresh would otherwise write nearly three
     *                   thousand "viewed the audit trail" rows a day and bury everything else.
     *                   Deliberate searches are recorded; polling is not.
     */
    @GetMapping("/audit/trails")
    public ResponseEntity<Page<AuditTrailResponse>> listTrails(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false, defaultValue = "25") int pageSize,
            @RequestParam(required = false, defaultValue = "0") int pageNumber,
            @RequestParam(required = false, defaultValue = "false") boolean background
    ) {
        // Newest first: an audit log is read from the present backwards.
        PageRequest pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "occurredAt"));

        Page<AuditTrailResponse> results = auditTrailService.search(
                search, eventType, module, severity, actorId, entityType, entityId, from, to, pageable);

        if (!background) {
            // Whoever reads the log appears in the log.
            //
            // Recorded after the search, not before, so the page a user is looking at does not open
            // with a row saying they just opened it. It shows up on their next look, which is the
            // point — the trail is for whoever reads it afterwards.
            auditService.record(AuditEventType.SYSTEM, AuditModule.SYSTEM, "AUDIT_TRAIL_VIEWED",
                    "Viewed the audit trail" + describeFilters(search, eventType, module, severity),
                    AuditSeverity.INFO);
        }

        return new ResponseEntity<>(results, HttpStatus.OK);
    }

    /**
     * The four tiles, counted over the rows the current filters describe.
     *
     * <p>A page of twenty-five rows cannot produce them, and counting the whole table regardless —
     * which is what this did — put unfiltered totals above a filtered listing. It takes the same
     * parameters as the listing so the two cannot describe different things.
     *
     * <p>No {@code background} flag: the summary is always fetched beside a listing that carries one,
     * so recording it separately would double every entry.
     */
    @GetMapping("/audit/trails/summary")
    public ResponseEntity<AuditTrailSummary> summary(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return new ResponseEntity<>(
                auditTrailService.summary(
                        search, eventType, module, severity, actorId, entityType, entityId, from, to),
                HttpStatus.OK);
    }

    /**
     * Records that a report or listing was exported.
     *
     * <p>Ordinary page views are not audited — they would be the overwhelming majority of rows and
     * would bury the events that matter. Exports are the exception, because that is data leaving the
     * building: "who downloaded the full asset register on the 3rd" is a question someone will
     * eventually ask.
     *
     * <p>Exports are generated in the browser from rows already fetched, so this is client-reported
     * and therefore best-effort — it establishes intent, not proof. The fetch that supplied the rows
     * went through the ordinary permission checks regardless.
     */
    @PostMapping("/audit/exports")
    public ResponseEntity<Void> recordExport(@RequestBody ExportAuditRequest request) {
        auditService.record(
                AuditEventType.SYSTEM,
                AuditTrailService.moduleOrSystem(request.module()),
                "REPORT_EXPORTED",
                "Exported " + safe(request.reportName(), "a report")
                        + " as " + safe(request.format(), "a file")
                        + (request.rowCount() == null ? "" : " (" + request.rowCount() + " rows)"),
                AuditSeverity.WARNING);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    /** What the client says it exported. */
    public record ExportAuditRequest(String module, String reportName, String format, Integer rowCount) {
    }

    private static String safe(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /** Keeps the recorded sentence useful without repeating the whole query string. */
    private static String describeFilters(String search, String eventType, String module, String severity) {
        StringBuilder detail = new StringBuilder();
        appendIfFiltered(detail, "search", search);
        appendIfFiltered(detail, "event", eventType);
        appendIfFiltered(detail, "module", module);
        appendIfFiltered(detail, "severity", severity);
        return detail.isEmpty() ? "" : " filtered by " + detail;
    }

    private static void appendIfFiltered(StringBuilder detail, String label, String value) {
        if (value == null || value.isBlank() || "All".equalsIgnoreCase(value)) {
            return;
        }
        if (!detail.isEmpty()) {
            detail.append(", ");
        }
        detail.append(label).append(" = ").append(value);
    }
}

/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.auditing.trail;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reads the activity log for the Audit Trails page.
 *
 * <p>Read-only by design — the write path is {@link AuditService} and nothing here can modify an
 * event. See {@link AuditEvent} on why the table is append-only.
 */
@Service
@RequiredArgsConstructor
public class AuditTrailService {

    private final AuditEventRepository repository;

    @Transactional(readOnly = true)
    public Page<AuditTrailResponse> search(String search,
                                           String eventType,
                                           String module,
                                           String severity,
                                           Long actorId,
                                           String entityType,
                                           Long entityId,
                                           LocalDateTime from,
                                           LocalDateTime to,
                                           Pageable pageable) {
        return repository.search(
                        blankToNull(search),
                        parseEventType(eventType),
                        parseModule(module),
                        parseSeverity(severity),
                        actorId,
                        blankToNull(entityType),
                        entityId,
                        from,
                        to,
                        pageable)
                .map(AuditTrailResponse::from);
    }

    /**
     * The four tiles, over the same rows the table is showing.
     *
     * <p>This took no filters at all, so the figures above a narrowed table described the whole log:
     * filter to "critical events in Movement last week" and the tiles still read the totals for
     * everything. Two kinds of number on one screen with nothing to distinguish them — the fault the
     * movements register had before its tiles were counted over the matching set.
     *
     * <p>"Active actors" counts distinct actors <em>in the result</em> rather than "since midnight",
     * which is what it meant before. Under a date filter the old reading was simply a different
     * question from the one the filter asked.
     */
    @Transactional(readOnly = true)
    public AuditTrailSummary summary(String search,
                                     String eventType,
                                     String module,
                                     String severity,
                                     Long actorId,
                                     String entityType,
                                     Long entityId,
                                     LocalDateTime from,
                                     LocalDateTime to) {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();

        List<Object[]> result = repository.summarise(
                blankToNull(search),
                parseEventType(eventType),
                parseModule(module),
                parseSeverity(severity),
                actorId,
                blankToNull(entityType),
                entityId,
                from,
                to,
                startOfToday,
                AuditSeverity.INFO);

        // An aggregate query always returns one row, but SUM over no rows is null rather than zero.
        Object[] row = result.isEmpty() ? new Object[] { 0L, 0L, 0L, 0L } : result.get(0);
        return new AuditTrailSummary(
                asLong(row[0]), asLong(row[1]), asLong(row[2]), asLong(row[3]));
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /**
     * The module a caller named, or {@link AuditModule#SYSTEM} when it named nothing recognisable.
     *
     * <p>For the export endpoint, where the module arrives as free text from the browser. An
     * unrecognised name lands the row under System rather than rejecting it: losing the record of an
     * export over a spelling is a worse outcome than filing it imprecisely.
     */
    public static AuditModule moduleOrSystem(String value) {
        AuditModule parsed = parseModule(value);
        return parsed == null ? AuditModule.SYSTEM : parsed;
    }

    // ─────────────────────────────────────────────────────────────────────────

    /*
     * The page sends the same lower-case tokens it renders chips from ("login", "critical") and the
     * module's display label ("Movement"). Parsing is tolerant of case and of the "All" sentinel the
     * dropdowns use for "no filter", and an unrecognised value means no filter rather than a 400 —
     * a stale bookmark should show the log, not an error.
     */

    private static AuditEventType parseEventType(String value) {
        String token = normalise(value);
        if (token == null) {
            return null;
        }
        for (AuditEventType candidate : AuditEventType.values()) {
            if (candidate.name().equalsIgnoreCase(token)) {
                return candidate;
            }
        }
        return null;
    }

    private static AuditModule parseModule(String value) {
        String token = normalise(value);
        if (token == null) {
            return null;
        }
        for (AuditModule candidate : AuditModule.values()) {
            if (candidate.name().equalsIgnoreCase(token) || candidate.getLabel().equalsIgnoreCase(token)) {
                return candidate;
            }
        }
        return null;
    }

    private static AuditSeverity parseSeverity(String value) {
        String token = normalise(value);
        if (token == null) {
            return null;
        }
        for (AuditSeverity candidate : AuditSeverity.values()) {
            if (candidate.name().equalsIgnoreCase(token)) {
                return candidate;
            }
        }
        return null;
    }

    private static String normalise(String value) {
        String trimmed = blankToNull(value);
        return (trimmed == null || "All".equalsIgnoreCase(trimmed)) ? null : trimmed;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}

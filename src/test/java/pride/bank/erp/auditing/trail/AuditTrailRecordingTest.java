package pride.bank.erp.auditing.trail;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the activity log's write-then-read round trip.
 *
 * <p>The recorder is called from services across every module and is meant to be incapable of being
 * the thing that fails — it joins the caller's transaction, so an exception from it would roll back
 * a movement or an asset assignment. These tests pin the two ways that could happen: an oversized
 * value hitting a column limit, and a missing value hitting a NOT NULL.
 *
 * <p>They also exercise {@code AuditEventRepository.search}, whose null-tolerant JPQL is the one
 * query behind the Audit Trails page. A malformed filter clause there would not surface until
 * someone opened the page.
 *
 * <p>Everything runs inside a rolled-back transaction, so no rows survive the test. That is also why
 * these exercise {@code record} rather than {@code recordAs}: the latter is deliberately
 * {@code REQUIRES_NEW} so a refused sign-in still gets recorded, which means it would commit for
 * real.
 */
@SpringBootTest
class AuditTrailRecordingTest {

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditTrailService auditTrailService;

    @Autowired
    private AuditEventRepository repository;

    @Test
    @Transactional
    void recordedEventIsFoundByEveryFilter() {
        String action = "TEST_ACTION_" + System.nanoTime();
        auditService.record(AuditEventType.APPROVED, AuditModule.MOVEMENT, action,
                "Approved a movement during a test", AuditSeverity.WARNING, "Movement", 4242L);

        // Each filter is asserted on its own, because the query's null-tolerance means a broken
        // clause is invisible whenever any other clause happens to narrow to the same row.
        assertThat(searchBy(action, null, null, null, null, null)).hasSize(1);
        assertThat(searchBy(null, "approved", null, null, null, null)).anyMatch(matches(action));
        assertThat(searchBy(null, null, "Movement", null, null, null)).anyMatch(matches(action));
        assertThat(searchBy(null, null, null, "warning", null, null)).anyMatch(matches(action));
        assertThat(searchBy(null, null, null, null, "Movement", 4242L)).anyMatch(matches(action));

        // A filter that excludes it must actually exclude it — otherwise the clause is a no-op.
        assertThat(searchBy(action, "login", null, null, null, null)).isEmpty();
        assertThat(searchBy(action, null, null, "critical", null, null)).isEmpty();
    }

    /**
     * "All" is what the page's dropdowns send for an unset filter, and a stale bookmark can carry a
     * value no longer in the enum. Both must mean "no filter" rather than "no results" or a 400.
     */
    @Test
    @Transactional
    void sentinelAndUnknownFilterValuesAreIgnored() {
        String action = "TEST_SENTINEL_" + System.nanoTime();
        auditService.record(AuditEventType.CREATED, AuditModule.ASSETS, action,
                "Created something during a test", AuditSeverity.INFO, null, null);

        assertThat(searchBy(action, "All", "All", "All", null, null)).hasSize(1);
        assertThat(searchBy(action, "not-an-event", "not-a-module", "not-a-severity", null, null)).hasSize(1);
    }

    /**
     * A description longer than its column must be truncated, not rejected.
     *
     * <p>Descriptions are assembled from user-supplied text — a rejection comment, a bypass reason,
     * an asset name. Letting one of those overflow would fail the insert and, because the recorder
     * shares the caller's transaction, take the business operation down with it.
     */
    @Test
    @Transactional
    void oversizedDescriptionIsTruncatedRatherThanFailing() {
        String action = "TEST_LONG_" + System.nanoTime();
        auditService.record(AuditEventType.UPDATED, AuditModule.STORE, action,
                "x".repeat(AuditEvent.DESCRIPTION_MAX * 3), AuditSeverity.INFO, null, null);

        Page<AuditTrailResponse> found = searchBy(action, null, null, null, null, null);
        assertThat(found).hasSize(1);
        assertThat(found.getContent().get(0).getDescription()).hasSize(AuditEvent.DESCRIPTION_MAX);
    }

    /** Null where the column is NOT NULL must become a placeholder, not a constraint violation. */
    @Test
    @Transactional
    void missingValuesFallBackInsteadOfFailing() {
        auditService.record(null, null, null, null, null, null, null);

        AuditEvent latest = repository.findAll(
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "id"))).getContent().get(0);

        assertThat(latest.getEventType()).isEqualTo(AuditEventType.SYSTEM);
        assertThat(latest.getModule()).isEqualTo(AuditModule.SYSTEM);
        assertThat(latest.getSeverity()).isEqualTo(AuditSeverity.INFO);
        assertThat(latest.getAction()).isNotBlank();
        assertThat(latest.getDescription()).isNotBlank();
        // No bound request and no authentication in a test, which is the same position a seeder or a
        // scheduled job is in. It must read as a deliberate statement, not a blank cell.
        assertThat(latest.getActorName()).isEqualTo("System");
    }

    /** The four tiles count over the whole table, so they must not depend on the current page. */
    @Test
    @Transactional
    void summaryCounts() {
        auditService.record(AuditEventType.DELETED, AuditModule.DISPOSAL, "TEST_SUMMARY",
                "Disposed something during a test", AuditSeverity.CRITICAL, null, null);

        AuditTrailSummary summary = auditTrailService.summary();
        assertThat(summary.totalEvents()).isPositive();
        assertThat(summary.eventsToday()).isPositive();
        assertThat(summary.flaggedEvents()).isPositive();
        assertThat(summary.activeActors()).isGreaterThanOrEqualTo(0);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Page<AuditTrailResponse> searchBy(String search, String eventType, String module,
                                              String severity, String entityType, Long entityId) {
        return auditTrailService.search(search, eventType, module, severity, null,
                entityType, entityId, LocalDateTime.now().minusHours(1), null,
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "occurredAt")));
    }

    private static java.util.function.Predicate<AuditTrailResponse> matches(String action) {
        return row -> action.equals(row.getAction());
    }
}

/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.auditing.trail;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * Reads over the activity log.
 *
 * <p>Note what is absent: no {@code delete}, no update. {@link JpaRepository} supplies {@code save},
 * which the recorder uses for inserts, but nothing in the application ever loads an event to modify
 * it. See {@link AuditEvent} on why the table is append-only.
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /**
     * The Audit Trails page's one query.
     *
     * <p>Filtered and paged <b>in SQL</b>, unlike the Movement report which narrows a fetched page in
     * the browser. That shortcut is fine over a few hundred movements and impossible here: this will
     * be the largest table in the database, growing on every write in the application, and it has no
     * natural ceiling.
     *
     * <p>Every filter is null-tolerant so one query serves all of them; the alternative is a
     * Specification builder, which for six flat predicates costs more to read than it saves.
     *
     * <p>The search term is matched against the sentence, the actor and the action code. Not against
     * module or event type — those have their own dropdowns, and folding them in here would make
     * typing "user" surface every row in the Users module.
     */
    @Query("""
            SELECT e FROM AuditEvent e
            WHERE (:eventType IS NULL OR e.eventType = :eventType)
              AND (:module IS NULL OR e.module = :module)
              AND (:severity IS NULL OR e.severity = :severity)
              AND (:actorId IS NULL OR e.actorId = :actorId)
              AND (:entityType IS NULL OR e.entityType = :entityType)
              AND (:entityId IS NULL OR e.entityId = :entityId)
              AND (:from IS NULL OR e.occurredAt >= :from)
              AND (:to IS NULL OR e.occurredAt <= :to)
              AND (:search IS NULL
                   OR LOWER(e.description) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(e.actorName)   LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(e.action)      LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<AuditEvent> search(
            @Param("search") String search,
            @Param("eventType") AuditEventType eventType,
            @Param("module") AuditModule module,
            @Param("severity") AuditSeverity severity,
            @Param("actorId") Long actorId,
            @Param("entityType") String entityType,
            @Param("entityId") Long entityId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    long countByOccurredAtGreaterThanEqual(LocalDateTime from);

    /** Feeds the "Flagged Events" tile — everything above routine. */
    long countBySeverityNot(AuditSeverity severity);

    /**
     * Distinct people who have appeared in the log since a date.
     *
     * <p>Counts ids, not rows, so someone doing forty things in a morning is one active user.
     */
    @Query("SELECT COUNT(DISTINCT e.actorId) FROM AuditEvent e "
            + "WHERE e.actorId IS NOT NULL AND e.occurredAt >= :from")
    long countDistinctActorsSince(@Param("from") LocalDateTime from);
}

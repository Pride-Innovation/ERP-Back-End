/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.auditing.trail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.user.User;

import java.time.LocalDateTime;

/**
 * The one way anything in this application writes to the activity log.
 *
 * <p>Injected into services across every module, so the surface is kept deliberately small: a
 * {@code record} for the ordinary case and a {@code recordAs} for events with no signed-in actor.
 * Reading the log is {@link AuditTrailService}'s job, so the widely-injected bean stays tiny.
 *
 * <h2>Transactions</h2>
 * {@link #record} joins the caller's transaction. That makes the trail exactly consistent with what
 * committed: no phantom "asset created" row for a creation that later rolled back, and no gap where
 * something succeeded unrecorded. The cost is that a failing audit insert would take the business
 * operation with it — so this class is defensive about its own inputs instead, truncating anything
 * oversized and refusing to put a null in a non-null column. It should not be capable of being the
 * thing that fails.
 *
 * <p>{@link #recordAs} uses {@code REQUIRES_NEW} because it exists for the opposite case: a failed
 * login must be recorded even though the login is about to throw and unwind everything around it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditEventRepository repository;
    private final AuditContext context;

    /** Shown when a call site passes nothing usable, rather than writing a null into a NOT NULL. */
    private static final String NO_DESCRIPTION = "(no description recorded)";

    /**
     * Records something the signed-in user did.
     *
     * @param entityType the kind of record affected ("Movement", "Asset"), for the per-record
     *                   history view; null when the event isn't about one record
     * @param entityId   its id, on the same terms
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditEventType eventType,
                       AuditModule module,
                       String action,
                       String description,
                       AuditSeverity severity,
                       String entityType,
                       Long entityId) {
        User actor = context.currentUser();
        write(eventType, module, action, description, severity, entityType, entityId,
                actor == null ? null : actor.getId(),
                displayName(actor),
                actor == null ? null : actor.getEmail(),
                actor == null || actor.getBranch() == null ? null : actor.getBranch().getId());
    }

    /** As {@link #record}, for events that aren't about one particular record. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditEventType eventType,
                       AuditModule module,
                       String action,
                       String description,
                       AuditSeverity severity) {
        record(eventType, module, action, description, severity, null, null);
    }

    /**
     * Records an event on behalf of a named actor, in its own transaction.
     *
     * <p>For authentication, where the security context is empty by definition — nobody is signed in
     * yet when a login succeeds, and nobody ever will be when one fails. The caller supplies whatever
     * identity it has: a resolved {@link User} for a success, or just the attempted email for a
     * failure against an address that may not even exist.
     *
     * <p>{@code REQUIRES_NEW} so that the throw which follows a refused login rolls back the login
     * attempt without taking the record of it along.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAs(User actor,
                         String actorEmail,
                         AuditEventType eventType,
                         AuditModule module,
                         String action,
                         String description,
                         AuditSeverity severity) {
        Long actorId = actor == null ? null : actor.getId();
        // The affected record is the user account itself, so a login lines up with everything else
        // that ever happened to that account in the per-record history.
        String entityType = actor == null ? null : "User";
        Long branchId = (actor == null || actor.getBranch() == null) ? null : actor.getBranch().getId();

        write(eventType, module, action, description, severity,
                entityType, actorId,
                actorId, displayName(actor),
                actor != null ? actor.getEmail() : actorEmail,
                branchId);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void write(AuditEventType eventType,
                       AuditModule module,
                       String action,
                       String description,
                       AuditSeverity severity,
                       String entityType,
                       Long entityId,
                       Long actorId,
                       String actorName,
                       String actorEmail,
                       Long branchId) {
        repository.save(AuditEvent.builder()
                .occurredAt(LocalDateTime.now())
                .eventType(eventType == null ? AuditEventType.SYSTEM : eventType)
                .module(module == null ? AuditModule.SYSTEM : module)
                .action(AuditContext.truncate(orDefault(action, "UNSPECIFIED"), 80))
                .description(AuditContext.truncate(
                        orDefault(description, NO_DESCRIPTION), AuditEvent.DESCRIPTION_MAX))
                .severity(severity == null ? AuditSeverity.INFO : severity)
                .actorId(actorId)
                .actorName(AuditContext.truncate(actorName, 150))
                .actorEmail(AuditContext.truncate(actorEmail, 150))
                .entityType(AuditContext.truncate(entityType, 60))
                .entityId(entityId)
                .ipAddress(context.clientIp())
                .userAgent(context.userAgent())
                .branchId(branchId)
                .build());
    }

    /**
     * The actor's name, copied rather than referenced.
     *
     * <p>"System" when there is nobody — a seeder, a scheduled job, an unauthenticated call. Leaving
     * it null would render as a blank cell that reads like missing data rather than a deliberate
     * statement that the application acted on its own.
     */
    private static String displayName(User user) {
        if (user == null) {
            return "System";
        }
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        if (!name.isEmpty()) {
            return name;
        }
        return user.getEmail() == null ? "Unknown" : user.getEmail();
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}

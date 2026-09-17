/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.auditing.trail;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One thing that happened, in one row.
 *
 * <p>Hibernate Envers already mirrors nineteen entities into {@code _aud} tables, and none of it can
 * serve an activity log. {@code revinfo} carries a revision number and a timestamp — no actor and no
 * IP address. Signing in changes no entity at all, so logins and logouts are invisible to it by
 * construction. And a revision says "column status_id went 12 → 13", never "approved movement #21".
 * Envers stays underneath as the field-level forensic record; this table is the narrative.
 *
 * <p><b>Append-only.</b> Nothing updates or deletes an {@code AuditEvent} — there is no setter path
 * out of {@link AuditTrailService} and no mutating endpoint. An audit trail that can be edited is
 * not one.
 *
 * <p><b>Deliberately not {@code @Audited}.</b> Mirroring this into {@code audit_event_aud} would
 * double every write to record changes to a table that never changes.
 */
@Entity
@Table(
        name = "audit_event",
        indexes = {
                // The default view is "newest first", and every filter narrows within a date range.
                @Index(name = "idx_audit_event_occurred", columnList = "occurred_at"),
                @Index(name = "idx_audit_event_type", columnList = "event_type"),
                @Index(name = "idx_audit_event_module", columnList = "module"),
                @Index(name = "idx_audit_event_actor", columnList = "actor_id"),
                // "everything that ever happened to asset 412" — the per-record history view.
                @Index(name = "idx_audit_event_entity", columnList = "entity_type,entity_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {

    /** Maximum length of {@link #description} — see {@code AuditTrailService.record}, which truncates. */
    public static final int DESCRIPTION_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * When it happened, not when it was written.
     *
     * <p>Set by the recorder rather than {@code @CreatedDate} so that a caller replaying or
     * back-dating an event can say so.
     */
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private AuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, length = 40)
    private AuditModule module;

    /**
     * Machine-readable code for the specific thing that happened, e.g.
     * {@code MOVEMENT_APPROVAL_BYPASSED}, {@code LOGIN_FAILED_BAD_PASSWORD}.
     *
     * <p>Separate from {@link #description} on purpose: the sentence is written for a person and will
     * be reworded over the years, so anything counting or alerting keys off this instead.
     */
    @Column(name = "action", nullable = false, length = 80)
    private String action;

    /** The sentence shown on screen: "Approved movement #21 (HO IT Store → Gulu Branch)". */
    @Column(name = "description", nullable = false, length = DESCRIPTION_MAX)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private AuditSeverity severity;

    /**
     * Who did it. Null for events with no signed-in actor — a failed login by an unknown address, a
     * seeder, a scheduled job.
     *
     * <p>A plain id rather than a {@code @ManyToOne}: this table is written on nearly every business
     * operation, and a mapped association would pull a User into the persistence context each time
     * for a value we have already copied below.
     */
    @Column(name = "actor_id")
    private Long actorId;

    /**
     * The actor's name as it read at the time.
     *
     * <p>Denormalised on purpose. Reading it back through the user table would mean that renaming
     * someone, or deleting them, silently rewrites who did what years ago. History should say what
     * it said then.
     */
    @Column(name = "actor_name", length = 150)
    private String actorName;

    @Column(name = "actor_email", length = 150)
    private String actorEmail;

    /** The kind of record affected ("Movement", "Asset"), for the per-record history view. */
    @Column(name = "entity_type", length = 60)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    /** 45 characters: the longest possible IPv6 literal, including an embedded IPv4 tail. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    /** The actor's branch at the time — the hook for scoping this page per branch later. */
    @Column(name = "branch_id")
    private Long branchId;
}

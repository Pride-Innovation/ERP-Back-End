/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.auditing.trail;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * One row of the Audit Trails page.
 *
 * <p>Enums are sent as the lower-case tokens the page's chip config is already keyed by
 * ({@code created}, {@code login}, {@code critical}) and the module as its display label, so the
 * frontend needs no translation table. The raw entity is not exposed: it carries a user agent and a
 * branch id that the table doesn't show, and shipping the whole row on every page would send them
 * anyway.
 */
@Data
@Builder
public class AuditTrailResponse {

    private Long id;
    private LocalDateTime timeStamp;
    /** Lower-case token matching the page's EVENT_CONFIG keys. */
    private String event;
    /** Display label — "Movement", "Assets". */
    private String module;
    private String action;
    private String description;
    private String actor;
    private Long actorId;
    private String actorEmail;
    private String entityType;
    private Long entityId;
    private String ipAddress;
    /** Lower-case token matching the page's SEVERITY_CONFIG keys. */
    private String severity;

    public static AuditTrailResponse from(AuditEvent event) {
        return AuditTrailResponse.builder()
                .id(event.getId())
                .timeStamp(event.getOccurredAt())
                .event(event.getEventType() == null ? null : event.getEventType().name().toLowerCase())
                .module(event.getModule() == null ? null : event.getModule().getLabel())
                .action(event.getAction())
                .description(event.getDescription())
                .actor(event.getActorName())
                .actorId(event.getActorId())
                .actorEmail(event.getActorEmail())
                .entityType(event.getEntityType())
                .entityId(event.getEntityId())
                .ipAddress(event.getIpAddress())
                .severity(event.getSeverity() == null ? null : event.getSeverity().name().toLowerCase())
                .build();
    }
}

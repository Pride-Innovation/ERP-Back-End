/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.movement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * The wire shape of one tier decision on a movement's approval ladder.
 *
 * <p>The trail endpoint used to return {@link MovementApprovalRecord} itself, which carries a LAZY
 * {@code movement}. Two separate things then opened that proxy while writing the response — Jackson
 * serialising the field, and Spring's DEBUG response logging calling Lombok's {@code toString()} —
 * and initialising it issues the full {@code Movement} load. With nine EAGER associations on
 * Movement, each user among them pulling branch/title/department/unit/roles, and
 * {@code Title.reportsTo} being EAGER and self-referencing, that load exceeds the database's
 * 61-table join limit and the request fails with a 500.
 *
 * <p>It only ever surfaced on movements that actually went through approval: an empty trail
 * serialises as {@code []} and never touches a proxy, so the endpoint looked healthy.
 *
 * <p>Deliberately omits the parent movement altogether — the caller already knows which movement it
 * asked about, and re-stating it is what dragged the graph in.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MovementApprovalRecordDTO {

    private Long id;

    /** The tier that acted, or must act for a PENDING row. */
    private MovementResponseDTO.UserSummary actor;

    private MovementApprovalAction action;

    /** The ladder role this tier represents (e.g. MANAGER), for the audit view. */
    private String tierRole;

    /** 0-based position of this tier in the resolved chain. */
    private int tierIndex;

    private String comment;

    private LocalDateTime createDate;
}

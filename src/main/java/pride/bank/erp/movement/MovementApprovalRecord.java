/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.movement;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.user.User;

import java.time.LocalDateTime;

/**
 * Audit trail of a movement's approval ladder — one row per tier decision (pending / approved /
 * rejected), mirroring the request's {@code RequestReport}. Purely append-only history.
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovementApprovalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movement_id", nullable = false)
    private Movement movement;

    /** The tier that acted (or must act, for PENDING). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20) NOT NULL")
    private MovementApprovalAction action;

    /** The ladder role this tier represents (e.g. MANAGER), for the audit view. */
    private String tierRole;

    /** 0-based position of this tier in the resolved chain. */
    private int tierIndex;

    @Column(length = 1000)
    private String comment;

    private LocalDateTime createDate;
}

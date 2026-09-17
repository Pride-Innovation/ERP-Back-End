/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config.seed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A one-time seeding step that has already run.
 *
 * <p>See {@code V19__seeder_state.sql} for why this exists: a seeder that re-applies grants on every
 * startup makes revocation impossible, and the existing seeders infer "already done" from a side
 * effect that is not always available.
 */
@Entity
@Table(name = "seeder_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeederState {

    /** The step's name. The marker is the identity, so there is nothing to generate. */
    @Id
    @Column(name = "marker", length = 120, nullable = false)
    private String marker;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    /** What it did, in words, for whoever reads this table wondering what happened on deploy day. */
    @Column(name = "note", length = 500)
    private String note;
}

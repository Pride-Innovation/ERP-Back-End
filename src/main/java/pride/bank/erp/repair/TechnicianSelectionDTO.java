/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.repair;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A technician as every form sends one: the kind first, then exactly one reference.
 *
 * <p>{@code technicianUserId} goes with INTERNAL, {@code technicianConsultantId} with EXTERNAL.
 * Sending both, or a kind with nothing chosen, is refused rather than guessed at - see
 * {@code RepairService#applyTechnician}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechnicianSelectionDTO {
    private TechnicianType technicianType;
    private Long technicianUserId;
    private Long technicianConsultantId;

    /** Whether anything was chosen at all. Nothing chosen is legitimate wherever it is optional. */
    public boolean isEmpty() {
        return technicianType == null && technicianUserId == null && technicianConsultantId == null;
    }
}

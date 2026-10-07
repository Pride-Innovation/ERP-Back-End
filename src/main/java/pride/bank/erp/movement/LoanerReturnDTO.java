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

/**
 * Handing a loaner back. Everything else - who holds it, where it goes - is derived; the only
 * inputs are a note and, when the return crosses a location and nobody can approve it, the
 * confirmation to proceed anyway.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanerReturnDTO implements ApprovalBypassRequest {
    private String remarks;
    private Boolean proceedWithoutApproval;
    private String bypassReason;
}

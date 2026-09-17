/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.unit;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The complete set of roles a unit should confer after this call.
 *
 * <p>Deliberately a replacement rather than an add/remove pair. Everything about this feature has to
 * be revokable, and "send me the list you want" makes revocation the same operation as granting —
 * there is no second endpoint that someone can forget to call, and no way to end up with a role
 * nobody remembers granting because the remove half failed quietly.
 *
 * <p>An empty list is meaningful and allowed: it strips the unit back to what it was before this
 * model existed, a group mailbox with a department.
 *
 * <p>Kept apart from {@link UnitDTO} on purpose. That one is the unit's name, department and group
 * email — an ordinary settings edit. Folding roles into it would mean any form that omitted the
 * field silently revoked every role the unit had, which is the classic shape of this bug.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnitRolesDTO {

    @NotNull(message = "Provide the roles this unit should confer; send an empty list to revoke all of them")
    private List<Long> roleIds;
}

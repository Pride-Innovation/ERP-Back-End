/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.authentication;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.role.Role;
import pride.bank.erp.title.Title;
import pride.bank.erp.unit.Unit;

import java.util.Set;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class AuthenticationResponse {
    private Long id;
    private String accessToken;
    private String refreshToken;
    private String firstName;
    private String lastName;
    private String email;
    private Title title;
    private String gender;
    /** Optional unit (e.g. Admin Unit, Infra Unit) the user belongs to — drives in-app visibility
     *  and eligibility for unit-routed workflow steps (acknowledge / issuance / approval). */
    private Unit unit;

    /**
     * The user's duty station.
     *
     * <p>Absent until now, and this response is written to session storage verbatim and read back
     * as the signed-in user everywhere in the browser. So {@code user.branch} was undefined for
     * every user in the bank: the dashboard's scope label fell through to "Your records" instead of
     * naming the branch, the hero icon picked the personal variant, and nothing client-side could
     * scope anything to a branch.
     */
    private Branch branch;

    /**
     * Roles granted on top of the one the user's title carries.
     *
     * <p>Also absent until now, with a larger consequence: effective permissions are
     * {@code title.role.permissions ∪ additionalRoles[].permissions}, and the browser could only
     * ever see the first half. Every permission granted through an additional role was invisible to
     * every widget, menu entry and button guard in the application — so the design that lets one
     * person hold several capabilities was only half connected.
     */
    private Set<Role> additionalRoles;
}

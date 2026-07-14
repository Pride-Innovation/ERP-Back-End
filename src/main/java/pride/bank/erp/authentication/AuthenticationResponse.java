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
import pride.bank.erp.title.Title;
import pride.bank.erp.unit.Unit;

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
}

/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.user;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class UserSearchCriteria {
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private Boolean blocked;
    private Boolean isEnabled;
    private Boolean isAccountNonLocked;
    private String name;
    private String staffNumber;
    private String title;
    /** Inclusive lower bound on createDate */
    private LocalDateTime createdAtFrom;
    /** Inclusive upper bound on createDate */
    private LocalDateTime createdAtTo;
}

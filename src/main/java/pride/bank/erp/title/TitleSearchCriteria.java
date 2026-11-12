/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.title;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class TitleSearchCriteria {
    private String name;
    private String shortCode;
    private Long reportsToId;
    private Long roleId;

    /** Null = no filter. With {@link #workplaceNotSet} true, only titles with no workplace. */
    private TitleWorkplace workplace;
    private boolean workplaceNotSet;

    private Long departmentId;
    private boolean noDepartment;
}

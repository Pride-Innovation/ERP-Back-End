/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.branch;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class BranchSearchCriteria {
    private String name;
    private Long regionId;
    private Long districtId;
    private Long branchManagerId;
    private Long branchOperationsManagerId;
    private Long relationshipManagerId;
    private Long creditAdministratorId;
}

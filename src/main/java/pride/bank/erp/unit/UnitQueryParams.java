/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.unit;

import lombok.Getter;
import lombok.Setter;

import static pride.bank.erp.helper.Constants.ASCENDING_SORT_DIRECTION;

@Getter
@Setter
public class UnitQueryParams {
    private int pageSize = 10;
    private int pageNumber = 0;
    private String sortBy = "id";
    private String sortDirection = ASCENDING_SORT_DIRECTION;
    private String name;
    /** Optional — restrict the result set to a single department (the units-under-department picker). */
    private Long departmentId;
}

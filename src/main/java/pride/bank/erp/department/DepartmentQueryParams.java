package pride.bank.erp.department;

import lombok.Getter;
import lombok.Setter;

import static pride.bank.erp.helper.Constants.ASCENDING_SORT_DIRECTION;

@Getter
@Setter
public class DepartmentQueryParams {
    private int pageSize = 10;
    private int pageNumber = 0;
    private String sortBy = "id";
    private String sortDirection = ASCENDING_SORT_DIRECTION;
    private String name;
    /** Optional — restrict the result set to a single branch. Required for the Head Office department picker. */
    private Long branchId;
}

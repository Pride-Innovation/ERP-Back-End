package pride.bank.erp.branch;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import static pride.bank.erp.helper.Constants.ASCENDING_SORT_DIRECTION;

@Getter
@Setter
@Schema(description = "Query parameters for filtering and paginating branches")
public class BranchQueryParams {

    @Schema(description = "Number of records per page", example = "10", defaultValue = "10")
    private int pageSize = 10;

    @Schema(description = "Zero-based page index", example = "0", defaultValue = "0")
    private int pageNumber = 0;

    @Schema(description = "Field name to sort by (e.g. id, name)", example = "id", defaultValue = "id")
    private String sortBy = "id";

    @Schema(description = "Sort direction: ASC or DESC", example = "ASC", allowableValues = {"ASC", "DESC"}, defaultValue = "ASC")
    private String sortDirection = ASCENDING_SORT_DIRECTION;

    @Schema(description = "Filter by branch name (case-insensitive partial match)", example = "Kampala")
    private String name;

    @Schema(description = "Filter by region ID", example = "1")
    private Long regionId;

    @Schema(description = "Filter by district ID", example = "3")
    private Long districtId;

    @Schema(description = "Filter by branch manager user ID", example = "5")
    private Long branchManagerId;

    @Schema(description = "Filter by branch operations manager user ID", example = "7")
    private Long branchOperationsManagerId;

    @Schema(description = "Filter by relationship manager user ID", example = "9")
    private Long relationshipManagerId;

    @Schema(description = "Filter by credit administrator user ID", example = "11")
    private Long creditAdministratorId;
}

package pride.bank.erp.title;

import lombok.Getter;
import lombok.Setter;

import static pride.bank.erp.helper.Constants.ASCENDING_SORT_DIRECTION;

@Getter
@Setter
public class TitleQueryParams {
    private int pageSize = 10;
    private int pageNumber = 0;
    private String sortBy = "id";
    private String sortDirection = ASCENDING_SORT_DIRECTION;
    private String name;
    private String shortCode;
    private Long reportsToId;
    private Long roleId;

    /**
     * {@code HEAD_OFFICE}, {@code BRANCH} or {@code NOT_SET}. Bound as text so {@code NOT_SET} - which
     * is a filter value, not a workplace - can be expressed; anything else is refused with a 400
     * rather than read as "no filter".
     */
    private String workplace;

    private Long departmentId;

    /** Titles with no department. Ignored when {@link #departmentId} is given. */
    private Boolean noDepartment;
}

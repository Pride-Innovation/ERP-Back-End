/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.title;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.department.Department;
import pride.bank.erp.department.DepartmentRepository;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.role.Role;
import pride.bank.erp.role.RoleRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A title says where it is used (Head Office / Branches) and, optionally, its department - for
 * filtering the Titles page in Settings, and nothing else.
 *
 * <p>Every title here is made inside the test's transaction and rolled back. Names carry a marker so
 * the filters can be checked against exactly the titles the test made, whatever else is in the table.
 */
@SpringBootTest
@Transactional
class TitleClassificationTest {

    private static final String MARK = "ZZ-CLASSIFY-";

    @Autowired private TitleService titleService;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private ObjectMapper objectMapper;

    private Long roleId;
    private Department department;

    @BeforeEach
    void setUp() {
        Role role = roleRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No role seeded"));
        roleId = role.getId();
        department = departmentRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No department seeded"));
    }

    private Title create(String suffix, TitleWorkplace workplace, Long departmentId) throws NotFoundException {
        TitleRequestDTO request = new TitleRequestDTO();
        request.setName(MARK + suffix);
        request.setShortCode("ZZ" + suffix);
        request.setRole(roleId);
        request.setWorkplace(workplace);
        request.setDepartmentId(departmentId);
        return titleService.createTitle(request);
    }

    private Set<Long> idsMatching(Consumer<TitleQueryParams> filter) {
        TitleQueryParams params = new TitleQueryParams();
        params.setName(MARK);
        params.setPageSize(500);
        filter.accept(params);
        Set<Long> ids = new HashSet<>();
        titleService.fetchAllTitles(params).forEach(t -> ids.add(t.getId()));
        return ids;
    }

    @Test
    void createAndUpdateStoreBothFieldsAndAnUpdateWithoutThemClearsThem() throws Exception {
        Title title = create("A", TitleWorkplace.HEAD_OFFICE, department.getId());
        assertThat(title.getWorkplace()).isEqualTo(TitleWorkplace.HEAD_OFFICE);
        assertThat(title.getDepartment().getId()).isEqualTo(department.getId());

        TitleRequestDTO update = new TitleRequestDTO();
        update.setName(title.getName());
        update.setShortCode(title.getShortCode());
        update.setRole(roleId);
        update.setWorkplace(TitleWorkplace.BRANCH);
        assertThat(titleService.updateTitle(update, title.getId()))
                .satisfies(t -> assertThat(t.getWorkplace()).isEqualTo(TitleWorkplace.BRANCH))
                .satisfies(t -> assertThat(t.getDepartment()).isNull());

        update.setWorkplace(null);
        assertThat(titleService.updateTitle(update, title.getId()).getWorkplace()).isNull();
    }

    @Test
    void anUnknownDepartmentIsRefused() {
        assertThatThrownBy(() -> create("X", null, Long.MAX_VALUE)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void theWorkplaceFilterNarrowsAndItsThreeValuesPartitionTheTitles() throws Exception {
        Long ho = create("HO", TitleWorkplace.HEAD_OFFICE, null).getId();
        Long br = create("BR", TitleWorkplace.BRANCH, null).getId();
        Long unset = create("UN", null, null).getId();

        assertThat(idsMatching(p -> p.setWorkplace("HEAD_OFFICE"))).containsExactly(ho);
        assertThat(idsMatching(p -> p.setWorkplace("branch"))).containsExactly(br);
        assertThat(idsMatching(p -> p.setWorkplace("NOT_SET"))).containsExactly(unset);
        assertThat(idsMatching(p -> { })).containsExactlyInAnyOrder(ho, br, unset);
    }

    @Test
    void theWorkplaceValuesPartitionTheWholeTable() {
        TitleQueryParams all = new TitleQueryParams();
        long total = titleService.fetchAllTitles(all).getTotalElements();
        long sum = 0;
        for (String value : List.of("HEAD_OFFICE", "BRANCH", "NOT_SET")) {
            TitleQueryParams p = new TitleQueryParams();
            p.setWorkplace(value);
            sum += titleService.fetchAllTitles(p).getTotalElements();
        }
        assertThat(sum).as("Head Office + Branches + Not set must be every title").isEqualTo(total);
    }

    @Test
    void anUnknownWorkplaceIsA400NotAnUnfilteredList() {
        TitleQueryParams params = new TitleQueryParams();
        params.setWorkplace("HEADOFFICE");
        assertThatThrownBy(() -> titleService.fetchAllTitles(params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HEADOFFICE");
    }

    /**
     * The department is optional, so the filter joins it LEFT (trap #1). An inner join would show
     * here: titles with no department dropping out of the unfiltered and "no department" lists.
     */
    @Test
    void theDepartmentFilterAndNoDepartmentPartitionTheTitles() throws Exception {
        Long withDept = create("D1", TitleWorkplace.HEAD_OFFICE, department.getId()).getId();
        Long without = create("D0", TitleWorkplace.BRANCH, null).getId();

        assertThat(idsMatching(p -> p.setDepartmentId(department.getId()))).containsExactly(withDept);
        assertThat(idsMatching(p -> p.setNoDepartment(true))).containsExactly(without);
        assertThat(idsMatching(p -> { })).containsExactlyInAnyOrder(withDept, without);
        assertThat(idsMatching(p -> {
            p.setDepartmentId(department.getId());
            p.setWorkplace("BRANCH");
        })).as("filters combine").isEmpty();
    }

    /**
     * Trap #3: sorted on a repeated name, OFFSET paging must neither repeat nor drop a title.
     *
     * <p>Not proof on its own: with the {@code id} tie-break removed this still passed, because on a
     * result this small MySQL orders the ties the same way every time. It pins the behaviour; the
     * tie-break is there because the ordering is not guaranteed, not because this test saw it fail.
     */
    @Test
    void pagingOverARepeatedNameNeitherRepeatsNorDropsTitles() throws Exception {
        Set<Long> made = new HashSet<>();
        for (int i = 0; i < 7; i++) {
            TitleRequestDTO request = new TitleRequestDTO();
            request.setName(MARK + "SAME");
            request.setShortCode("ZZS" + i);
            request.setRole(roleId);
            made.add(titleService.createTitle(request).getId());
        }

        List<Long> seen = new ArrayList<>();
        for (int page = 0; page < 4; page++) {
            TitleQueryParams params = new TitleQueryParams();
            params.setName(MARK + "SAME");
            params.setSortBy("name");
            params.setPageSize(2);
            params.setPageNumber(page);
            titleService.fetchAllTitles(params).forEach(t -> seen.add(t.getId()));
        }
        assertThat(seen).doesNotHaveDuplicates();
        assertThat(new HashSet<>(seen)).isEqualTo(made);
    }

    @Test
    void anUnknownSortColumnFallsBackRatherThanFailing() {
        TitleQueryParams params = new TitleQueryParams();
        params.setSortBy("noSuchColumn");
        assertThat(titleService.fetchAllTitles(params)).isNotNull();
    }

    /** A title travels inside every user response, so its department carries no branch with it. */
    @Test
    void theDepartmentIsSerialisedWithoutItsBranch() throws Exception {
        Title title = create("J", TitleWorkplace.HEAD_OFFICE, department.getId());
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(title));

        assertThat(json.get("workplace").asText()).isEqualTo("HEAD_OFFICE");
        assertThat(json.get("department").get("name").asText()).isEqualTo(department.getName());
        assertThat(json.get("department").has("branch")).isFalse();
    }
}

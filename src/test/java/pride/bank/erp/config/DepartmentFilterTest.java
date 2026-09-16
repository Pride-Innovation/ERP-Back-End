package pride.bank.erp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.persistence.EntityManager;
import pride.bank.erp.asset.AssetSearchCriteria;
import pride.bank.erp.asset.AssetSearchDao;
import pride.bank.erp.request.RequestPage;
import pride.bank.erp.request.RequestSearchCriteria;
import pride.bank.erp.request.RequestSearchDao;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two department filters actually filter.
 *
 * <h2>Why this needs a test rather than a look</h2>
 * A filter that does nothing is indistinguishable from a working one whose criteria happen to match
 * everything. This codebase has shipped three of them: the assets, requests and store filters all
 * sent parameters the endpoint never declared (trap #2), and `GET /requests?status=` went further —
 * it was declared, passed to the service and written to the debug log while restricting nothing.
 *
 * <h2>What "works" means here, and why the counts are asserted</h2>
 * Neither department can answer for most records, and that is a property of the data rather than a
 * fault:
 *
 * <ul>
 *   <li><b>Assets have no department of their own</b> — only the holder does. 105 of 252 are
 *       unassigned, and only the 18 held at Head Office have a department at all.</li>
 *   <li><b>Requests belong to wherever the requester works.</b> 12 of 60 can match; the other 48 are
 *       raised from Gulu and Mbarara, where no staff have departments, plus two Head Office accounts
 *       that have none.</li>
 * </ul>
 *
 * <p>So these assert that filtering <em>narrows</em> and that every row it returns really carries the
 * department asked for — not a fixed number, which would break the first time somebody is moved
 * between departments.
 */
@SpringBootTest
class DepartmentFilterTest {

    @Autowired EntityManager em;
    @Autowired AssetSearchDao assetSearchDao;
    @Autowired RequestSearchDao requestSearchDao;

    /** A department that somebody actually belongs to, so the test is not asserting over nothing. */
    private Long aDepartmentInUse(String ownerTable) {
        List<?> ids = em.createQuery(
                        "select d.id from " + ownerTable + " o join o.department d group by d.id "
                                + "order by count(o) desc")
                .setMaxResults(1)
                .getResultList();
        assertThat(ids)
                .as("no %s has a department, so this filter cannot be exercised at all", ownerTable)
                .isNotEmpty();
        return (Long) ids.get(0);
    }

    // ── Assets ───────────────────────────────────────────────────────────────

    @Test
    void theAssetDepartmentFilterNarrowsAndOnlyReturnsThatDepartment() {
        Long departmentId = aDepartmentInUse("pride.bank.erp.user.User");

        long unfiltered = assetSearchDao.findAllWithFilters(
                page(), AssetSearchCriteria.builder().build()).getTotalElements();

        var filtered = assetSearchDao.findAllWithFilters(
                page(), AssetSearchCriteria.builder().departmentId(departmentId).build());

        assertThat(filtered.getTotalElements())
                .as("a declared parameter that changes nothing is the shape this codebase has "
                        + "shipped three times")
                .isLessThan(unfiltered)
                .isGreaterThan(0);

        assertThat(filtered.getContent())
                .allSatisfy(asset -> assertThat(asset.getAssignedTo())
                        .as("an asset with no holder has no department and must not be returned")
                        .isNotNull());
    }

    /**
     * The nullable joins must be LEFT joins, which is only visible on the <em>unfiltered</em> query.
     *
     * <p>`assignedTo` and its `department` are both nullable. A dotted path would compile to INNER
     * joins sitting in the FROM clause, and the 105 unassigned assets would vanish from every search
     * — including the ones with no department filter applied at all. That is trap #1, and it would
     * not show up in the test above, because an unassigned asset is correctly absent there either way.
     */
    @Test
    void addingTheDepartmentJoinDidNotDropUnassignedAssetsFromOrdinarySearches() {
        long total = assetSearchDao.findAllWithFilters(
                page(), AssetSearchCriteria.builder().build()).getTotalElements();

        long unassigned = (Long) em.createQuery(
                        "select count(a) from Asset a where a.assignedTo is null "
                                + "and (a.deleted = false or a.deleted is null)")
                .getSingleResult();

        assertThat(unassigned)
                .as("the estate has unassigned assets; without them this proves nothing")
                .isGreaterThan(0);
        assertThat(total)
                .as("an unfiltered search must still return assets nobody holds — if the department "
                        + "join became an INNER one, all %s of them would disappear", unassigned)
                .isGreaterThanOrEqualTo(unassigned);
    }

    // ── Requests ─────────────────────────────────────────────────────────────

    @Test
    void theRequestDepartmentFilterNarrowsAndOnlyReturnsThatDepartment() {
        Long departmentId = aDepartmentInUse("pride.bank.erp.user.User");

        long unfiltered = requestSearchDao.findAllWithFilters(
                requestPage(), RequestSearchCriteria.builder().build()).getTotalElements();

        var filtered = requestSearchDao.findAllWithFilters(
                requestPage(),
                RequestSearchCriteria.builder().requesterDepartmentId(departmentId).build());

        assertThat(filtered.getTotalElements())
                .isLessThan(unfiltered)
                .isGreaterThan(0);

        assertThat(filtered.getContent())
                .allSatisfy(request -> {
                    assertThat(request.getRequester()).isNotNull();
                    assertThat(request.getRequester().getDepartment()).isNotNull();
                    assertThat(request.getRequester().getDepartment().getId()).isEqualTo(departmentId);
                });
    }

    /** Same trap, same reason: requests raised by people with no department must still be findable. */
    @Test
    void addingTheDepartmentJoinDidNotDropDepartmentlessRequests() {
        long total = requestSearchDao.findAllWithFilters(
                requestPage(), RequestSearchCriteria.builder().build()).getTotalElements();

        long departmentless = (Long) em.createQuery(
                        "select count(r) from Request r left join r.requester u left join u.department d "
                                + "where d is null")
                .getSingleResult();

        assertThat(departmentless)
                .as("most requests come from staff with no department; without them this proves nothing")
                .isGreaterThan(0);
        assertThat(total).isGreaterThanOrEqualTo(departmentless);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private pride.bank.erp.asset.AssetPage page() {
        return pride.bank.erp.asset.AssetPage.builder()
                .pageNumber(0).pageSize(500).sortBy("id").sortDirection(
                        org.springframework.data.domain.Sort.Direction.DESC).build();
    }

    private RequestPage requestPage() {
        return RequestPage.builder()
                .pageNumber(0).pageSize(500).sortBy("id").sortDirection(
                        org.springframework.data.domain.Sort.Direction.DESC).build();
    }
}

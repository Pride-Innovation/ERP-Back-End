package pride.bank.erp.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.persistence.EntityManager;
import pride.bank.erp.asset.AssetPage;
import pride.bank.erp.asset.AssetSearchCriteria;
import pride.bank.erp.asset.AssetSearchDao;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reports page's branch and disposal-date filters on {@code GET /assets}.
 *
 * <ul>
 *   <li>{@code branchId} replaced sending the picked branch's <em>name</em> as {@code location},
 *       which is a partial LIKE — so a branch matched every branch whose name contained its own.</li>
 *   <li>{@code disposedFrom}/{@code disposedTo} exist because the report's period went to
 *       {@code startDate}/{@code endDate}, which match {@code createDate}: "disposed this month"
 *       meant "disposed, and created this month".</li>
 * </ul>
 */
@SpringBootTest
class ReportAssetFilterTest {

    @Autowired EntityManager em;
    @Autowired AssetSearchDao assetSearchDao;

    @Test
    void theBranchFilterReturnsOnlyAssetsAtOrHeldAtThatBranch() {
        List<?> ids = em.createQuery(
                        "select b.id from Asset a join a.branch b group by b.id order by count(a) desc")
                .setMaxResults(1).getResultList();
        assertThat(ids).as("no asset has a branch, so the filter cannot be exercised").isNotEmpty();
        Long branchId = (Long) ids.get(0);

        long unfiltered = assetSearchDao.findAllWithFilters(page(), AssetSearchCriteria.builder().build())
                .getTotalElements();
        var filtered = assetSearchDao.findAllWithFilters(
                page(), AssetSearchCriteria.builder().branchId(branchId).build());

        assertThat(filtered.getTotalElements()).isGreaterThan(0).isLessThanOrEqualTo(unfiltered);
        assertThat(filtered.getContent()).allSatisfy(asset -> {
            boolean atBranch = asset.getBranch() != null && branchId.equals(asset.getBranch().getId());
            boolean heldThere = asset.getAssignedTo() != null && asset.getAssignedTo().getBranch() != null
                    && branchId.equals(asset.getAssignedTo().getBranch().getId());
            assertThat(atBranch || heldThere)
                    .as("asset %s is neither at nor held at branch %s", asset.getId(), branchId)
                    .isTrue();
        });
    }

    /** Joins are cached, so the chosen branch and the scope must not duplicate them and inflate counts. */
    @Test
    void theBranchFilterAndTheScopeTogetherCountEachAssetOnce() {
        List<?> ids = em.createQuery("select b.id from Asset a join a.branch b group by b.id order by count(a) desc")
                .setMaxResults(1).getResultList();
        Long branchId = (Long) ids.get(0);

        var chosenOnly = assetSearchDao.findAllWithFilters(
                page(), AssetSearchCriteria.builder().branchId(branchId).build());
        var chosenAndScoped = assetSearchDao.findAllWithFilters(
                page(), AssetSearchCriteria.builder().branchId(branchId).scopedBranchId(branchId).build());

        assertThat(chosenAndScoped.getTotalElements()).isEqualTo(chosenOnly.getTotalElements());
        assertThat(chosenAndScoped.getContent()).doesNotHaveDuplicates();
    }

    @Test
    void theDisposalWindowMatchesTheDisposalDateNotTheCreationDate() {
        LocalDateTime from = LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime to = LocalDateTime.now().plusDays(1);

        var filtered = assetSearchDao.findAllWithFilters(page(), AssetSearchCriteria.builder()
                .disposed(true).disposedFrom(from).disposedTo(to).build());

        assertThat(filtered.getContent()).allSatisfy(asset -> {
            assertThat(asset.getDisposalDate()).as("asset %s has no disposal date", asset.getId()).isNotNull();
            assertThat(asset.getDisposalDate()).isBetween(from, to);
        });

        // A window in the far past matches nothing, whatever the assets' creation dates.
        var ancient = assetSearchDao.findAllWithFilters(page(), AssetSearchCriteria.builder()
                .disposed(true)
                .disposedFrom(LocalDateTime.of(1990, 1, 1, 0, 0))
                .disposedTo(LocalDateTime.of(1990, 12, 31, 23, 59)).build());
        assertThat(ancient.getTotalElements()).isZero();
    }

    private AssetPage page() {
        return AssetPage.builder().pageNumber(0).pageSize(500).sortBy("id")
                .sortDirection(org.springframework.data.domain.Sort.Direction.DESC).build();
    }
}

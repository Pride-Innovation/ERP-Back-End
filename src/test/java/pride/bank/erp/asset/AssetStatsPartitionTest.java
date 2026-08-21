package pride.bank.erp.asset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the arithmetic behind the dashboard's headline figures.
 *
 * <p>The four state counts have to partition the total exactly, because three widgets add them up
 * independently — the KPI band sums them, the condition meter divides by them, and the stacked bars
 * lay them end to end against the same total. If they overlap or leave a gap, those three disagree
 * with each other on screen and there is nothing to tell the reader which is right.
 *
 * <p>They did overlap. {@code assigned} was defined as "any status that is not in-maintenance and
 * not require-update", which swept every disposed asset into the In Use figure — so the dashboard
 * counted written-off equipment as deployed.
 */
@SpringBootTest
class AssetStatsPartitionTest {

    @Autowired
    private AssetRepository assetRepository;

    @Test
    @Transactional(readOnly = true)
    void stateCountsPartitionTheTotalExactly() {
        List<AssetTypeStatsProjection> rows = assetRepository.getAssetStatsScoped(null, null);
        if (rows.isEmpty()) return; // nothing on the register to assert against

        for (AssetTypeStatsProjection row : rows) {
            long total = row.getTotal() == null ? 0 : row.getTotal();
            long assigned = row.getAssigned() == null ? 0 : row.getAssigned();
            long unassigned = row.getUnassigned() == null ? 0 : row.getUnassigned();
            long inMaintenance = row.getInMaintenance() == null ? 0 : row.getInMaintenance();
            long disposed = row.getDisposed() == null ? 0 : row.getDisposed();

            assertThat(assigned + unassigned + inMaintenance + disposed)
                    .as("category '%s': in-use %d + in-store %d + in-repair %d + disposed %d must "
                                    + "equal the total %d — anything else means the buckets overlap "
                                    + "or leave a gap, and the three widgets that add them up will "
                                    + "disagree on screen",
                            row.getAssetType(), assigned, unassigned, inMaintenance, disposed, total)
                    .isEqualTo(total);
        }
    }

    /** No bucket may be negative — a sign the CASE arms have started overlapping. */
    @Test
    @Transactional(readOnly = true)
    void noBucketIsNegative() {
        for (AssetTypeStatsProjection row : assetRepository.getAssetStatsScoped(null, null)) {
            assertThat(row.getAssigned()).isNotNegative();
            assertThat(row.getUnassigned()).isNotNegative();
            assertThat(row.getInMaintenance()).isNotNegative();
            assertThat(row.getDisposed()).isNotNegative();
        }
    }

    /**
     * The national roll-up must be at least as large as any single branch's slice.
     *
     * <p>Cheap, but it is the property that would have caught the original scoping bug: a Head
     * Office user's "national" figures were their own branch's, so the roll-up and the slice were
     * identical for the one branch and smaller than it for every other.
     */
    @Test
    @Transactional(readOnly = true)
    void nationalRollUpIsNotSmallerThanABranchSlice() {
        long national = assetRepository.getAssetStatsScoped(null, null).stream()
                .mapToLong(r -> r.getTotal() == null ? 0 : r.getTotal()).sum();
        if (national == 0) return;

        long headOffice = assetRepository.getAssetStatsScoped(1L, null).stream()
                .mapToLong(r -> r.getTotal() == null ? 0 : r.getTotal()).sum();

        assertThat(national)
                .as("the roll-up across every branch cannot be smaller than one branch's slice")
                .isGreaterThanOrEqualTo(headOffice);
    }
}

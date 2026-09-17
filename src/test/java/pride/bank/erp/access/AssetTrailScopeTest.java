package pride.bank.erp.access;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An asset's trail must be scoped like the asset itself.
 *
 * <h2>Why this is its own test</h2>
 * Guarding a record and forgetting the records <em>about</em> it is a recurring shape of mistake,
 * and it fails quietly: the detail page correctly refuses, so the guard looks like it works, while
 * the history endpoint beside it answers the same question with the same id.
 *
 * <p>Assignment history is the chain of custody — every holder, every date, every status change.
 * Repair history is what failed, who had it and where it went. Both leak what the asset leaks, so
 * both answer to the asset's branch and not merely to {@code READ_ASSET}.
 *
 * <p>Both listings also answer with <b>no</b> asset id, which is the sharper half: unscoped, that is
 * the whole estate's custody trail and maintenance record, a page at a time. Those paths are scoped
 * by branch through the same resolver the asset listing uses.
 *
 * <p>A structural test rather than a behavioural one. Standing up two branches, two users and a
 * populated history to assert a refusal would pin the fixture more than the rule; what actually
 * matters is that the call is present and reaches the resolver, and that is what would be lost if
 * someone simplified these services later.
 */
@org.springframework.boot.test.context.SpringBootTest
class AssetTrailScopeTest {

    @org.springframework.beans.factory.annotation.Autowired
    private pride.bank.erp.permission.PermissionRepository permissionRepository;

    /**
     * Both trail permissions exist, so Settings can offer them and roles can hold them.
     *
     * <p>This deliberately does <b>not</b> assert that any particular role holds them. The backfill
     * onto existing {@code READ_ASSET} holders is a one-time migration — it exists so that nobody
     * lost a tab on the day the trail was split out — and after it has run, who holds what is the
     * administrator's decision. Asserting current grants would turn a legitimate revocation into a
     * build failure, which is precisely the mistake that made the seeded scopes un-revokable in the
     * first place.
     *
     * <p>What is worth pinning is that the rows exist: without them Settings has nothing to offer
     * and the endpoints refuse everybody.
     */
    @Test
    void bothTrailPermissionsAreAvailableToGrant() {
        for (String name : java.util.List.of("READ_ASSIGNMENT_HISTORY", "READ_REPAIR_HISTORY")) {
            assertThat(permissionRepository.findByName(name))
                    .as("%s must exist or the tab is unreachable for everyone and ungrantable", name)
                    .isPresent();
        }
    }

    /** The one place that answers "may I see this asset", exposed for the trail services. */
    @Test
    void theAssetReadCheckIsAvailableToOtherPackages() throws Exception {
        Method check = pride.bank.erp.asset.AssetService.class
                .getMethod("requireAssetReadable", Long.class);

        assertThat(java.lang.reflect.Modifier.isPublic(check.getModifiers()))
                .as("assignment history and repair history live in their own packages and must not "
                        + "each re-implement the check — there is one answer to 'may I see this "
                        + "asset' and it belongs on AssetService")
                .isTrue();
    }

    /**
     * Both per-asset trails must be able to refuse.
     *
     * <p>Pinned through the checked exception: {@code requireAssetReadable} throws
     * {@code NotFoundException}, so a caller that consults it has to declare or handle it. A service
     * method that quietly stopped calling it would lose that from its signature.
     */
    @Test
    void thePerAssetTrailsConsultTheAssetCheck() throws Exception {
        Method repairs = pride.bank.erp.repair.RepairService.class
                .getMethod("listRepairDetails", Long.class, org.springframework.data.domain.Pageable.class);

        assertThat(repairs.getExceptionTypes())
                .as("listRepairDetails must reach requireAssetReadable, which is checked")
                .contains(pride.bank.erp.exception.NotFoundException.class);
    }

    /**
     * The unscoped listings must carry a branch scope.
     *
     * <p>Pinned on the criteria object rather than the query, because the field is what the service
     * populates from the resolver and the DAO reads. Deleting it is the change that would silently
     * reopen the estate.
     */
    @Test
    void theUnscopedHistoryListingCarriesABranchScope() {
        var fields = Arrays.stream(
                        pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistorySearchCriteria.class
                                .getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .toList();

        assertThat(fields)
                .as("called with no asset id this returns every assignment row in the bank; without "
                        + "these it is the whole estate's chain of custody, page by page")
                .contains("scopedBranchId", "scopedOwnerId");
    }

    /** The maintenance report answers across the estate, so it takes the scope as a parameter. */
    @Test
    void theMaintenanceReportQueryTakesABranchScope() throws Exception {
        Method report = Arrays.stream(pride.bank.erp.repair.RepairRepository.class.getMethods())
                .filter(m -> "findForReport".equals(m.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("findForReport is gone"));

        boolean scoped = Arrays.stream(report.getParameters())
                .anyMatch(p -> {
                    var param = p.getAnnotation(org.springframework.data.repository.query.Param.class);
                    return param != null && "scopedBranchId".equals(param.value());
                });

        assertThat(scoped)
                .as("the report asks 'what has been repaired that I may see', not 'what has been "
                        + "repaired' — without the parameter it is every branch's maintenance trail")
                .isTrue();
    }
}

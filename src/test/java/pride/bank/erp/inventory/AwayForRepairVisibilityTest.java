package pride.bank.erp.inventory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.asset.AssetService;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.config.BranchScopeService;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.movement.MovementService;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreRepository;
import pride.bank.erp.store.StoreService;
import pride.bank.erp.store.StoreType;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Sent From", and the reach it gives a branch, last only while an asset is away.
 *
 * <h2>The drift this pins shut</h2>
 * The store listings and the asset detail page each had a copy of "which branch sent this here". The
 * detail page's copy asked only while the asset was in maintenance or pending disposal, and read the
 * newest repair or disposal transfer. The listing's copy took the newest movement of any kind that
 * carried an origin - and only a repair transfer carries one - so an asset kept the branch of its last
 * repair for good. Once it came home and moved on, that branch could still list it wherever it went
 * and was then refused the record. Measured when fixed: no live asset was in that state yet, so this
 * was latent, and these tests are what keep it so.
 *
 * <h2>And the store it opens up</h2>
 * ADMIN-routed and external repairs are held in the Head Office Admin store. The IT store already
 * showed a branch its own assets on the bench; this one did not, on any page. Measured: six Gulu
 * assets were there, unseen by Gulu.
 */
@SpringBootTest
@Transactional
class AwayForRepairVisibilityTest {

    @Autowired InventoryReportController controller;
    @Autowired InventoryReportService reportService;
    @Autowired BranchScopeService branchScope;
    @Autowired StoreRepository storeRepository;
    @Autowired StoreService storeService;
    @Autowired AssetRepository assetRepository;
    @Autowired BranchRepository branchRepository;
    @Autowired UserRepository userRepository;
    @Autowired MovementService movementService;
    @Autowired StatusService statusService;
    @Autowired JwtService jwtService;

    private static final Set<String> READERS = Set.of(
            "READ_STORE", "READ_INVENTORY", "RECEIVE_ASSET_IN_STORE", "CREATE_MOVEMENT", "PERFORM_STOCK_TAKE");

    @AfterEach
    void signOut() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    // ── The store ────────────────────────────────────────────────────────────

    /**
     * The companion store is the very store repairs are sent to, resolved the way the repair flow
     * resolves it - and only a branch's own Admin store has one. A look-alike (a department's store at
     * Head Office) would list a branch nothing while its chairs sat somewhere else.
     */
    @Test
    void aBranchAdminStoresCompanionIsTheStoreRepairsGoTo() {
        Optional<Branch> headOffice = branchRepository.findFirstByIsHeadOfficeTrue();
        if (headOffice.isEmpty()) return;
        Store custody = storeService.resolveAdminStore(headOffice.get());

        StoreView answered = reportService.headOfficeAdminStore().orElseThrow();
        assertThat(answered.getId()).isEqualTo(custody.getId());
        assertThat(answered.getStoreType()).isEqualTo(StoreType.ADMIN);
        assertThat(answered.getDepartmentId()).isNull();
        assertThat(answered.getLocationId()).isEqualTo(headOffice.get().getId());

        for (Store store : storeRepository.findAll()) {
            boolean branchAdmin = store.getStoreType() == StoreType.ADMIN && store.getDepartment() == null
                    && store.getLocation() != null && !store.getLocation().isHeadOffice();
            Optional<InventoryReportService.AwayCompanion> companion = reportService.awayCompanionFor(store.getId());
            assertThat(companion.isPresent()).as("store %d %s", store.getId(), store.getName()).isEqualTo(branchAdmin);
            companion.ifPresent(c -> {
                assertThat(c.headOfficeAdminStoreId()).isEqualTo(custody.getId());
                assertThat(c.branchId()).isEqualTo(store.getLocation().getId());
            });
        }
    }

    /** The column belongs to the stores other branches send assets into, and to no branch's own store. */
    @Test
    void sentFromAppliesExactlyWhereOtherBranchesSendAssets() {
        Long hoAdmin = reportService.headOfficeAdminStore().map(StoreView::getId).orElse(null);

        for (Store store : storeRepository.findAll()) {
            boolean expected = store.getStoreType() == StoreType.IT
                    || store.getStoreType() == StoreType.DISPOSAL
                    || store.getId().equals(hoAdmin);
            if (store.getStoreType() == StoreType.COURIER) continue;   // no page shows a courier's table
            assertThat(reportService.receivesAssetsFromOtherBranches(store.getId()))
                    .as("store %d %s (%s)", store.getId(), store.getName(), store.getStoreType())
                    .isEqualTo(expected);
        }
    }

    // ── The rule ─────────────────────────────────────────────────────────────

    /**
     * Every "Sent From" shown is on an asset that is away now, and is exactly the branch the detail
     * page would name - the batched listing and the single-asset rule cannot disagree.
     */
    @Test
    void anOriginIsShownOnlyWhileAwayAndMatchesTheDetailPagesRule() {
        Optional<User> viewer = findViewer(true, null);
        if (viewer.isEmpty()) return;
        signInAs(viewer.get());

        int checked = 0;
        for (Store store : storesWithAssets()) {
            for (StoreAssetRow row : allRows(store.getId(), null)) {
                Asset asset = assetRepository.findById(row.id()).orElseThrow();
                boolean away = AssetService.isAwayFromItsBranch(asset);
                if (row.originBranchId() != null) {
                    assertThat(away).as("asset %d shows a sending branch while not away", row.id()).isTrue();
                }
                if (away) {
                    assertThat(row.originBranchId())
                            .as("asset %d: listing and detail page must name the same branch", row.id())
                            .isEqualTo(movementService.branchLastSentAwayFrom(row.id()));
                    checked++;
                }
            }
        }
        System.out.printf("away-for-repair test: %d away asset(s) compared against the detail rule%n", checked);
    }

    /**
     * The case the gate exists for: an asset that has come home stops reading as sent, and its old
     * branch stops reaching it. Made here, inside the test's transaction, by moving one away asset
     * out of maintenance - so it runs whatever the live data holds today, and is rolled back.
     */
    @Test
    void anAssetNoLongerAwayStopsCountingForTheBranchThatSentIt() throws Exception {
        Optional<Branch> headOffice = branchRepository.findFirstByIsHeadOfficeTrue();
        if (headOffice.isEmpty()) return;

        // An away asset whose sender is not where it now sits - the only kind the gate changes.
        Asset away = storesWithAssets().stream()
                .flatMap(s -> reportService.assetsInStore(s.getId()).stream())
                .filter(AssetService::isAwayFromItsBranch)
                .filter(a -> {
                    Long origin = movementService.branchLastSentAwayFrom(a.getId());
                    return origin != null && (a.getBranch() == null || !origin.equals(a.getBranch().getId()));
                })
                .findFirst().orElse(null);
        if (away == null) {
            System.out.println("away-for-repair test: NO asset away from its branch - gate case unexercised");
            return;
        }
        Long sender = movementService.branchLastSentAwayFrom(away.getId());
        Long storeId = away.getCurrentStore().getId();

        Optional<User> senderViewer = findViewer(false, sender);
        if (senderViewer.isPresent()) {
            signInAs(senderViewer.get());
            assertThat(ids(storeId)).as("while away, the sending branch sees it").contains(away.getId());
            signOut();
        }

        away.setAssetStatus(statusService.findByStatusCode("sentToStore"));
        assetRepository.saveAndFlush(away);

        if (senderViewer.isPresent()) {
            signInAs(senderViewer.get());
            assertThat(ids(storeId)).as("home again, it is no longer the sender's").doesNotContain(away.getId());
            signOut();
        }
        Optional<User> all = findViewer(true, null);
        if (all.isPresent()) {
            signInAs(all.get());
            StoreAssetRow row = allRows(storeId, null).stream()
                    .filter(r -> r.id().equals(away.getId())).findFirst().orElseThrow();
            assertThat(row.originBranchId()).as("and it no longer reads as sent from anywhere").isNull();
        }
    }

    // ── The branch's view of the Head Office Admin store ─────────────────────

    /**
     * A branch sees, in the Head Office Admin store, exactly its own assets that are away - nothing of
     * another branch's, and nothing that sits there as Head Office's own (the loaner pool).
     */
    @Test
    void aBranchSeesExactlyItsOwnAwayAssetsInTheHeadOfficeAdminStore() {
        Optional<StoreView> store = reportService.headOfficeAdminStore();
        if (store.isEmpty()) return;
        Long storeId = store.get().getId();

        // A branch that has something there, so the case is exercised rather than passed over empty.
        Long branch = reportService.assetsInStore(storeId).stream()
                .filter(AssetService::isAwayFromItsBranch)
                .map(a -> movementService.branchLastSentAwayFrom(a.getId()))
                .filter(Objects::nonNull)
                .filter(b -> branchRepository.findById(b).map(x -> !x.isHeadOffice()).orElse(false))
                .findFirst().orElse(null);
        if (branch == null) {
            System.out.println("away-for-repair test: NO branch asset in the Head Office Admin store - unexercised");
            return;
        }
        Optional<User> viewer = findViewer(false, branch);
        if (viewer.isEmpty()) return;

        Set<Long> expected = reportService.assetsInStore(storeId).stream()
                .filter(AssetService::isAwayFromItsBranch)
                .filter(a -> branch.equals(movementService.branchLastSentAwayFrom(a.getId())))
                .map(Asset::getId).collect(Collectors.toSet());

        signInAs(viewer.get());
        Set<Long> unfiltered = ids(storeId);
        assertThat(unfiltered).as("reading the Head Office Admin store itself, the scope admits exactly "
                + "what this branch sent").isEqualTo(expected);
        assertThat(controller.storeAssetFilterOptions(storeId, false).getBody().originColumn()).isTrue();

        // What the branch's own Admin page asks for: its store, plus what it has away at Head Office.
        Store own = storeService.resolveAdminStore(branchRepository.findById(branch).orElseThrow());
        Set<Long> ownStore = reportService.assetsInStore(own.getId()).stream()
                .map(Asset::getId).collect(Collectors.toSet());
        List<StoreAssetRow> merged = mergedRows(own.getId());
        Set<Long> shown = merged.stream().map(StoreAssetRow::id).collect(Collectors.toSet());

        System.out.printf("away-for-repair test: branch %d's Admin page lists %d of its own + %d away%n",
                branch, ownStore.size(), shown.size() - ownStore.size());
        Set<Long> want = new java.util.HashSet<>(ownStore);
        want.addAll(expected);
        assertThat(shown).isEqualTo(want);
        assertThat(merged).filteredOn(r -> expected.contains(r.id()))
                .as("away rows say where they are held").allSatisfy(r -> assertThat(r.storeId()).isEqualTo(storeId));
        assertThat(controller.storeAssetFilterOptions(own.getId(), true).getBody().awayIncluded()).isTrue();
        // Without the flag the store reads exactly as it did - nothing else that calls it changes.
        assertThat(ids(own.getId())).isEqualTo(ownStore);
    }

    /**
     * An all-branches viewer on a branch's Admin page sees that branch's repairs only - not every
     * branch's, which is what the Head Office Admin store would give them unfiltered.
     */
    @Test
    void anAllBranchesViewerOnABranchPageSeesOnlyThatBranchsAwayAssets() {
        Optional<StoreView> ho = reportService.headOfficeAdminStore();
        Optional<User> viewer = findViewer(true, null);
        if (ho.isEmpty() || viewer.isEmpty()) return;
        signInAs(viewer.get());

        int checked = 0;
        for (Store store : storeRepository.findAll()) {
            Optional<InventoryReportService.AwayCompanion> companion = reportService.awayCompanionFor(store.getId());
            if (companion.isEmpty()) continue;
            for (StoreAssetRow row : mergedRows(store.getId())) {
                if (row.storeId() == null || row.storeId().equals(store.getId())) continue;
                assertThat(row.originBranchId())
                        .as("row %d on %s's page was sent from elsewhere", row.id(), store.getName())
                        .isEqualTo(companion.get().branchId());
                checked++;
            }
        }
        System.out.printf("away-for-repair test: %d away row(s) checked across branch Admin pages%n", checked);
        assertThat(checked).as("the Head Office Admin store holds branch repairs today").isPositive();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private List<StoreAssetRow> allRows(Long storeId, Long originBranchId) {
        return controller.assetsInStorePage(storeId, null, null, null, null, originBranchId,
                0, StoreAssetListing.MAX_PAGE_SIZE, null, null, false).getBody().content();
    }

    private List<StoreAssetRow> mergedRows(Long storeId) {
        return controller.assetsInStorePage(storeId, null, null, null, null, null,
                0, StoreAssetListing.MAX_PAGE_SIZE, null, null, true).getBody().content();
    }

    private Set<Long> ids(Long storeId) {
        return allRows(storeId, null).stream().map(StoreAssetRow::id).collect(Collectors.toSet());
    }

    private List<Store> storesWithAssets() {
        return storeRepository.findAll().stream()
                .filter(s -> s.getStoreType() != StoreType.COURIER)
                .filter(s -> !reportService.assetsInStore(s.getId()).isEmpty())
                .toList();
    }

    /** A store reader who does (or does not) see every branch - at {@code branchId} when one is named. */
    private Optional<User> findViewer(boolean allBranches, Long branchId) {
        for (User u : userRepository.findAll()) {
            if (u.getBranch() == null) continue;
            if (branchId != null && !branchId.equals(u.getBranch().getId())) continue;
            if (u.getAuthorities().stream().noneMatch(a -> READERS.contains(a.getAuthority()))) continue;
            signInAs(u);
            boolean all = branchScope.canViewAllBranches();
            signOut();
            if (all == allBranches) return Optional.of(u);
        }
        System.out.printf("away-for-repair test: NO %s viewer%s found%n",
                allBranches ? "all-branches" : "branch", branchId == null ? "" : " at branch " + branchId);
        return Optional.empty();
    }

    private void signInAs(User user) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(user));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}

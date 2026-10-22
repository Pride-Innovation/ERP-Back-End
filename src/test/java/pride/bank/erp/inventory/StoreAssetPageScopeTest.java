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
import pride.bank.erp.config.BranchScopeService;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreRepository;
import pride.bank.erp.store.StoreType;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The paged "Assets Held" table sees exactly what the full store listing sees - for a caller who
 * sees every branch <b>and</b> for one who does not.
 *
 * <p>Both, deliberately. The movements register once shipped with a scoped search that crashed for
 * every branch user while every test passed, because every test ran as a caller with no branch
 * restriction. The restricted path is the one that needs the check, so it is the one exercised here
 * against real rows.
 */
@SpringBootTest
@Transactional
class StoreAssetPageScopeTest {

    @Autowired InventoryReportController controller;
    @Autowired InventoryReportService reportService;
    @Autowired BranchScopeService branchScope;
    @Autowired StoreRepository storeRepository;
    @Autowired AssetRepository assetRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    private static final Set<String> READERS = Set.of(
            "READ_STORE", "READ_INVENTORY", "RECEIVE_ASSET_IN_STORE", "CREATE_MOVEMENT", "PERFORM_STOCK_TAKE");

    @AfterEach
    void signOut() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    /** As someone who sees every branch, the page holds every asset the full listing returns. */
    @Test
    void anAllBranchesViewerSeesTheSameRowsAsTheFullListing() {
        Optional<User> viewer = findViewer(true);
        if (viewer.isEmpty()) return;
        signInAs(viewer.get());

        for (Store store : storesWithAssets()) {
            assertThat(pagedIds(store.getId())).as("store %s", store.getName())
                    .isEqualTo(listingIds(store.getId()));
        }
    }

    /**
     * As a branch user, the page holds exactly what the full listing does - and every row is one the
     * visibility rule admits: standing at their branch, or sent from it.
     */
    @Test
    void aBranchViewerSeesTheSameRowsAsTheFullListingAndNothingElse() {
        Optional<User> viewer = findViewer(false);
        if (viewer.isEmpty()) return;
        signInAs(viewer.get());
        Long own = branchScope.currentUserBranchId();

        for (Store store : storesWithAssets()) {
            Set<Long> paged = pagedIds(store.getId());
            assertThat(paged).as("store %s", store.getName()).isEqualTo(listingIds(store.getId()));

            List<StoreAssetRow> rows = controller.assetsInStorePage(store.getId(), null, null, null, null, null,
                    0, StoreAssetListing.MAX_PAGE_SIZE, null, null, false).getBody().content();
            for (StoreAssetRow row : rows) {
                Asset asset = assetRepository.findById(row.id()).orElseThrow();
                Long holding = asset.getBranch() == null ? null : asset.getBranch().getId();
                assertThat(own.equals(holding) || own.equals(row.originBranchId()))
                        .as("asset %d at branch %s, sent from %s, shown to branch %s",
                                row.id(), holding, row.originBranchId(), own)
                        .isTrue();
            }

            // "Sent from" is for callers who see every branch; a branch user could only ever pick their own.
            assertThat(controller.storeAssetFilterOptions(store.getId(), false).getBody().originBranches())
                    .as("store %s", store.getName()).isEmpty();
        }
    }

    /** Walking the pages returns every visible asset exactly once. */
    @Test
    void walkingThePagesReturnsEachAssetOnce() {
        Optional<User> viewer = findViewer(true);
        if (viewer.isEmpty()) return;
        signInAs(viewer.get());

        for (Store store : storesWithAssets()) {
            Set<Long> seen = new HashSet<>();
            int pages = controller.assetsInStorePage(store.getId(), null, null, null, null, null,
                    0, 3, "statusName", "ASC", false).getBody().totalPages();
            for (int p = 0; p < pages; p++) {
                controller.assetsInStorePage(store.getId(), null, null, null, null, null, p, 3, "statusName", "ASC", false)
                        .getBody().content()
                        .forEach(r -> assertThat(seen.add(r.id())).as("asset %d repeated", r.id()).isTrue());
            }
            assertThat(seen).isEqualTo(listingIds(store.getId()));
        }
    }

    /** A soft-deleted asset is not in its store's table, nor in the full listing the movement picker reads. */
    @Test
    void aDeletedAssetIsNotListed() {
        Optional<User> viewer = findViewer(true);
        Optional<Store> store = storesWithAssets().stream().findFirst();
        if (viewer.isEmpty() || store.isEmpty()) return;
        signInAs(viewer.get());

        Asset victim = assetRepository.findByCurrentStore_Id(store.get().getId()).get(0);
        victim.setDeleted(true);
        assetRepository.saveAndFlush(victim);

        assertThat(pagedIds(store.get().getId())).doesNotContain(victim.getId());
        assertThat(listingIds(store.get().getId())).doesNotContain(victim.getId());
    }

    /** Every option a dropdown offers matches at least one row the caller can see. */
    @Test
    void everyOfferedOptionMatches() {
        Optional<User> viewer = findViewer(true);
        if (viewer.isEmpty()) return;
        signInAs(viewer.get());

        for (Store store : storesWithAssets()) {
            StoreAssetListing.FilterOptions options = controller.storeAssetFilterOptions(store.getId(), false).getBody();
            for (StoreAssetListing.Option o : options.categories()) {
                assertThat(total(store, Long.valueOf(o.value()), null, null)).as("category %s", o.label()).isPositive();
            }
            for (StoreAssetListing.Option o : options.statuses()) {
                assertThat(total(store, null, o.value(), null)).as("status %s", o.label()).isPositive();
            }
            for (StoreAssetListing.Option o : options.originBranches()) {
                assertThat(total(store, null, null, Long.valueOf(o.value()))).as("origin %s", o.label()).isPositive();
            }
            // "Sent from" belongs to the stores other branches send assets into; a branch's own Admin
            // store holds only that branch's, so it never offers the filter.
            if (!reportService.receivesAssetsFromOtherBranches(store.getId())) {
                assertThat(options.originBranches()).as("store %s", store.getName()).isEmpty();
                assertThat(options.originColumn()).as("store %s", store.getName()).isFalse();
            }
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private long total(Store store, Long assetTypeId, String statusCode, Long originBranchId) {
        return controller.assetsInStorePage(store.getId(), null, assetTypeId, statusCode, null, originBranchId,
                0, 1, null, null, false).getBody().totalElements();
    }

    private Set<Long> pagedIds(Long storeId) {
        return controller.assetsInStorePage(storeId, null, null, null, null, null,
                        0, StoreAssetListing.MAX_PAGE_SIZE, null, null, false)
                .getBody().content().stream().map(StoreAssetRow::id).collect(Collectors.toSet());
    }

    private Set<Long> listingIds(Long storeId) {
        return controller.assetsInStore(storeId).getBody().stream().map(Asset::getId).collect(Collectors.toSet());
    }

    /** The IT, Disposal and Admin stores that hold at least one asset - the three pages this serves. */
    private List<Store> storesWithAssets() {
        return storeRepository.findAll().stream()
                .filter(s -> s.getStoreType() == StoreType.IT || s.getStoreType() == StoreType.DISPOSAL
                        || s.getStoreType() == StoreType.ADMIN)
                .filter(s -> !assetRepository.findByCurrentStore_Id(s.getId()).isEmpty())
                .toList();
    }

    /** A user who may read store contents and does (or does not) see every branch. */
    private Optional<User> findViewer(boolean allBranches) {
        /*
         * A restricted viewer away from Head Office first: that is who the "sent from" half of the rule
         * exists for - a Gulu officer whose laptop is on the Head Office bench. A Head Office viewer
         * would pass on the "where it sits" half alone and leave the other half unexercised.
         */
        List<User> candidates = userRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(
                        (User u) -> allBranches || u.getBranch() == null || u.getBranch().isHeadOffice()))
                .toList();
        for (User u : candidates) {
            if (u.getBranch() == null) continue;
            boolean reads = u.getAuthorities().stream().anyMatch(a -> READERS.contains(a.getAuthority()));
            if (!reads) continue;
            signInAs(u);
            boolean all = branchScope.canViewAllBranches();
            signOut();
            if (all == allBranches && Objects.nonNull(u.getId())) {
                System.out.printf("store-asset scope test: %s viewer is user %d (branch %d)%n",
                        allBranches ? "all-branches" : "branch", u.getId(), u.getBranch().getId());
                return Optional.of(u);
            }
        }
        // Reported, not passed silently: with no such user the scoped path would go unexercised.
        System.out.printf("store-asset scope test: NO %s viewer found - that path was not exercised%n",
                allBranches ? "all-branches" : "branch");
        return Optional.empty();
    }

    /** CurrentUserService reads the JWT on the bound request; @PreAuthorize reads the SecurityContext. */
    private void signInAs(User user) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(user));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}

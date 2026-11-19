/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stocktake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.assetType.AssetTypeRepository;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.role.Role;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreBalance;
import pride.bank.erp.store.StoreBalanceRepository;
import pride.bank.erp.store.StoreRepository;
import pride.bank.erp.store.StoreType;
import pride.bank.erp.stocktake.StockTakeDTOs.AdjustmentQuery;
import pride.bank.erp.stocktake.StockTakeDTOs.AdjustmentView;
import pride.bank.erp.stocktake.StockTakeDTOs.CountView;
import pride.bank.erp.stocktake.StockTakeDTOs.OpenCountRequest;
import pride.bank.erp.stocktake.StockTakeDTOs.VarianceReport;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Two things about the stock take page.
 *
 * <p><b>Asset-tracked categories are not counted.</b> Their quantity lines do not follow the asset
 * records (a movement relocates the asset and leaves the line), so counting against them reports
 * ordinary transfers as losses. A count sheet holds consumable lines only.
 *
 * <p><b>The adjustment history is filtered and paged by the server</b>, with totals over everything
 * matching rather than over the page. Adjustments made here are inside the rolled-back transaction.
 */
@SpringBootTest
@Transactional
class StockTakeAssetsAndHistoryTest {

    @Autowired StockTakeService stockTakeService;
    @Autowired StockAdjustmentRepository adjustmentRepository;
    @Autowired StockCountRepository countRepository;
    @Autowired StockCountLineRepository lineRepository;
    @Autowired StoreRepository storeRepository;
    @Autowired StoreBalanceRepository storeBalanceRepository;
    @Autowired AssetTypeRepository assetTypeRepository;
    @Autowired pride.bank.erp.commodity.CommodityRepository commodityRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    @AfterEach
    void signOut() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    // ── Asset-tracked categories ────────────────────────────────────────────

    @Test
    void aLineIsCountableOnlyWhenItsCategoryDoesNotTrackAssets() {
        AssetType tracked = AssetType.builder().name("t").tracksAssets(true).build();
        AssetType consumable = AssetType.builder().name("c").tracksAssets(false).build();

        assertThat(StockTakeService.isCountableLine(line(tracked))).isFalse();
        assertThat(StockTakeService.isCountableLine(line(consumable))).isTrue();
        assertThat(StockTakeService.isCountableLine(line(null))).as("no category: counted").isTrue();
    }

    private static StoreBalance line(AssetType type) {
        return StoreBalance.builder().commodity(Commodity.builder().name("x").assetType(type).build()).build();
    }

    @Test
    void anOpenedSheetLeavesAssetTrackedLinesOff() throws Exception {
        User admin = superAdmin();
        // Any store with no unfinished count in the way; the lines are made here and rolled back,
        // because the shape that matters - both kinds of line in one store, which is what received
        // goods in a mixed order leave - need not exist in whatever data the suite runs against.
        Optional<Store> mixed = storeRepository.findAll().stream()
                .filter(s -> s.getStoreType() != StoreType.COURIER && s.getLocation() != null)
                .filter(s -> countRepository.findOpenForStore(s.getId()).isEmpty())
                .findFirst();
        Set<Long> held = mixed.map(store -> storeBalanceRepository.findByStore_Id(store.getId()).stream()
                .map(b -> b.getCommodity().getId()).collect(Collectors.toSet())).orElse(Set.of());
        Optional<Commodity> trackedCommodity = commodityRepository.findAll().stream()
                .filter(c -> c.getAssetType() != null && c.getAssetType().isTracksAssets())
                .filter(c -> !held.contains(c.getId())).findFirst();
        Optional<Commodity> consumable = commodityRepository.findAll().stream()
                .filter(c -> c.getAssetType() != null && !c.getAssetType().isTracksAssets())
                .filter(c -> !held.contains(c.getId())).findFirst();
        if (admin == null || mixed.isEmpty() || trackedCommodity.isEmpty() || consumable.isEmpty()) {
            System.out.println("asset exclusion test: NO free store or commodities of both kinds - unexercised");
            return;
        }
        for (Commodity c : List.of(trackedCommodity.get(), consumable.get())) {
            storeBalanceRepository.save(StoreBalance.builder().store(mixed.get()).branch(mixed.get().getLocation())
                    .commodity(c).quantity(5).build());
        }
        System.out.printf("asset exclusion test: store %d, tracked %d, consumable %d%n",
                mixed.get().getId(), trackedCommodity.get().getId(), consumable.get().getId());
        signInAs(admin);

        OpenCountRequest request = new OpenCountRequest();
        request.setStoreId(mixed.get().getId());
        request.setBlind(true);
        CountView opened = stockTakeService.openCount(request);

        Set<Long> onSheet = lineRepository.findByStockCount_IdOrderByCommodity_NameAsc(opened.getId()).stream()
                .map(l -> l.getCommodity().getId()).collect(Collectors.toSet());
        Set<Long> expected = storeBalanceRepository.findByStore_Id(mixed.get().getId()).stream()
                .filter(StockTakeService::isCountableLine)
                .map(b -> b.getCommodity().getId()).collect(Collectors.toSet());
        assertThat(onSheet).isEqualTo(expected)
                .contains(consumable.get().getId())
                .doesNotContain(trackedCommodity.get().getId());
    }

    @Test
    void anAssetTrackedCategoryCannotBeChosenForACount() {
        User admin = superAdmin();
        Optional<AssetType> tracked = assetTypeRepository.findAll().stream().filter(AssetType::isTracksAssets).findFirst();
        Optional<Store> store = storeRepository.findAll().stream()
                .filter(s -> s.getStoreType() != StoreType.COURIER)
                .filter(s -> countRepository.findOpenForStore(s.getId()).isEmpty())
                .findFirst();
        if (admin == null || tracked.isEmpty() || store.isEmpty()) return;
        signInAs(admin);

        OpenCountRequest request = new OpenCountRequest();
        request.setStoreId(store.get().getId());
        request.setAssetTypeId(tracked.get().getId());
        assertThatThrownBy(() -> stockTakeService.openCount(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tracks individual assets");
    }

    @Test
    void storesOfferedForCountingReportOnlyTheirCountableLines() {
        User admin = superAdmin();
        if (admin == null) return;
        signInAs(admin);
        assertThat(stockTakeService.countableStores()).allSatisfy(s -> {
            long countable = storeBalanceRepository.findByStore_Id(s.getId()).stream()
                    .filter(StockTakeService::isCountableLine).count();
            assertThat((long) s.getLineCount()).isEqualTo(countable).isPositive();
        });
    }

    // ── Adjustment history ──────────────────────────────────────────────────

    /** Five adjustments at one store, varied in reason, commodity and date. */
    private List<StockAdjustment> seed(Store store, List<Commodity> commodities) {
        LocalDateTime base = LocalDateTime.of(2030, 1, 10, 9, 0);
        AdjustmentReason[] reasons = {AdjustmentReason.MISCOUNT, AdjustmentReason.DAMAGED,
                AdjustmentReason.MISCOUNT, AdjustmentReason.FOUND, AdjustmentReason.LOST_OR_STOLEN};
        List<StockAdjustment> made = new ArrayList<>();
        for (int i = 0; i < reasons.length; i++) {
            int delta = reasons[i] == AdjustmentReason.FOUND ? 3 : -(i + 1);
            made.add(adjustmentRepository.save(StockAdjustment.builder()
                    .store(store).commodity(commodities.get(i % commodities.size()))
                    .quantityDelta(delta).quantityBefore(10).quantityAfter(10 + delta)
                    .reason(reasons[i]).note("zz-history-test").postedAt(base.plusDays(i)).build()));
        }
        return made;
    }

    private Optional<Store> storeWithCommodities() {
        return storeRepository.findAll().stream()
                .filter(s -> s.getLocation() != null && storeBalanceRepository.findByStore_Id(s.getId()).size() >= 2)
                .findFirst();
    }

    private VarianceReport report(AdjustmentQuery.AdjustmentQueryBuilder q) {
        return stockTakeService.varianceReport(q.pageSize(5000).build());
    }

    private static Set<Long> ids(VarianceReport r) {
        return r.getAdjustments().stream().map(AdjustmentView::getId).collect(Collectors.toSet());
    }

    @Test
    void filtersNarrowToExactlyTheMatchingAdjustmentsAndTotalsFollowThem() {
        User admin = superAdmin();
        Optional<Store> store = storeWithCommodities();
        if (admin == null || store.isEmpty()) return;
        signInAs(admin);
        List<Commodity> commodities = storeBalanceRepository.findByStore_Id(store.get().getId()).stream()
                .map(StoreBalance::getCommodity).toList();
        List<StockAdjustment> made = seed(store.get(), commodities);
        Set<Long> madeIds = made.stream().map(StockAdjustment::getId).collect(Collectors.toSet());
        LocalDateTime from = LocalDateTime.of(2030, 1, 10, 0, 0);
        LocalDateTime to = LocalDateTime.of(2030, 1, 20, 23, 59, 59);

        // The date window holds only the seeded rows (they are dated 2030).
        VarianceReport window = report(AdjustmentQuery.builder().from(from).to(to));
        assertThat(ids(window)).isEqualTo(madeIds);
        assertThat(window.getTotalLines()).isEqualTo(5);

        VarianceReport miscount = report(AdjustmentQuery.builder().from(from).to(to).reason(AdjustmentReason.MISCOUNT));
        assertThat(ids(miscount)).isEqualTo(made.stream().filter(a -> a.getReason() == AdjustmentReason.MISCOUNT)
                .map(StockAdjustment::getId).collect(Collectors.toSet()));
        assertThat(miscount.getByReason()).singleElement()
                .satisfies(s -> assertThat(s.getLines()).isEqualTo(2));
        assertThat(miscount.getUnitsWrittenDown()).isEqualTo(1 + 3);

        assertThat(ids(report(AdjustmentQuery.builder().from(from).to(to).storeId(store.get().getId()))))
                .isEqualTo(madeIds);
        assertThat(ids(report(AdjustmentQuery.builder().from(from).to(to).branchId(store.get().getLocation().getId()))))
                .isEqualTo(madeIds);

        String name = commodities.get(0).getName();
        assertThat(ids(report(AdjustmentQuery.builder().from(from).to(to).search(name.substring(0, Math.min(4, name.length())).toUpperCase()))))
                .as("search is partial and case-insensitive")
                .containsAll(made.stream().filter(a -> a.getCommodity().getId().equals(commodities.get(0).getId()))
                        .map(StockAdjustment::getId).toList());

        assertThat(ids(report(AdjustmentQuery.builder().from(from).to(from.plusDays(1).minusSeconds(1)))))
                .containsExactly(made.get(0).getId());

        assertThat(window.getStoreOptions()).extracting(StockTakeDTOs.AdjustmentStoreOption::getId)
                .contains(store.get().getId());
    }

    @Test
    void pagingNeitherRepeatsNorDropsAdjustmentsAndTotalsCoverEveryPage() {
        User admin = superAdmin();
        Optional<Store> store = storeWithCommodities();
        if (admin == null || store.isEmpty()) return;
        signInAs(admin);
        List<StockAdjustment> made = seed(store.get(), storeBalanceRepository.findByStore_Id(store.get().getId())
                .stream().map(StoreBalance::getCommodity).toList());
        LocalDateTime from = LocalDateTime.of(2030, 1, 10, 0, 0);

        List<Long> seen = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            VarianceReport r = stockTakeService.varianceReport(AdjustmentQuery.builder()
                    .from(from).pageNumber(page).pageSize(2).build());
            assertThat(r.getTotalLines()).as("the total is the whole set, on every page").isEqualTo(5);
            assertThat(r.getTotalPages()).isEqualTo(3);
            r.getAdjustments().forEach(a -> seen.add(a.getId()));
        }
        assertThat(seen).doesNotHaveDuplicates();
        assertThat(new HashSet<>(seen)).isEqualTo(made.stream().map(StockAdjustment::getId).collect(Collectors.toSet()));
    }

    @Test
    void aBranchViewerNamingAnotherBranchIsRefused() {
        Optional<User> officer = userRepository.findAll().stream()
                .filter(u -> u.getBranch() != null && !u.getBranch().isHeadOffice() && !isSuperAdmin(u))
                .filter(u -> !holds(u, "VIEW_ALL_BRANCH_INVENTORY"))
                .findFirst();
        Optional<Long> other = storeRepository.findAll().stream()
                .map(Store::getLocation).filter(l -> l != null)
                .filter(l -> officer.isPresent() && !l.getId().equals(officer.get().getBranch().getId()))
                .map(l -> l.getId()).findFirst();
        if (officer.isEmpty() || other.isEmpty()) return;
        signInAs(officer.get());
        assertThatThrownBy(() -> stockTakeService.varianceReport(AdjustmentQuery.builder().branchId(other.get()).build()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void anUnknownReasonIsRefusedRatherThanIgnored() {
        assertThatThrownBy(() -> StockTakeController.parseReason("MISPLACED"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(StockTakeController.parseReason("damaged")).isEqualTo(AdjustmentReason.DAMAGED);
        assertThat(StockTakeController.parseReason(" ")).isNull();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private User superAdmin() {
        return userRepository.findAll().stream().filter(StockTakeAssetsAndHistoryTest::isSuperAdmin)
                .findFirst().orElse(null);
    }

    private static boolean holds(User u, String authority) {
        return u.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private static boolean isSuperAdmin(User u) {
        Stream<Role> roles = Stream.concat(
                Stream.of(u.getTitle() != null ? u.getTitle().getRole() : null),
                Stream.concat(
                        u.getAdditionalRoles() != null ? u.getAdditionalRoles().stream() : Stream.empty(),
                        u.getUnit() != null && u.getUnit().getRoles() != null ? u.getUnit().getRoles().stream() : Stream.empty()));
        return roles.anyMatch(r -> r != null && Constants.SUPER_ADMIN.equals(r.getName()));
    }

    private void signInAs(User user) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(user));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}

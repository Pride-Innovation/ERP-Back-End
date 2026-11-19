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
import pride.bank.erp.config.JwtService;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.role.Role;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreBalanceRepository;
import pride.bank.erp.store.StoreRepository;
import pride.bank.erp.store.StoreType;
import pride.bank.erp.stocktake.StockTakeDTOs.CountView;
import pride.bank.erp.stocktake.StockTakeDTOs.CountableStore;
import pride.bank.erp.stocktake.StockTakeDTOs.OpenCountRequest;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stock takes are done by the staff at the shelves: anyone granted the stock-take permissions acts
 * only on stores at their own branch, whatever cross-branch inventory reach they hold for other work.
 * Head Office still <em>sees</em> every branch's counts. The super administrator is exempt.
 *
 * <p>Before this, acting followed the inventory MANAGE scope, so the Admin and Infra units - which
 * hold MANAGE_ALL_BRANCH_INVENTORY for stock-order work - would have opened, approved and posted
 * counts at every branch the moment they were granted the stock-take permissions.
 */
@SpringBootTest
@Transactional
class StockTakeBranchRuleTest {

    @Autowired StockTakeService stockTakeService;
    @Autowired StoreRepository storeRepository;
    @Autowired StoreBalanceRepository storeBalanceRepository;
    @Autowired StockCountRepository countRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    @AfterEach
    void signOut() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private CountView open(Store store) throws Exception {
        OpenCountRequest request = new OpenCountRequest();
        request.setStoreId(store.getId());
        request.setBlind(true);
        return stockTakeService.openCount(request);
    }

    /** Stores with stock lines to count, at a branch that is (or is not) Head Office. */
    private List<Store> countable(boolean headOffice) {
        return storeRepository.findAll().stream()
                .filter(s -> s.getStoreType() != StoreType.COURIER && s.getLocation() != null)
                .filter(s -> s.getLocation().isHeadOffice() == headOffice)
                .filter(s -> storeBalanceRepository.findByStore_Id(s.getId()).stream()
                        .anyMatch(StockTakeService::isCountableLine))
                // A store with an unfinished count cannot be opened again - that is a real count
                // somebody is doing, so skip it rather than fail on it.
                .filter(s -> countRepository.findOpenForStore(s.getId()).isEmpty())
                .toList();
    }

    @Test
    void headOfficeWithCrossBranchReachCountsOnlyAtHeadOfficeButSeesTheBranches() throws Exception {
        Optional<User> hoOfficer = userRepository.findAll().stream()
                .filter(u -> u.getBranch() != null && u.getBranch().isHeadOffice() && !isSuperAdmin(u))
                .filter(u -> holds(u, "MANAGE_ALL_BRANCH_INVENTORY") && holds(u, "VIEW_ALL_BRANCH_INVENTORY"))
                .findFirst();
        List<Store> branchStores = countable(false);
        List<Store> hoStores = countable(true);
        if (hoOfficer.isEmpty() || branchStores.isEmpty()) {
            System.out.println("stock take rule test: NO Head Office cross-branch officer or branch stock - unexercised");
            return;
        }
        System.out.printf("stock take rule test: Head Office officer %d, %d branch store(s), %d Head Office store(s)%n",
                hoOfficer.get().getId(), branchStores.size(), hoStores.size());

        // A branch count, opened by someone at that branch, for Head Office to look at.
        Store branchStore = branchStores.get(0);
        User branchOfficer = userRepository.findAll().stream()
                .filter(u -> u.getBranch() != null && u.getBranch().getId().equals(branchStore.getLocation().getId()))
                .findFirst().orElseThrow();
        signInAs(branchOfficer);
        CountView branchCount = open(branchStore);
        assertThat(branchCount.isActionable()).isTrue();
        signOut();

        signInAs(hoOfficer.get());
        // Cannot act at the branch, despite MANAGE_ALL_BRANCH_INVENTORY.
        assertThatThrownBy(() -> open(branchStores.get(branchStores.size() > 1 ? 1 : 0)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> stockTakeService.submit(branchCount.getId())).isInstanceOf(AccessDeniedException.class);
        // But sees it, read-only.
        CountView seen = stockTakeService.fetch(branchCount.getId());
        assertThat(seen.isActionable()).isFalse();
        assertThat(stockTakeService.list(null)).extracting(CountView::getId).contains(branchCount.getId());
        // And is offered only Head Office stores to open.
        assertThat(stockTakeService.countableStores()).allSatisfy(s ->
                assertThat(hoStores.stream().map(Store::getId)).contains(s.getId()));
        if (!hoStores.isEmpty()) {
            assertThat(open(hoStores.get(0)).isActionable()).isTrue();
        }
    }

    @Test
    void aBranchOfficerCountsAtTheirOwnBranchOnly() throws Exception {
        List<Store> branchStores = countable(false);
        Optional<Store> mine = branchStores.stream().findFirst();
        if (mine.isEmpty()) return;
        Long branch = mine.get().getLocation().getId();
        Optional<User> officer = userRepository.findAll().stream()
                .filter(u -> u.getBranch() != null && u.getBranch().getId().equals(branch) && !isSuperAdmin(u))
                .findFirst();
        if (officer.isEmpty()) return;
        signInAs(officer.get());

        List<CountableStore> offered = stockTakeService.countableStores();
        assertThat(offered).isNotEmpty();
        assertThat(offered).allSatisfy(s -> {
            Store store = storeRepository.findById(s.getId()).orElseThrow();
            assertThat(store.getLocation().getId()).isEqualTo(branch);
            assertThat(s.getLineCount()).isPositive();
            assertThat(store.getStoreType()).isNotEqualTo(StoreType.COURIER);
        });
        assertThat(open(mine.get()).isActionable()).isTrue();

        Optional<Store> elsewhere = Stream.concat(branchStores.stream(), countable(true).stream())
                .filter(s -> !s.getLocation().getId().equals(branch)).findFirst();
        if (elsewhere.isPresent()) {
            assertThatThrownBy(() -> open(elsewhere.get())).isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void aStoreWithNothingToCountIsRefusedAndLeavesNoSheet() throws Exception {
        Optional<User> admin = userRepository.findAll().stream().filter(StockTakeBranchRuleTest::isSuperAdmin).findFirst();
        Optional<Store> empty = storeRepository.findAll().stream()
                .filter(s -> s.getStoreType() == StoreType.IT || s.getStoreType() == StoreType.DISPOSAL)
                .filter(s -> storeBalanceRepository.findByStore_Id(s.getId()).isEmpty())
                .findFirst();
        if (admin.isEmpty() || empty.isEmpty()) return;
        signInAs(admin.get());

        int before = stockTakeService.list(null).size();
        assertThatThrownBy(() -> open(empty.get()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("track individual assets");
        assertThat(stockTakeService.list(null)).hasSize(before);
        assertThat(stockTakeService.countableStores()).extracting(CountableStore::getId).doesNotContain(empty.get().getId());
    }

    @Test
    void theSuperAdministratorMayActAnywhere() throws Exception {
        Optional<User> admin = userRepository.findAll().stream().filter(StockTakeBranchRuleTest::isSuperAdmin).findFirst();
        List<Store> branchStores = countable(false);
        if (admin.isEmpty() || branchStores.isEmpty()) return;
        signInAs(admin.get());
        assertThat(open(branchStores.get(0)).isActionable()).isTrue();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

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

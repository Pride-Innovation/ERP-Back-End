package pride.bank.erp.request;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.inventory.StockAvailabilityService;
import pride.bank.erp.issuance.IssuanceCommodityDTO;
import pride.bank.erp.issuance.IssuanceDTO;
import pride.bank.erp.issuance.IssuanceRepository;
import pride.bank.erp.issuance.IssuanceService;
import pride.bank.erp.notification.Email;
import pride.bank.erp.role.Role;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreBalance;
import pride.bank.erp.store.StoreBalanceRepository;
import pride.bank.erp.store.StoreRepository;
import pride.bank.erp.store.StoreType;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A branch issues from its own store, and the BM approves later. Between the two, what was issued
 * stays on the shelf (approval is what debits it) but must not be issued again: the BOM may issue
 * several requests before the BM gets to any of them.
 *
 * <p>Runs the real {@link IssuanceService} against a real branch store, inside a rolled-back
 * transaction. The requests carry no workflow, so issuing takes the legacy path - which sends mail,
 * hence the mocked {@link Email}.
 */
@SpringBootTest
@Transactional
class BranchIssuanceReservationTest {

    @Autowired IssuanceService issuanceService;
    @Autowired IssuanceRepository issuanceRepository;
    @Autowired StockAvailabilityService availability;
    @Autowired StoreRepository storeRepository;
    @Autowired pride.bank.erp.store.StoreService storeService;
    @Autowired StoreBalanceRepository balanceRepository;
    @Autowired RequestRepository requestRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;
    @MockBean Email email;

    @AfterEach
    void signOut() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    /** A branch BOM, two other people at the branch, and a consumable the branch store can still give. */
    private record Shelf(User bom, User first, User second, Store store, Commodity commodity, int free) {}

    private Optional<Shelf> shelf() {
        List<User> users = userRepository.findAll().stream()
                .filter(u -> u.getBranch() != null && !u.getBranch().isHeadOffice() && u.isEnabled() && !u.isBlocked())
                .toList();
        for (User bom : users) {
            if (!holdsRole(bom, "BRANCH_OPERATIONS_MANAGER")) continue;
            Long branchId = bom.getBranch().getId();
            List<User> others = users.stream()
                    .filter(u -> u.getBranch().getId().equals(branchId) && !u.getId().equals(bom.getId()))
                    .filter(u -> !holdsRole(u, "BRANCH_OPERATIONS_MANAGER") && !holdsRole(u, "BRANCH_MANAGER"))
                    .toList();
            if (others.size() < 2) continue;
            Store store = storeRepository.findByLocation_IdAndStoreTypeAndDepartmentIsNull(branchId, StoreType.ADMIN).orElse(null);
            if (store == null) continue;
            for (StoreBalance b : balanceRepository.findByStore_Id(store.getId())) {
                int free = availability.available(store, b.getCommodity());
                if (free >= 1) {
                    commodityId = b.getCommodity().getId();
                    System.out.printf("reservation test: %s, commodity %d '%s', %d free of %d on hand%n",
                            store.getName(), b.getCommodity().getId(), b.getCommodity().getName(), free, b.getQuantity());
                    return Optional.of(new Shelf(bom, others.get(0), others.get(1), store, b.getCommodity(), free));
                }
            }
        }
        System.out.println("reservation test: NO branch with a BOM, two colleagues and free stock - unexercised");
        return Optional.empty();
    }

    @Test
    void stockIssuedButNotYetApprovedCannotBeIssuedAgain() throws Exception {
        Optional<Shelf> found = shelf();
        if (found.isEmpty()) return;
        Shelf s = found.get();
        signInAs(s.bom());
        int onHandBefore = onHand(s);

        // The BOM issues everything that is free to the first request.
        issuanceService.issueCommodities(issue(request(s.first()), s.first(), s.free()));
        assertThat(onHand(s)).as("issuing does not take it off the shelf - approval does").isEqualTo(onHandBefore);
        assertThat(availability.available(s.store(), s.commodity())).as("but it is promised").isZero();

        // And the store page says so, beside the shelf figure.
        StoreBalance listed = storeService.fetchStoreDetailsPerBranchPerAssetType(
                        s.bom().getBranch().getId(), null, StoreType.ADMIN, s.commodity().getName(), null,
                        org.springframework.data.domain.PageRequest.of(0, 50))
                .getContent().stream()
                .filter(b -> b.getCommodity().getId().equals(s.commodity().getId()) && b.getStore().getId().equals(s.store().getId()))
                .findFirst().orElseThrow();
        assertThat(listed.getQuantity()).isEqualTo(onHandBefore);
        assertThat(listed.getPromised()).as("the store page shows it promised").isEqualTo(s.free());
        assertThat(listed.getAvailable()).as("and none of it free").isZero();

        // A second request, before the BM has approved the first, cannot be given the same units.
        Request second = request(s.second());
        assertThatThrownBy(() -> issuanceService.issueCommodities(issue(second, s.second(), 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already promised");
        assertThat(issuanceRepository.findByRequestId(second.getId())).as("and nothing was recorded for it").isEmpty();
    }

    @Test
    void approvingTheIssuanceTakesItOffTheShelfAndReleasesNothing() throws Exception {
        Optional<Shelf> found = shelf();
        if (found.isEmpty()) return;
        Shelf s = found.get();
        signInAs(s.bom());
        int onHandBefore = onHand(s);
        Request first = request(s.first());
        issuanceService.issueCommodities(issue(first, s.first(), s.free()));

        issuanceService.settleIssuanceStock(issuanceRepository.findByRequestId(first.getId()).orElseThrow(), true);

        assertThat(onHand(s)).as("approval debits the shelf").isEqualTo(onHandBefore - s.free());
        assertThat(availability.available(s.store(), s.commodity())).as("and what is left is still not free").isZero();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private int onHand(Shelf s) {
        return balanceRepository.findByStore_Id(s.store().getId()).stream()
                .filter(b -> b.getCommodity().getId().equals(s.commodity().getId()))
                .mapToInt(StoreBalance::getQuantity).sum();
    }

    private Request request(User requester) {
        return requestRepository.saveAndFlush(Request.builder()
                .name("Reservation test").priority("Low").description("test")
                .requester(requester).forStoreRestock(false).deleted(false).build());
    }

    private IssuanceDTO issue(Request request, User requester, int quantity) {
        IssuanceDTO dto = new IssuanceDTO();
        dto.setRequestId(request.getId());
        dto.setRequester(requester.getId());
        dto.setCommodities(List.of(IssuanceCommodityDTO.builder().commodityId(commodityId).quantity(quantity).build()));
        return dto;
    }

    private Long commodityId;

    private static boolean holdsRole(User u, String name) {
        return Stream.concat(
                        Stream.of(u.getTitle() != null ? u.getTitle().getRole() : null),
                        Stream.concat(
                                u.getAdditionalRoles() != null ? u.getAdditionalRoles().stream() : Stream.empty(),
                                u.getUnit() != null && u.getUnit().getRoles() != null ? u.getUnit().getRoles().stream() : Stream.empty()))
                .anyMatch(r -> r != null && name.equals(((Role) r).getName()));
    }

    private void signInAs(User user) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(user));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}

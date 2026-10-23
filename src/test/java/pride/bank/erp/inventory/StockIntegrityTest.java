package pride.bank.erp.inventory;

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
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.issuance.Issuance;
import pride.bank.erp.issuance.IssuanceRepository;
import pride.bank.erp.issuance.IssuanceService;
import pride.bank.erp.issuedCommodity.IssuedCommodity;
import pride.bank.erp.issuedCommodity.IssuedCommodityRepository;
import pride.bank.erp.movement.Movement;
import pride.bank.erp.movement.MovementDTO;
import pride.bank.erp.movement.MovementItemDTO;
import pride.bank.erp.movement.MovementService;
import pride.bank.erp.movement.MovementType;
import pride.bank.erp.request.Request;
import pride.bank.erp.request.RequestRepository;
import pride.bank.erp.role.Role;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreBalance;
import pride.bank.erp.store.StoreBalanceRepository;
import pride.bank.erp.store.StoreRepository;
import pride.bank.erp.store.StoreService;
import pride.bank.erp.store.StoreType;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 1 of stock depletion: stock leaves at approval, is promised from the moment it is asked for,
 * and the movement form can only draw what is really on its own branch's shelf.
 *
 * <p>Runs against live data inside a rolled-back transaction; each case prints what it used, and says
 * so when the data offers nothing to exercise.
 */
@SpringBootTest
@Transactional
class StockIntegrityTest {

    @Autowired MovementService movementService;
    @Autowired StockAvailabilityService availability;
    @Autowired IssuanceService issuanceService;
    @Autowired IssuanceRepository issuanceRepository;
    @Autowired IssuedCommodityRepository issuedCommodityRepository;
    @Autowired RequestRepository requestRepository;
    @Autowired StoreService storeService;
    @Autowired StoreRepository storeRepository;
    @Autowired StoreBalanceRepository balanceRepository;
    @Autowired AssetRepository assetRepository;
    @Autowired pride.bank.erp.movement.MovementRepository movementRepository;
    @Autowired pride.bank.erp.status.StatusRepository statusRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    @AfterEach
    void signOut() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    /** Someone who may create movements, their branch's Admin store, and a commodity it can still give. */
    private record Shelf(User officer, User colleague, Store store, Commodity commodity, int available) {}

    private Optional<Shelf> shelf() {
        for (User u : userRepository.findAll()) {
            if (u.getBranch() == null || isSuperAdmin(u) || !holds(u, Constants.CREATE_MOVEMENT)
                    || !holds(u, Constants.ISSUE_WITHOUT_REQUEST)) continue;
            Store store = storeService.resolveAdminStore(u.getBranch());
            User colleague = userRepository.findAll().stream()
                    .filter(o -> !o.getId().equals(u.getId()) && o.getBranch() != null
                            && o.getBranch().getId().equals(u.getBranch().getId()))
                    .findFirst().orElse(null);
            if (colleague == null) continue;
            for (StoreBalance b : balanceRepository.findByStore_Id(store.getId())) {
                int free = availability.available(store, b.getCommodity());
                if (free >= 2) {
                    System.out.printf("stock integrity test: officer %d, store %s, commodity %d, %d available%n",
                            u.getId(), store.getName(), b.getCommodity().getId(), free);
                    return Optional.of(new Shelf(u, colleague, store, b.getCommodity(), free));
                }
            }
        }
        System.out.println("stock integrity test: NO officer with a consumable to move at their branch - unexercised");
        return Optional.empty();
    }

    private MovementDTO handOver(Shelf s, int quantity) {
        return MovementDTO.builder()
                .movementType(MovementType.DEPARTMENT_TRANSFER)
                .sourceStoreId(s.store().getId())
                .recipientUserId(s.colleague().getId())
                // Giving to a person without a request is the exception path: it needs a reason.
                .remarks("Stock integrity test")
                .items(List.of(MovementItemDTO.builder().commodityId(s.commodity().getId()).quantity(quantity).build()))
                .build();
    }

    @Test
    void theFormCreatesOnlyItsOwnTypes() throws Exception {
        Optional<Shelf> s = shelf();
        if (s.isEmpty()) return;
        signInAs(s.get().officer());
        MovementDTO disposal = handOver(s.get(), 1);
        disposal.setMovementType(MovementType.DISPOSAL_TRANSFER);
        assertThatThrownBy(() -> movementService.createFromForm(disposal))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Only a replenishment");
    }

    @Test
    void stockPromisedToOneMovementCannotBePromisedAgainUntilItIsReleased() throws Exception {
        Optional<Shelf> found = shelf();
        if (found.isEmpty()) return;
        Shelf s = found.get();
        signInAs(s.officer());

        Movement first = movementService.createFromForm(handOver(s, s.available()));
        assertThat(availability.available(s.store(), s.commodity())).as("all of it is promised").isZero();
        assertThatThrownBy(() -> movementService.createFromForm(handOver(s, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already promised");

        movementService.cancelMovement(first.getId(), "test");
        assertThat(availability.available(s.store(), s.commodity())).as("cancelling releases it").isEqualTo(s.available());
    }

    @Test
    void youDrawOnlyFromYourOwnBranchButMaySendAnywhere() throws Exception {
        Optional<Shelf> found = shelf();
        if (found.isEmpty()) return;
        Shelf s = found.get();
        Store elsewhere = storeRepository.findAll().stream()
                .filter(st -> st.getStoreType() == StoreType.ADMIN && st.getDepartment() == null && st.getLocation() != null)
                .filter(st -> !st.getLocation().getId().equals(s.officer().getBranch().getId()))
                .findFirst().orElse(null);
        if (elsewhere == null) return;
        signInAs(s.officer());

        MovementDTO draw = handOver(s, 1);
        draw.setSourceStoreId(elsewhere.getId());
        assertThatThrownBy(() -> movementService.createFromForm(draw))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("your own branch");

        // From their own store to another branch's: the draw rule allows it. It then climbs the
        // approval ladder, which may or may not resolve on this data - not the subject here.
        MovementDTO send = MovementDTO.builder()
                .movementType(MovementType.REPLENISHMENT)
                .sourceStoreId(s.store().getId())
                .destStoreId(elsewhere.getId())
                .items(List.of(MovementItemDTO.builder().commodityId(s.commodity().getId()).quantity(1).build()))
                .build();
        try {
            assertThat(movementService.createFromForm(send).getId()).isNotNull();
        } catch (AccessDeniedException e) {
            assertThat(e.getMessage()).as("sending to another branch is allowed").doesNotContain("your own branch");
        } catch (RuntimeException approvalOutcome) {
            System.out.println("stock integrity test: cross-branch send passed the draw rule, then: " + approvalOutcome.getMessage());
        }
    }

    @Test
    void anAssetMustBeFreeOnTheSourceShelf() throws Exception {
        Optional<Shelf> found = shelf();
        if (found.isEmpty()) return;
        Shelf s = found.get();
        signInAs(s.officer());

        // Not in this store: anything held by a person is somewhere else.
        Asset held = assetRepository.findAll().stream()
                .filter(a -> a.getAssignedTo() != null && !Boolean.TRUE.equals(a.getDeleted()))
                .findFirst().orElse(null);
        if (held != null) {
            MovementDTO dto = handOver(s, 1);
            dto.setItems(List.of(MovementItemDTO.builder().assetId(held.getId()).quantity(1).build()));
            assertThatThrownBy(() -> movementService.createFromForm(dto))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("is not in");
        }

        // On the shelf and free: movable once, and then it is on that movement. A free asset from any
        // store is placed on this shelf inside the rolled-back transaction when this one has none.
        // "Free" excludes assets already on an open movement - live data has some that read
        // Available for Issuance while sitting on a movement stuck in transit.
        java.util.Set<Long> onMovements = new java.util.HashSet<>(movementRepository.findAssetIdsOnOpenMovements(
                assetRepository.findAll().stream().map(Asset::getId).toList()));
        // Measured: every asset sitting in a store today is a loaner, away, travelling or already on a
        // movement, so a plain shelf item is made from one on no movement at all.
        Asset free = assetRepository.findAll().stream()
                .filter(a -> !onMovements.contains(a.getId()))
                .filter(a -> !Boolean.TRUE.equals(a.getDeleted()) && !Boolean.TRUE.equals(a.getDisposed()))
                .findFirst().orElse(null);
        if (free == null) {
            System.out.println("stock integrity test: NO asset off every movement - double booking unexercised");
            return;
        }
        free.setCurrentStore(s.store());
        free.setAssignedTo(null);
        free.setTemporaryPool(false);
        free.setAssetStatus(statusRepository.findByStatus("issuanceAvailable").orElseThrow());
        assetRepository.saveAndFlush(free);
        MovementDTO once = handOver(s, 1);
        once.setItems(List.of(MovementItemDTO.builder().assetId(free.getId()).quantity(1).build()));
        Movement first = movementService.createFromForm(once);
        MovementDTO twice = handOver(s, 1);
        twice.setItems(List.of(MovementItemDTO.builder().assetId(free.getId()).quantity(1).build()));
        assertThatThrownBy(() -> movementService.createFromForm(twice))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already on movement #" + first.getId());
    }

    /**
     * Issuing promises the stock; approving debits it; an issuance settled under the old rule is
     * never debited again. Made directly on the entities, since the request workflow is not the
     * subject here.
     */
    @Test
    void anIssuanceHoldsItsStockUntilApprovalThenDebitsItOnce() throws Exception {
        Optional<Shelf> found = shelf();
        Request request = requestRepository.findAll().stream()
                .filter(r -> !Boolean.TRUE.equals(r.getDeleted()))
                .filter(r -> r.getStatus() == null || !"requestRejected".equals(r.getStatus().getStatus()))
                .findFirst().orElse(null);
        if (found.isEmpty() || request == null) return;
        Shelf s = found.get();
        signInAs(s.officer());

        int onHandBefore = onHand(s);
        Issuance issuance = issuanceRepository.save(Issuance.builder()
                .requester(s.colleague()).issuer(s.officer()).request(request).stockSettled(false).build());
        issuedCommodityRepository.save(IssuedCommodity.builder()
                .issuance(issuance).commodity(s.commodity()).quantity(1).build());
        issuedCommodityRepository.flush();

        assertThat(onHand(s)).as("issuing debits nothing").isEqualTo(onHandBefore);
        assertThat(availability.available(s.store(), s.commodity())).as("but promises it").isEqualTo(s.available() - 1);

        issuanceService.settleIssuanceStock(issuance, true);
        assertThat(onHand(s)).as("approval debits it").isEqualTo(onHandBefore - 1);
        assertThat(availability.available(s.store(), s.commodity())).as("and the promise is spent").isEqualTo(s.available() - 1);

        issuanceService.settleIssuanceStock(issuance, true);
        assertThat(onHand(s)).as("once").isEqualTo(onHandBefore - 1);

        Issuance legacy = issuanceRepository.save(Issuance.builder()
                .requester(s.colleague()).issuer(s.officer()).request(request).stockSettled(true).build());
        issuedCommodityRepository.save(IssuedCommodity.builder()
                .issuance(legacy).commodity(s.commodity()).quantity(1).build());
        issuanceService.settleIssuanceStock(legacy, true);
        assertThat(onHand(s)).as("settled under the old rule: not debited again").isEqualTo(onHandBefore - 1);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private int onHand(Shelf s) {
        return balanceRepository.findByStore_IdAndCommodity_Id(s.store().getId(), s.commodity().getId())
                .map(StoreBalance::getQuantity).orElse(0);
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

package pride.bank.erp.request;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pride.bank.erp.appnotification.AppNotificationRepository;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityRepository;
import pride.bank.erp.config.IssueWithoutRequestPermissionSeeder;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.config.seed.SeederStateRepository;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.inventory.IssueStockCheckService;
import pride.bank.erp.inventory.StockAvailabilityService;
import pride.bank.erp.issuance.Issuance;
import pride.bank.erp.issuance.IssuanceDTO;
import pride.bank.erp.issuance.IssuanceRepository;
import pride.bank.erp.issuance.IssuanceService;
import pride.bank.erp.movement.MovementDTO;
import pride.bank.erp.movement.MovementService;
import pride.bank.erp.movement.MovementType;
import pride.bank.erp.requestCommodity.RequestCommodity;
import pride.bank.erp.requestCommodity.RequestCommodityRepository;
import pride.bank.erp.role.Role;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreRepository;
import pride.bank.erp.store.StoreType;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;
import pride.bank.erp.workflow.ApprovalStep;
import pride.bank.erp.workflow.ApproverSubject;
import pride.bank.erp.workflow.StepApproverType;
import pride.bank.erp.workflow.StepType;
import pride.bank.erp.workflowinstance.WorkflowEngineService;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3: requests handled at the branch.
 *
 * <ul>
 *   <li>A branch Issue / issuance-approval step goes to the BOM or BM, never to the requester or the
 *       issuer; with nobody left it waits rather than being skipped.</li>
 *   <li>The issuance and approval services refuse the requester outright, whatever the routing.</li>
 *   <li>The request page and the notices say when the issuing store is short, once each.</li>
 *   <li>Giving to a person with no request needs ISSUE_WITHOUT_REQUEST and a reason.</li>
 * </ul>
 * Live data, rolled back; each case says when the data offers nothing to exercise.
 */
@SpringBootTest
@Transactional
class BranchIssuanceTest {

    @Autowired WorkflowEngineService engine;
    @Autowired IssuanceService issuanceService;
    @Autowired IssuanceRepository issuanceRepository;
    @Autowired IssueStockCheckService stockCheck;
    @Autowired StockAvailabilityService availability;
    @Autowired MovementService movementService;
    @Autowired RequestRepository requestRepository;
    @Autowired RequestCommodityRepository requestCommodityRepository;
    @Autowired CommodityRepository commodityRepository;
    @Autowired StoreRepository storeRepository;
    @Autowired UserRepository userRepository;
    @Autowired AppNotificationRepository notificationRepository;
    @Autowired SeederStateRepository seederStateRepository;
    @Autowired JwtService jwtService;

    @AfterEach
    void signOut() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    /** A branch with a BOM, a BM and somebody else, all active. */
    private record Cast(Branch branch, User bom, User bm, User officer) {}

    private Optional<Cast> cast() {
        List<User> users = userRepository.findAll().stream()
                .filter(u -> u.getBranch() != null && !u.getBranch().isHeadOffice())
                .filter(u -> u.isEnabled() && !u.isBlocked())
                .sorted(java.util.Comparator.comparing(User::getId))
                .toList();
        for (User bom : users) {
            if (!holdsRole(bom, "BRANCH_OPERATIONS_MANAGER")) continue;
            Long branchId = bom.getBranch().getId();
            Optional<User> bm = users.stream()
                    .filter(u -> u.getBranch().getId().equals(branchId) && holdsRole(u, "BRANCH_MANAGER")).findFirst();
            Optional<User> officer = users.stream()
                    .filter(u -> u.getBranch().getId().equals(branchId)
                            && !holdsRole(u, "BRANCH_MANAGER") && !holdsRole(u, "BRANCH_OPERATIONS_MANAGER"))
                    .findFirst();
            // The engine takes the lowest-id holder when the branch fields are unset; so does this.
            boolean lowestBom = users.stream().filter(u -> u.getBranch().getId().equals(branchId)
                    && holdsRole(u, "BRANCH_OPERATIONS_MANAGER")).findFirst().map(u -> u.getId().equals(bom.getId())).orElse(false);
            if (bm.isPresent() && officer.isPresent() && lowestBom
                    && bom.getBranch().getBranchManager() == null && bom.getBranch().getBranchOperationsManager() == null) {
                return Optional.of(new Cast(bom.getBranch(), bom, bm.get(), officer.get()));
            }
        }
        System.out.println("branch issuance test: NO branch with an unset BM/BOM field, a BOM, a BM and another user");
        return Optional.empty();
    }

    private Request request(User requester) {
        return requestRepository.saveAndFlush(Request.builder()
                .name("Branch issuance test").priority("Low").description("test")
                .requester(requester).forStoreRestock(false).deleted(false).build());
    }

    private static ApprovalStep issueStep(StepApproverType who) {
        return ApprovalStep.builder().stepOrder(3).stepName("Issue").stepType(StepType.ISSUANCE).approverType(who).build();
    }

    private static ApprovalStep issuanceApprovalStep(StepApproverType who) {
        return ApprovalStep.builder().stepOrder(4).stepName("Approve issuance").stepType(StepType.REQUEST_APPROVAL)
                .approverSubject(ApproverSubject.ISSUER).approverType(who).build();
    }

    // ── routing ─────────────────────────────────────────────────────────────

    @Test
    void theBomIssuesAnOfficersRequestAndTheBmIssuesTheBoms() {
        Optional<Cast> c = cast();
        if (c.isEmpty()) return;
        assertThat(idOf(engine.resolveApprover(issueStep(StepApproverType.BOM), request(c.get().officer()))))
                .isEqualTo(c.get().bom().getId());
        assertThat(idOf(engine.resolveApprover(issueStep(StepApproverType.BOM), request(c.get().bom()))))
                .as("the BOM never issues their own request; the BM does")
                .isEqualTo(c.get().bm().getId());
    }

    @Test
    void anIssuanceIsApprovedByNeitherTheRequesterNorTheIssuer() {
        Optional<Cast> c = cast();
        if (c.isEmpty()) return;
        signInAs(c.get().bom()); // created_by is filled from the signed-in user
        // An officer's request, issued by the BOM: the BM approves it.
        Request fromOfficer = request(c.get().officer());
        issuanceRepository.saveAndFlush(Issuance.builder().request(fromOfficer)
                .requester(c.get().officer()).issuer(c.get().bom()).stockSettled(false).build());
        assertThat(idOf(engine.resolveApprover(issuanceApprovalStep(StepApproverType.BRANCH_MANAGER), fromOfficer)))
                .isEqualTo(c.get().bm().getId());
        // Configured the other way round, the BOM is the issuer, so it falls to the BM all the same.
        assertThat(idOf(engine.resolveApprover(issuanceApprovalStep(StepApproverType.BOM), fromOfficer)))
                .isEqualTo(c.get().bm().getId());
    }

    @Test
    void withNobodyLeftTheStepWaitsAndIsNotSkipped() {
        Optional<Cast> c = cast();
        if (c.isEmpty()) return;
        signInAs(c.get().bm()); // created_by is filled from the signed-in user
        // The BOM asked and the BM issued: neither may approve it.
        Request fromBom = request(c.get().bom());
        issuanceRepository.saveAndFlush(Issuance.builder().request(fromBom)
                .requester(c.get().bom()).issuer(c.get().bm()).stockSettled(false).build());
        ApprovalStep step = issuanceApprovalStep(StepApproverType.BRANCH_MANAGER);
        assertThat(engine.resolveApprover(step, fromBom)).as("nobody may act, so nobody is routed").isNull();

        Object target = AopTestUtils.getUltimateTargetObject(engine);
        Boolean skipped = ReflectionTestUtils.invokeMethod(target, "shouldSkipStep", step, fromBom, Set.<Integer>of());
        assertThat(skipped).as("skipping an issuance step would skip the issuing; it waits instead").isFalse();
    }

    // ── refusals behind the routing ─────────────────────────────────────────

    @Test
    void nobodyIssuesTheirOwnRequest() {
        Optional<Cast> c = cast();
        if (c.isEmpty()) return;
        Request own = request(c.get().bom());
        signInAs(c.get().bom());
        IssuanceDTO dto = new IssuanceDTO();
        dto.setRequestId(own.getId());
        assertThatThrownBy(() -> issuanceService.issueCommodities(dto))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("your own request");
    }

    // ── waiting for stock ───────────────────────────────────────────────────

    @Test
    void theStockCheckNamesTheIssuersStoreAndWhatItIsShort() {
        Optional<Cast> c = cast();
        if (c.isEmpty()) return;
        Store store = storeRepository.findByLocation_IdAndStoreTypeAndDepartmentIsNull(
                c.get().branch().getId(), StoreType.ADMIN).orElse(null);
        Commodity commodity = commodityRepository.findAll().stream().findFirst().orElse(null);
        if (store == null || commodity == null) return;

        Request request = request(c.get().officer());
        request.setCurrentApprover(c.get().bom());
        requestRepository.saveAndFlush(request);
        int onHand = availability.available(store, commodity);
        requestCommodityRepository.saveAndFlush(RequestCommodity.builder()
                .request(request).commodity(commodity).quantity(onHand + 1_000_000).build());

        IssueStockCheckService.StockCheck check = stockCheck.check(request.getId());
        assertThat(check.storeId()).as("the issuer's branch store").isEqualTo(store.getId());
        assertThat(check.waiting()).isTrue();
        assertThat(check.lines()).singleElement().satisfies(l -> {
            assertThat(l.commodityId()).isEqualTo(commodity.getId());
            assertThat(l.shortfall()).isTrue();
        });
    }

    @Test
    void theRequesterAndIssuerAreToldOnceAndOnlyAfterCommit() {
        Optional<Cast> c = cast();
        if (c.isEmpty()) return;
        Commodity commodity = commodityRepository.findAll().stream().findFirst().orElse(null);
        if (commodity == null) return;
        Request request = request(c.get().officer());
        request.setCurrentApprover(c.get().bom());
        requestRepository.saveAndFlush(request);
        requestCommodityRepository.saveAndFlush(RequestCommodity.builder()
                .request(request).commodity(commodity).quantity(1_000_000).build());

        int before = TransactionSynchronizationManager.getSynchronizations().size();
        stockCheck.onIssueStepReached(request.getId());
        assertThat(waitingNotices(request, c.get().officer())).as("nothing is said before commit").isZero();

        // Commit, as far as the notice is concerned: run what was deferred, twice.
        List<TransactionSynchronization> deferred = TransactionSynchronizationManager.getSynchronizations()
                .subList(before, TransactionSynchronizationManager.getSynchronizations().size());
        assertThat(deferred).hasSize(1);
        deferred.get(0).afterCommit();
        deferred.get(0).afterCommit();

        assertThat(waitingNotices(request, c.get().officer())).as("the requester, once").isEqualTo(1);
        assertThat(waitingNotices(request, c.get().bom())).as("the issuer, once").isEqualTo(1);
    }

    private long waitingNotices(Request request, User to) {
        notificationRepository.flush();
        return notificationRepository.findAll().stream()
                .filter(n -> "STOCK_WAITING".equals(n.getType()) && request.getId().equals(n.getEntityId())
                        && n.getRecipient() != null && to.getId().equals(n.getRecipient().getId()))
                .count();
    }

    // ── the exception path ──────────────────────────────────────────────────

    @Test
    void theExceptionPermissionWasGrantedOnce() {
        assertThat(seederStateRepository.existsById(IssueWithoutRequestPermissionSeeder.MARKER)).isTrue();
    }

    @Test
    void givingToAPersonWithoutARequestNeedsThePermissionAndAReason() {
        Optional<User> without = userRepository.findAll().stream()
                .filter(u -> !isSuperAdmin(u) && !holds(u, Constants.ISSUE_WITHOUT_REQUEST))
                .findFirst();
        Optional<User> with = userRepository.findAll().stream()
                .filter(u -> !isSuperAdmin(u) && holds(u, Constants.ISSUE_WITHOUT_REQUEST))
                .findFirst();

        if (without.isPresent()) {
            signInAs(without.get());
            assertThatThrownBy(() -> movementService.createFromForm(toAPerson(without.get(), "a reason")))
                    .isInstanceOf(AccessDeniedException.class).hasMessageContaining("ISSUE_WITHOUT_REQUEST");
            signOut();
        } else {
            System.out.println("branch issuance test: NO user without ISSUE_WITHOUT_REQUEST - refusal unexercised");
        }
        if (with.isPresent()) {
            signInAs(with.get());
            assertThatThrownBy(() -> movementService.createFromForm(toAPerson(with.get(), "  ")))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reason");
        } else {
            System.out.println("branch issuance test: NO user holding ISSUE_WITHOUT_REQUEST - reason rule unexercised");
        }
    }

    private MovementDTO toAPerson(User recipient, String remarks) {
        MovementDTO dto = new MovementDTO();
        dto.setMovementType(MovementType.REPLENISHMENT);
        dto.setSourceStoreId(storeRepository.findAll().get(0).getId());
        dto.setRecipientUserId(recipient.getId());
        dto.setRemarks(remarks);
        return dto;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** An id, not the User: printing a User in a failure message overflows the stack. */
    private static Long idOf(User u) {
        return u == null ? null : u.getId();
    }

    private static boolean holds(User u, String authority) {
        return u.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private static Stream<Role> roles(User u) {
        return Stream.concat(
                Stream.of(u.getTitle() != null ? u.getTitle().getRole() : null),
                Stream.concat(
                        u.getAdditionalRoles() != null ? u.getAdditionalRoles().stream() : Stream.empty(),
                        u.getUnit() != null && u.getUnit().getRoles() != null ? u.getUnit().getRoles().stream() : Stream.empty()));
    }

    private static boolean holdsRole(User u, String name) {
        return roles(u).anyMatch(r -> r != null && name.equals(r.getName()));
    }

    private static boolean isSuperAdmin(User u) {
        return holdsRole(u, Constants.SUPER_ADMIN);
    }

    private void signInAs(User user) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(user));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}

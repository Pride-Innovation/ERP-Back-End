package pride.bank.erp.request;

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
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.assetType.AssetTypeRepository;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityRepository;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.config.RestockPermissionSeeder;
import pride.bank.erp.config.seed.SeederStateRepository;
import pride.bank.erp.exception.BusinessRuleException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.issuance.Issuance;
import pride.bank.erp.issuance.IssuanceRepository;
import pride.bank.erp.issuedAsset.IssuedAsset;
import pride.bank.erp.issuedAsset.IssuedAssetLifecycleService;
import pride.bank.erp.issuedAsset.IssuedAssetRepository;
import pride.bank.erp.movement.FulfillmentService;
import pride.bank.erp.movement.Movement;
import pride.bank.erp.movement.MovementRepository;
import pride.bank.erp.movement.MovementType;
import pride.bank.erp.requestCommodity.RequestCommodity;
import pride.bank.erp.requestCommodity.RequestCommodityRepository;
import pride.bank.erp.role.Role;
import pride.bank.erp.store.StoreService;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;
import pride.bank.erp.workflow.ApprovalStep;
import pride.bank.erp.workflow.ApprovalWorkflow;
import pride.bank.erp.workflow.ApprovalWorkflowRepository;
import pride.bank.erp.workflow.StepApproverType;
import pride.bank.erp.workflow.StepType;
import pride.bank.erp.workflow.WorkflowBranchScope;
import pride.bank.erp.workflow.WorkflowPurpose;
import pride.bank.erp.workflow.WorkflowResolveService;
import pride.bank.erp.workflowinstance.WorkflowEngineService;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 2: a request is personal or a store restock, chosen on the request (V30).
 *
 * <ul>
 *   <li>Only RESTOCK_STORE holders may ask, and only at a branch.</li>
 *   <li>Workflows say which kind they serve, so a branch workflow can serve personal requests while
 *       restocks go elsewhere - and a restock no workflow serves is refused, never sent down the
 *       legacy route.</li>
 *   <li>A restock is delivered into the branch Admin store, its assets landing as stock ready to
 *       issue and never assigned to the requester.</li>
 * </ul>
 * Live data, rolled back; each case says when the data offers nothing to exercise.
 */
@SpringBootTest
@Transactional
class StoreRestockRequestTest {

    @Autowired RequestService requestService;
    @Autowired RequestRepository requestRepository;
    @Autowired RequestCommodityRepository requestCommodityRepository;
    @Autowired ApprovalWorkflowRepository workflowRepository;
    @Autowired WorkflowResolveService resolveService;
    @Autowired WorkflowEngineService engine;
    @Autowired FulfillmentService fulfillmentService;
    @Autowired IssuedAssetLifecycleService issuedAssetLifecycleService;
    @Autowired IssuanceRepository issuanceRepository;
    @Autowired IssuedAssetRepository issuedAssetRepository;
    @Autowired MovementRepository movementRepository;
    @Autowired AssetRepository assetRepository;
    @Autowired AssetTypeRepository assetTypeRepository;
    @Autowired CommodityRepository commodityRepository;
    @Autowired StoreService storeService;
    @Autowired UserRepository userRepository;
    @Autowired SeederStateRepository seederStateRepository;
    @Autowired pride.bank.erp.permission.PermissionRepository permissionRepository;
    @Autowired pride.bank.erp.role.RoleRepository roleRepository;
    @Autowired JwtService jwtService;

    @AfterEach
    void signOut() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private Optional<User> branchUser(boolean holdingRestock) {
        return userRepository.findAll().stream()
                .filter(u -> u.getBranch() != null && !u.getBranch().isHeadOffice() && !isSuperAdmin(u))
                .filter(u -> holds(u, Constants.RESTOCK_STORE) == holdingRestock)
                .findFirst();
    }

    private Optional<User> headOfficeUser() {
        return userRepository.findAll().stream()
                .filter(u -> u.getBranch() != null && u.getBranch().isHeadOffice() && !isSuperAdmin(u))
                .findFirst();
    }

    private Request restockRequest(User requester, AssetType category) {
        return requestRepository.saveAndFlush(Request.builder()
                .name("Restock test").priority("Low").description("test")
                .requester(requester).assetType(category).forStoreRestock(true).deleted(false)
                .build());
    }

    @Test
    void theGrantRanOnce() {
        assertThat(seederStateRepository.existsById(RestockPermissionSeeder.MARKER))
                .as("BM and BOM were given RESTOCK_STORE once; who holds it now is Settings' decision")
                .isTrue();
    }

    /** One valid line, so the request passes its own validation and reaches the restock rule. */
    private List<pride.bank.erp.requestCommodity.RequestCommodityDTO> oneLine() {
        Commodity commodity = commodityRepository.findAll().stream().findFirst().orElseThrow();
        return List.of(pride.bank.erp.requestCommodity.RequestCommodityDTO.builder()
                .commodityId(commodity.getId()).quantity(1).build());
    }

    private Request asking() {
        return Request.builder().name("Restock test").priority("Low").description("test").forStoreRestock(true).build();
    }

    @Test
    void onlyARestockHolderAtABranchMayAsk() throws Exception {
        Optional<User> without = branchUser(false);
        if (without.isPresent()) {
            signInAs(without.get());
            assertThatThrownBy(() -> requestService.createRequest(asking(), null, oneLine(), null, null))
                    .isInstanceOf(AccessDeniedException.class).hasMessageContaining("Restock Store");
            signOut();
        } else {
            System.out.println("restock test: NO branch user without RESTOCK_STORE - refusal unexercised");
        }

        // Head Office cannot ask even holding the permission, granted to its role here for the test.
        Optional<User> ho = headOfficeUser();
        Role role = ho.map(u -> u.getTitle() != null ? u.getTitle().getRole() : null).orElse(null);
        if (role == null) {
            System.out.println("restock test: NO Head Office user with a titled role - Head Office refusal unexercised");
            return;
        }
        role.getPermissions().add(permissionRepository.findByName(Constants.RESTOCK_STORE).orElseThrow());
        roleRepository.saveAndFlush(role);
        User reloaded = userRepository.findById(ho.get().getId()).orElseThrow();
        signInAs(reloaded);
        assertThatThrownBy(() -> requestService.createRequest(asking(), null, oneLine(), null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Head Office");
    }

    @Test
    void aWorkflowServesOnlyTheKindItSaysItDoes() {
        Optional<User> requester = branchUser(true);
        AssetType category = assetTypeRepository.findAll().stream().findFirst().orElse(null);
        if (requester.isEmpty() || category == null) return;
        Long branchId = requester.get().getBranch().getId();

        // Specific to this branch and category, so they outrank every general workflow.
        ApprovalWorkflow personal = workflow("Test personal", branchId, category, WorkflowPurpose.PERSONAL);
        ApprovalWorkflow restock = workflow("Test restock", branchId, category, WorkflowPurpose.RESTOCK);

        assertThat(resolveService.resolve(category.getId(), branchId, requester.get().getId(), false).getWorkflowId())
                .isEqualTo(personal.getId());
        assertThat(resolveService.resolve(category.getId(), branchId, requester.get().getId(), true).getWorkflowId())
                .isEqualTo(restock.getId());
    }

    @Test
    void aRestockNoWorkflowServesIsRefusedNotSentDownTheLegacyRoute() {
        Optional<User> requester = branchUser(true);
        AssetType category = assetTypeRepository.findAll().stream().findFirst().orElse(null);
        if (requester.isEmpty() || category == null) return;
        // Every workflow serves personal requests only, inside the rolled-back transaction.
        for (ApprovalWorkflow w : workflowRepository.findAll()) {
            w.setAppliesTo(WorkflowPurpose.PERSONAL);
            workflowRepository.save(w);
        }
        workflowRepository.flush();

        Request request = restockRequest(requester.get(), category);
        assertThatThrownBy(() -> engine.startWorkflow(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("store-restock")
                .hasMessageContaining(category.getName());
    }

    /** Head Office issues a restock; the movement is a replenishment into the requester's branch store. */
    @Test
    void aRestockIsDeliveredIntoTheBranchStoreAndItsAssetsStayThere() throws Exception {
        Optional<User> requester = branchUser(true);
        Optional<User> issuer = headOfficeUser();
        Commodity commodity = commodityRepository.findAll().stream().findFirst().orElse(null);
        if (requester.isEmpty() || issuer.isEmpty() || commodity == null) return;
        signInAs(issuer.get());

        Request request = restockRequest(requester.get(), commodity.getAssetType());
        requestCommodityRepository.save(RequestCommodity.builder().request(request).commodity(commodity).quantity(2).build());
        Issuance issuance = issuanceRepository.save(Issuance.builder()
                .requester(requester.get()).issuer(issuer.get()).request(request).stockSettled(true).build());

        fulfillmentService.handleIssuanceApproval(request, issuance);

        List<Movement> made = movementRepository.findByRequest_Id(request.getId());
        assertThat(made).hasSize(1);
        Movement m = made.get(0);
        assertThat(m.getMovementType()).isEqualTo(MovementType.REPLENISHMENT);
        assertThat(m.getRecipientUser()).as("delivered to the store, not to a person").isNull();
        assertThat(m.getDestStore().getId()).isEqualTo(storeService.resolveAdminStore(requester.get().getBranch()).getId());
        assertThat(m.getRestoreStatusCode()).as("assets land ready to issue").isEqualTo("issuanceAvailable");

        // Acknowledging receipt leaves an issued asset in the store, unassigned.
        Asset asset = assetRepository.findAll().stream()
                .filter(a -> a.getAssignedTo() == null && !Boolean.TRUE.equals(a.getDeleted()))
                .findFirst().orElse(null);
        if (asset == null) return;
        issuedAssetRepository.save(IssuedAsset.builder().issuance(issuance).asset(asset).build());
        issuedAssetLifecycleService.markAssigned(request);
        // An id, not the User: a failing assertion prints its subject, and printing a User walks
        // into Token.toString and overflows the stack.
        Long holderId = Optional.ofNullable(assetRepository.findById(asset.getId()).orElseThrow().getAssignedTo())
                .map(User::getId).orElse(null);
        assertThat(holderId).as("a restocked asset is never handed to whoever asked for it").isNull();
    }

    /** A personal request still ends with the requester, as before. */
    @Test
    void aPersonalRequestStillEndsWithThePerson() throws Exception {
        Optional<User> requester = branchUser(true);
        Optional<User> issuer = headOfficeUser();
        Commodity commodity = commodityRepository.findAll().stream().findFirst().orElse(null);
        if (requester.isEmpty() || issuer.isEmpty() || commodity == null) return;
        signInAs(issuer.get());

        Request request = requestRepository.saveAndFlush(Request.builder()
                .name("Personal test").priority("Low").description("test")
                .requester(requester.get()).forStoreRestock(false).deleted(false).build());
        requestCommodityRepository.save(RequestCommodity.builder().request(request).commodity(commodity).quantity(1).build());
        Issuance issuance = issuanceRepository.save(Issuance.builder()
                .requester(requester.get()).issuer(issuer.get()).request(request).stockSettled(true).build());

        fulfillmentService.handleIssuanceApproval(request, issuance);

        Movement m = movementRepository.findByRequest_Id(request.getId()).get(0);
        assertThat(m.getMovementType()).isEqualTo(MovementType.ISSUANCE_FULFILLMENT);
        assertThat(m.getRecipientUser().getId()).isEqualTo(requester.get().getId());
        assertThat(m.getDestStore()).isNull();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private ApprovalWorkflow workflow(String name, Long branchId, AssetType category, WorkflowPurpose purpose) {
        ApprovalWorkflow w = ApprovalWorkflow.builder()
                .name(name).branchId(branchId).branchScope(WorkflowBranchScope.BRANCH)
                .assetTypes(new java.util.HashSet<>(Set.of(category))).appliesTo(purpose).priority(1).active(true)
                .build();
        w.getSteps().add(ApprovalStep.builder().workflow(w).stepOrder(1).stepName("Receipt")
                .stepType(StepType.ACKNOWLEDGE_RECEIPT).approverType(StepApproverType.REQUESTER).build());
        return workflowRepository.saveAndFlush(w);
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

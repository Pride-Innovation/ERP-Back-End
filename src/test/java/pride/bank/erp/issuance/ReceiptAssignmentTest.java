package pride.bank.erp.issuance;

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
import pride.bank.erp.acknowledgeIssuanceReceipt.AcknowledgeIssuanceReceiptDTO;
import pride.bank.erp.acknowledgeIssuanceReceipt.AcknowledgeIssuanceReceiptService;
import pride.bank.erp.acknowledgeRequestReceipt.AcknowledgeRequestReceiptDTO;
import pride.bank.erp.acknowledgeRequestReceipt.AcknowledgeRequestReceiptService;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.exception.BusinessRuleException;
import pride.bank.erp.issuedAsset.IssuedAsset;
import pride.bank.erp.issuedAsset.IssuedAssetRepository;
import pride.bank.erp.movement.Movement;
import pride.bank.erp.movement.MovementRepository;
import pride.bank.erp.movement.MovementStatus;
import pride.bank.erp.movement.MovementType;
import pride.bank.erp.notification.Email;
import pride.bank.erp.request.Request;
import pride.bank.erp.request.RequestRepository;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Issued assets become the requester's when the requester acknowledges receipt - and not before.
 *
 * <p>The hand-over used to sit on the Infra/Admin unit's acknowledgement of the <em>request</em>,
 * which happens before anything is issued, so it never had anything to hand over. Every Head Office
 * request (issued directly, no movement) finished with its assets "Issued", holder empty, store empty.
 * The delivery check sat there too, where no movement exists yet, so it never refused anything.
 *
 * <p>Real data in a rolled-back transaction. No workflow instance, so the services take their legacy
 * path, which sends mail - hence the mocked {@link Email}.
 */
@SpringBootTest
@Transactional
class ReceiptAssignmentTest {

    @Autowired AcknowledgeIssuanceReceiptService receiptService;
    @Autowired AcknowledgeRequestReceiptService unitAcknowledgementService;
    @Autowired IssuanceRepository issuanceRepository;
    @Autowired IssuedAssetRepository issuedAssetRepository;
    @Autowired AssetRepository assetRepository;
    @Autowired MovementRepository movementRepository;
    @Autowired RequestRepository requestRepository;
    @Autowired UserRepository userRepository;
    @Autowired StatusService statusService;
    @Autowired JwtService jwtService;
    @MockBean Email email;

    @AfterEach
    void signOut() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    /** A Head Office requester, a colleague who issues, and an asset put into the "Issued" state. */
    private record Case(User requester, User issuer, Request request, Asset asset) {}

    private Optional<Case> issuedToSomebody() throws Exception {
        List<User> ho = userRepository.findAll().stream()
                .filter(u -> u.getBranch() != null && u.getBranch().isHeadOffice() && u.isEnabled())
                .toList();
        Asset asset = assetRepository.findAll().stream()
                .filter(a -> !Boolean.TRUE.equals(a.getDeleted()) && !Boolean.TRUE.equals(a.getDisposed()) && !a.isTemporaryPool())
                .findFirst().orElse(null);
        if (ho.size() < 2 || asset == null) {
            System.out.println("receipt test: NO two Head Office users and an asset - unexercised");
            return Optional.empty();
        }
        User requester = ho.get(0);
        User issuer = ho.get(1);
        signInAs(issuer); // created_by on what follows

        // What issuing leaves behind: the asset Issued, off the shelf, held by nobody.
        asset.setAssetStatus(statusService.findByStatusCode("assetIssued"));
        asset.setAssignedTo(null);
        asset.setCurrentStore(null);
        assetRepository.saveAndFlush(asset);

        Request request = requestRepository.saveAndFlush(Request.builder()
                .name("Receipt test").priority("Low").description("test")
                .requester(requester).forStoreRestock(false).deleted(false).build());
        Issuance issuance = issuanceRepository.saveAndFlush(Issuance.builder()
                .request(request).requester(requester).issuer(issuer).stockSettled(true).build());
        issuedAssetRepository.saveAndFlush(IssuedAsset.builder().issuance(issuance).asset(asset).build());
        return Optional.of(new Case(requester, issuer, request, asset));
    }

    @Test
    void acknowledgingReceiptHandsTheIssuedAssetsToTheRequester() throws Exception {
        Optional<Case> found = issuedToSomebody();
        if (found.isEmpty()) return;
        Case c = found.get();

        signInAs(c.requester());
        receiptService.createAcknowledgeRequestIssuance(receipt(c.request()));

        Asset after = assetRepository.findById(c.asset().getId()).orElseThrow();
        // Ids and codes, not the User: printing a User in a failure message overflows the stack.
        assertThat(after.getAssignedTo() == null ? null : after.getAssignedTo().getId())
                .as("the asset is the requester's").isEqualTo(c.requester().getId());
        assertThat(after.getAssetStatus().getStatus()).isEqualTo("assetAssigned");
        assertThat(after.getCurrentStore()).as("held by a person, not a store").isNull();
    }

    @Test
    void theUnitsAcknowledgementOfTheRequestHandsNothingOver() throws Exception {
        Optional<Case> found = issuedToSomebody();
        if (found.isEmpty()) return;
        Case c = found.get();

        signInAs(c.issuer());
        AcknowledgeRequestReceiptDTO dto = new AcknowledgeRequestReceiptDTO();
        dto.setRequestId(c.request().getId());
        dto.setStatusId(statusService.findByStatusCode("unitAcknowledged").getId());
        unitAcknowledgementService.createAcknowledgeRequestReceipt(dto);

        Asset after = assetRepository.findById(c.asset().getId()).orElseThrow();
        assertThat(holderId(after)).as("only the requester's receipt assigns").isNull();
        assertThat(after.getAssetStatus().getStatus()).isEqualTo("assetIssued");
    }

    @Test
    void receiptIsRefusedWhileTheDeliveryIsStillOnTheRoad() throws Exception {
        Optional<Case> found = issuedToSomebody();
        if (found.isEmpty()) return;
        Case c = found.get();
        movementRepository.saveAndFlush(Movement.builder()
                .movementType(MovementType.ISSUANCE_FULFILLMENT).status(MovementStatus.IN_TRANSIT)
                .movementCategory(pride.bank.erp.movement.MovementCategory.INTER_LOCATION)
                .request(c.request()).recipientUser(c.requester()).initiator(c.issuer()).build());

        signInAs(c.requester());
        assertThatThrownBy(() -> receiptService.createAcknowledgeRequestIssuance(receipt(c.request())))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not been delivered");
        assertThat(holderId(assetRepository.findById(c.asset().getId()).orElseThrow())).isNull();
    }

    /** A branch delivery: the movement already handed it over and recorded it, but left it "Issued". */
    @Test
    void anAssetTheMovementAlreadyHandedOverOnlyHasItsStatusCorrected() throws Exception {
        Optional<Case> found = issuedToSomebody();
        if (found.isEmpty()) return;
        Case c = found.get();
        Asset asset = assetRepository.findById(c.asset().getId()).orElseThrow();
        asset.setAssignedTo(c.requester());
        assetRepository.saveAndFlush(asset);
        // As the movement would: the previous holding closes, the requester's opens.
        historyRepository.findAll().stream()
                .filter(h -> h.getAsset() != null && h.getAsset().getId().equals(asset.getId()) && h.getEndDate() == null)
                .forEach(h -> { h.setEndDate(java.time.LocalDateTime.now()); historyRepository.save(h); });
        historyRepository.saveAndFlush(pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistory.builder()
                .asset(asset).user(c.requester()).startDate(java.time.LocalDateTime.now()).build());
        long recordsBefore = historyFor(asset);

        signInAs(c.requester());
        receiptService.createAcknowledgeRequestIssuance(receipt(c.request()));

        Asset after = assetRepository.findById(asset.getId()).orElseThrow();
        assertThat(after.getAssetStatus().getStatus()).isEqualTo("assetAssigned");
        assertThat(historyFor(asset)).as("the movement's assignment record is kept, not duplicated").isEqualTo(recordsBefore);
    }

    @Test
    void anAssetThatHasMovedOnOrIsHeldBySomebodyElseIsLeftAlone() throws Exception {
        Optional<Case> found = issuedToSomebody();
        if (found.isEmpty()) return;
        Case c = found.get();
        Asset asset = assetRepository.findById(c.asset().getId()).orElseThrow();

        // Held by somebody else.
        asset.setAssignedTo(c.issuer());
        assetRepository.saveAndFlush(asset);
        signInAs(c.requester());
        receiptService.createAcknowledgeRequestIssuance(receipt(c.request()));
        Asset after = assetRepository.findById(asset.getId()).orElseThrow();
        assertThat(after.getAssignedTo().getId()).as("never taken from another holder").isEqualTo(c.issuer().getId());
        assertThat(after.getAssetStatus().getStatus()).isEqualTo("assetIssued");

        // Gone for repair since it was issued.
        after.setAssignedTo(null);
        after.setAssetStatus(statusService.findByStatusCode("inMaintenance"));
        assetRepository.saveAndFlush(after);
        issuedAssetLifecycle.markAssigned(requestRepository.findById(c.request().getId()).orElseThrow());
        Asset later = assetRepository.findById(asset.getId()).orElseThrow();
        assertThat(holderId(later)).as("not pulled back from the repair bench").isNull();
        assertThat(later.getAssetStatus().getStatus()).isEqualTo("inMaintenance");
    }

    /**
     * The repair of past receipts ran once. Pinned by its marker, not its effect: it commits per
     * request, outside any test's rollback, so a test must never run it, and what it changed is live
     * data that later work legitimately moves on.
     */
    @Test
    void thePastReceiptsWereRepairedOnce() {
        assertThat(seederStateRepository.existsById(pride.bank.erp.config.IssuedAssetHandOverRepairSeeder.MARKER)).isTrue();
    }

    @Autowired pride.bank.erp.config.seed.SeederStateRepository seederStateRepository;
    @Autowired pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistoryRepository historyRepository;
    @Autowired pride.bank.erp.issuedAsset.IssuedAssetLifecycleService issuedAssetLifecycle;

    /** An id, not the User: printing a User in a failure message overflows the stack. */
    private static Long holderId(Asset asset) {
        return asset.getAssignedTo() == null ? null : asset.getAssignedTo().getId();
    }

    private long historyFor(Asset asset) {
        return historyRepository.findAll().stream()
                .filter(h -> h.getAsset() != null && h.getAsset().getId().equals(asset.getId())).count();
    }

    private AcknowledgeIssuanceReceiptDTO receipt(Request request) throws Exception {
        AcknowledgeIssuanceReceiptDTO dto = new AcknowledgeIssuanceReceiptDTO();
        dto.setRequestId(request.getId());
        dto.setStatusId(statusService.findByStatusCode("receiptAcknowledged").getId());
        return dto;
    }

    private void signInAs(User user) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(user));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}

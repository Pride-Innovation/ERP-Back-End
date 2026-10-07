package pride.bank.erp.asset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pride.bank.erp.assetType.RepairDestination;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.config.seed.SeederStateRepository;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.issuance.IssuanceService;
import pride.bank.erp.issuance.IssueAssetsDTO;
import pride.bank.erp.notification.Email;
import pride.bank.erp.status.StatusRepository;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreRepository;
import pride.bank.erp.store.StoreType;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Receive into Store, and what a loaner is.
 *
 * <h2>What was wrong</h2>
 * <ul>
 *   <li>A loaner received with no store chosen went to the Admin store of wherever it stood - so an
 *       IT loaner landed in the Head Office Admin store, lendable but invisible from the IT store.</li>
 *   <li>Loaners were stamped "Available for Issuance", the status of stock awaiting issuance, so the
 *       register could not tell them apart and issuance - which checks only that status - would issue
 *       one permanently.</li>
 *   <li>A loaner could be booked into the Disposal store, flagged lendable and never offered.</li>
 *   <li>Reassigning an asset out of a store left it in the store and reading "Sent to Store" while a
 *       person held it.</li>
 * </ul>
 *
 * <p>Rolled back throughout. Runs as somebody with bank-wide asset reach, because the hand-back is
 * scoped and scope is not what these tests are about.
 */
@SpringBootTest
@Transactional
class StoreHandBackTest {

    @Autowired AssetService assetService;
    @Autowired AssetRepository assetRepository;
    @Autowired StoreRepository storeRepository;
    @Autowired StatusRepository statusRepository;
    @Autowired UserRepository userRepository;
    @Autowired IssuanceService issuanceService;
    @Autowired SeederStateRepository seederStateRepository;
    @Autowired JwtService jwtService;

    /** Reassigning notifies the person by email; nothing here should reach a real mailbox. */
    @MockBean Email email;

    @BeforeEach
    void actAsSomeoneWithBankWideReach() {
        User user = userRepository.findAll().stream()
                .filter(u -> u.getAuthorities().stream()
                        .anyMatch(a -> Constants.MANAGE_ALL_BRANCH_ASSETS.equals(a.getAuthority())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("nobody holds " + Constants.MANAGE_ALL_BRANCH_ASSETS));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(user));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearPrincipal() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ── Where a loaner goes ──────────────────────────────────────────────────

    /** No store chosen: an IT-category loaner goes to the IT store, where the IT store page shows it. */
    @Test
    void aLoanerWithNoStoreChosenGoesToItsCategorysLoanerStore() throws Exception {
        Asset asset = anAssetRoutedTo(RepairDestination.IT);

        assetService.sendAssetToStore(asset.getId(), null, true);

        Asset after = assetRepository.findById(asset.getId()).orElseThrow();
        assertThat(after.getCurrentStore().getStoreType())
                .as("it used to land in the Head Office Admin store")
                .isEqualTo(StoreType.IT);
        assertThat(after.isTemporaryPool()).isTrue();
        assertThat(after.getAssetStatus().getStatus()).isEqualTo("loanAvailable");
    }

    /** Switch off: parked in store, nothing more. */
    @Test
    void anOrdinaryHandBackIsSentToStore() throws Exception {
        Asset asset = anAssetRoutedTo(RepairDestination.IT);

        assetService.sendAssetToStore(asset.getId(), null, false);

        Asset after = assetRepository.findById(asset.getId()).orElseThrow();
        assertThat(after.isTemporaryPool()).isFalse();
        assertThat(after.getAssetStatus().getStatus()).isEqualTo("sentToStore");
        assertThat(after.getCurrentStore()).isNotNull();
    }

    /** The pool is read from IT and Admin stores only; a loaner anywhere else is never offered. */
    @Test
    void aLoanerCannotBeKeptInTheDisposalStore() {
        Asset asset = anAssetRoutedTo(RepairDestination.IT);
        Store disposal = storeRepository.findByStoreType(StoreType.DISPOSAL).get(0);

        assertThatThrownBy(() -> assetService.sendAssetToStore(asset.getId(), disposal.getId(), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IT or Admin store");
    }

    // ── A loaner is not stock for issuance ───────────────────────────────────

    /**
     * Refused even while it still carries the old status, because that is exactly the case the
     * status check alone let through - and the one pool assets from before this change are in.
     */
    @Test
    void issuanceRefusesALoanerWhateverItsStatus() {
        Asset asset = anAssetRoutedTo(RepairDestination.IT);
        asset.setTemporaryPool(true);
        asset.setAssetStatus(statusRepository.findByStatus("issuanceAvailable").orElseThrow());
        if (asset.getEngravedNumber() == null) asset.setEngravedNumber("TEST-LOANER-" + asset.getId());
        assetRepository.save(asset);

        IssueAssetsDTO line = new IssueAssetsDTO();
        line.setId(asset.getId());

        // Through the target, not the bean: IssuanceService is proxied, and the proxy's own copies
        // of its fields are null - the trap CLAUDE.md records against AssetCategoryFeatureSeeder.
        IssuanceService target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(issuanceService);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                target, "validateAssetsReadyForIssuance", List.of(line)))
                .hasMessageContaining("loaner");
    }

    // ── Reassign ─────────────────────────────────────────────────────────────

    @Test
    void aLoanerIsNotReassignedAsAnOrdinaryAssignment() throws Exception {
        Asset asset = anAssetRoutedTo(RepairDestination.IT);
        assetService.sendAssetToStore(asset.getId(), null, true);
        User someone = anEnabledUser();

        assertThatThrownBy(() -> assetService.reassignAsset(asset.getId(), new ReassignAsset(someone.getId())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loaner");
    }

    /** Out of the store and onto the person - it used to be in both places at once. */
    @Test
    void assigningAnAssetOutOfAStoreTakesItOutOfTheStore() throws Exception {
        Asset asset = anAssetRoutedTo(RepairDestination.IT);
        assetService.sendAssetToStore(asset.getId(), null, false);
        User someone = anEnabledUser();

        assetService.reassignAsset(asset.getId(), new ReassignAsset(someone.getId()));

        Asset after = assetRepository.findById(asset.getId()).orElseThrow();
        assertThat(after.getAssignedTo().getId()).isEqualTo(someone.getId());
        assertThat(after.getCurrentStore()).as("no longer on a shelf").isNull();
        assertThat(after.getAssetStatus().getStatus())
                .as("it used to keep reading Sent to Store")
                .isEqualTo("assetAssigned");
    }

    // ── The one-time relabel ─────────────────────────────────────────────────

    /** Pinned by its marker, never by its effect - a loaner set back by hand is the feature working. */
    @Test
    void theLoanerRelabelRanExactlyOnce() {
        assertThat(seederStateRepository.existsById("LOANERS_ON_LOAN_AVAILABLE_STATUS")).isTrue();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Asset anAssetRoutedTo(RepairDestination routing) {
        return assetRepository.findAll().stream()
                .filter(a -> !Boolean.TRUE.equals(a.getDeleted()) && !Boolean.TRUE.equals(a.getDisposed()))
                .filter(a -> a.getAssetType() != null && routing == a.getAssetType().getRepairDestination())
                .filter(a -> a.getBranch() != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no asset whose category routes to " + routing));
    }

    private User anEnabledUser() {
        return userRepository.findAll().stream().filter(User::isEnabled).findFirst().orElseThrow();
    }
}

/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.issuance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.asset.AssetService;
import pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistoryRepository;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.commodity.CommodityService;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.exception.BadRequestException;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.inventory.InventoryService;
import pride.bank.erp.issuedAsset.IssuedAssetRepository;
import pride.bank.erp.issuedCommodity.IssuedCommodityRepository;
import pride.bank.erp.notification.Email;
import pride.bank.erp.request.Request;
import pride.bank.erp.request.RequestRepository;
import pride.bank.erp.request.RequestService;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.store.StoreBalanceRepository;
import pride.bank.erp.store.StoreService;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;
import pride.bank.erp.user.UserService;
import pride.bank.erp.workflowinstance.WorkflowEngineService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The API-side guards on which assets may be handed out.
 *
 * <p>The frontend picker filters to "Available for Issuance" and rejects an asset picked twice
 * across lines, but neither is a guarantee the API can lean on — a stale client cache re-offered
 * issued assets for a whole browser session, which is what sent us looking here in the first place.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IssuanceAssetGuardTest {

    private static final Long BRANCH_ID = 7L;

    @Mock private UserService userService;
    @Mock private IssuanceRepository issuanceRepository;
    @Mock private StatusService statusService;
    @Mock private CommodityService commodityService;
    @Mock private IssuedCommodityRepository issuedCommodityRepository;
    @Mock private IssuedAssetRepository issuedAssetRepository;
    @Mock private StoreService storeService;
    @Mock private AssetService assetService;
    @Mock private AssetRepository assetRepository;
    @Mock private RequestService requestService;
    @Mock private RequestRepository requestRepository;
    @Mock private AssetAssignmentHistoryRepository assetAssignmentHistoryRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private Email emailService;
    @Mock private UserRepository userRepository;
    @Mock private UserBuilder userBuilder;
    @Mock private StoreBalanceRepository storeBalanceRepository;
    @Mock private WorkflowEngineService workflowEngineService;
    @Mock private InventoryService inventoryService;

    private IssuanceService issuanceService;

    @BeforeEach
    void setUp() throws NotFoundException {
        issuanceService = new IssuanceService(
                userService, issuanceRepository, statusService, commodityService,
                issuedCommodityRepository, issuedAssetRepository, storeService, assetService,
                assetRepository, requestService, requestRepository, assetAssignmentHistoryRepository,
                currentUserService, emailService, userRepository, userBuilder,
                storeBalanceRepository, workflowEngineService, inventoryService);

        Branch branch = Branch.builder().id(BRANCH_ID).build();
        User person = User.builder().id(1L).branch(branch).build();

        when(userService.findUser(anyLong())).thenReturn(person);
        when(currentUserService.getCurrentUser()).thenReturn(person);
        when(requestService.findRequest(anyLong())).thenReturn(Request.builder().id(5L).build());
        when(statusService.findByStatusCode(any())).thenAnswer(
                invocation -> Status.builder().id(1L).status(invocation.getArgument(0)).build());
    }

    private Asset asset(Long id, String statusCode) {
        return Asset.builder()
                .id(id)
                .assetName("Dell Monitor")
                .engravedNumber("PBL-" + id)
                .assetStatus(Status.builder().id(2L).status(statusCode).build())
                .build();
    }

    private IssuanceDTO issuanceOf(IssueAssetsDTO... assets) {
        return IssuanceDTO.builder()
                .requester(1L)
                .requestId(5L)
                .assets(List.of(assets))
                .build();
    }

    /**
     * The same asset listed twice on one issuance. Both entries read "Available for Issuance" —
     * nothing is stamped until applyAssetIssuance runs later — so a per-entry status check alone
     * lets them both through and writes two IssuedAsset rows for one physical asset.
     */
    @Test
    void anAssetListedTwiceOnOneIssuanceIsRejected() throws NotFoundException {
        when(assetService.findOne(42L)).thenReturn(asset(42L, "issuanceAvailable"));

        assertThatThrownBy(() -> issuanceService.issueCommodities(issuanceOf(
                new IssueAssetsDTO(42L, "PBL-42"),
                new IssueAssetsDTO(42L, "PBL-42"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("more than once")
                .hasMessageContaining("id=42");

        verify(issuedAssetRepository, never()).save(any());
    }

    /** An asset already issued to another request must not be handed out a second time. */
    @Test
    void anAlreadyIssuedAssetIsRejected() throws NotFoundException {
        when(assetService.findOne(42L)).thenReturn(asset(42L, "assetIssued"));

        assertThatThrownBy(() -> issuanceService.issueCommodities(
                issuanceOf(new IssueAssetsDTO(42L, "PBL-42"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already issued and awaiting delivery");

        verify(issuedAssetRepository, never()).save(any());
    }

    /** Distinct available assets on one issuance are fine — the de-dupe must not over-reach. */
    @Test
    void distinctAvailableAssetsPassTheGuard() throws NotFoundException {
        when(assetService.findOne(42L)).thenReturn(asset(42L, "issuanceAvailable"));
        when(assetService.findOne(43L)).thenReturn(asset(43L, "issuanceAvailable"));

        assertThat(catchGuard(issuanceOf(
                new IssueAssetsDTO(42L, "PBL-42"),
                new IssueAssetsDTO(43L, "PBL-43"))))
                .as("two different available assets should clear the pre-issuance guard")
                .isNull();
    }

    /**
     * Runs an issuance far enough to exercise the guard and returns the guard's complaint, or null
     * if it passed. Anything after the guard needs the full store/workflow graph, which this test
     * deliberately does not stand up — so only a BadRequestException is meaningful here.
     */
    private String catchGuard(IssuanceDTO dto) {
        try {
            issuanceService.issueCommodities(dto);
            return null;
        } catch (BadRequestException rejected) {
            return rejected.getMessage();
        } catch (Exception everythingElse) {
            return null;
        }
    }
}

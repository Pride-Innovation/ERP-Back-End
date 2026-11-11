/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.issuedAsset;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistory;
import pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistoryRepository;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.issuance.Issuance;
import pride.bank.erp.issuance.IssuanceRepository;
import pride.bank.erp.request.Request;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.user.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Drives the status transitions of assets that have been issued against a request, keeping the
 * "already issued / in transit" state out of the issuable pool (see {@code IssuanceService}).
 *
 * <p>Lives in its own service — rather than on {@code IssuanceService} — so the request-rejection
 * path ({@code WorkflowEngineService}) and the movement-cancellation path ({@code MovementService})
 * can drive reversals without creating a circular bean dependency
 * ({@code IssuanceService → WorkflowEngineService} already exists). Its own dependencies are all
 * repositories / leaf services, so nothing depends back on it.
 *
 * <p>Lifecycle: {@code Available for Issuance → Issued} (stamped at issuance by IssuanceService)
 * {@code → Assigned} (here, on acknowledgement of receipt). Reversals return an asset still in the
 * {@code Issued} / {@code In Transit} limbo to {@code Available for Issuance}; assets that have moved
 * on (assigned, in maintenance, disposed) are never touched.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IssuedAssetLifecycleService {

    private static final String CODE_ASSET_ISSUED = "assetIssued";
    private static final String CODE_ASSET_ASSIGNED = "assetAssigned";
    private static final String CODE_ISSUANCE_AVAILABLE = "issuanceAvailable";
    private static final String CODE_IN_TRANSIT = "inTransit";

    private final IssuanceRepository issuanceRepository;
    private final IssuedAssetRepository issuedAssetRepository;
    private final AssetRepository assetRepository;
    private final StatusService statusService;
    private final AssetAssignmentHistoryRepository assignmentHistoryRepository;
    private final pride.bank.erp.movement.MovementRepository movementRepository;

    /**
     * Whether the items have actually reached the requester. A direct issuance (no movement) has. One
     * delivered by movement has once the leg handing the items over ({@code recipientUser} set, else
     * the last leg) is {@code COMPLETED}. The requester's receipt is refused before then, and the
     * repair of past receipts skips such a request - its assets are with the courier, and completing
     * the movement is what hands them over.
     */
    public boolean deliveryComplete(Request request) {
        List<pride.bank.erp.movement.Movement> movements = movementRepository.findByRequest_Id(request.getId());
        if (movements.isEmpty()) {
            return true;
        }
        pride.bank.erp.movement.Movement deliveryLeg = movements.stream()
                .filter(m -> m.getRecipientUser() != null)
                .findFirst()
                .orElse(movements.get(movements.size() - 1));
        return deliveryLeg.getStatus() == pride.bank.erp.movement.MovementStatus.COMPLETED;
    }

    /** The serialized assets issued under a request (empty when the request had none / no issuance). */
    private List<Asset> issuedAssetsFor(Long requestId) {
        Issuance issuance = issuanceRepository.findByRequestId(requestId).orElse(null);
        if (issuance == null) {
            return List.of();
        }
        return issuedAssetRepository.findByIssuance_Id(issuance.getId()).stream()
                .map(IssuedAsset::getAsset)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * On acknowledgement of receipt: each issued asset becomes the requester's and reads "Assigned".
     *
     * <p>Finishes what issuing started and nothing else, so it is safe to run more than once:
     * <ul>
     *   <li><b>Only assets still in the issuing limbo</b> ({@code Issued} / {@code In Transit}) - the
     *       same states {@link #restoreToAvailable} reverses. An asset that has since gone for repair,
     *       back to a store or to disposal is not pulled back to the requester.</li>
     *   <li><b>Never from somebody else.</b> An asset another person now holds is left alone.</li>
     *   <li><b>Delivered by movement, already the requester's:</b> completing the movement handed it
     *       over and wrote the assignment, but left it reading "Issued". Only the status is corrected;
     *       the assignment record the movement wrote is kept rather than closed and duplicated.</li>
     *   <li><b>Issued directly</b> (Head Office, or a branch from its own store - no movement): held by
     *       nobody until now, so it is assigned here and the assignment recorded.</li>
     * </ul>
     */
    @Transactional
    public void markAssigned(Request request) throws NotFoundException {
        if (request == null || request.getRequester() == null) {
            return;
        }
        // A store restock's assets belong to the branch store, not to whoever asked for them. Its
        // movement put them on the shelf as stock ready to issue; acknowledging receipt confirms the
        // delivery and must not hand them to the requester.
        if (request.isForStoreRestock()) {
            log.info("Request {} restocked its branch store; issued assets stay in the store", request.getId());
            return;
        }
        User requester = request.getRequester();
        Status assigned = statusService.findByStatusCode(CODE_ASSET_ASSIGNED);

        int handedOver = 0;
        for (Asset asset : issuedAssetsFor(request.getId())) {
            String code = asset.getAssetStatus() != null ? asset.getAssetStatus().getStatus() : null;
            if (!CODE_ASSET_ISSUED.equals(code) && !CODE_IN_TRANSIT.equals(code)) {
                continue; // assigned already, or moved on since it was issued
            }
            User holder = asset.getAssignedTo();
            if (holder != null && !holder.getId().equals(requester.getId())) {
                log.warn("Request {}: asset {} is held by user {}, not the requester; left alone",
                        request.getId(), asset.getId(), holder.getId());
                continue;
            }

            Status before = asset.getAssetStatus();
            AssetAssignmentHistory active = assignmentHistoryRepository.findActiveByAssetId(asset.getId());
            boolean alreadyRecorded = holder != null && active != null && active.getUser() != null
                    && active.getUser().getId().equals(requester.getId());

            asset.setAssignedTo(requester);
            asset.setCurrentStore(null); // now held by a user, not a store
            if (requester.getBranch() != null) {
                asset.setBranch(requester.getBranch());
            }
            asset.setAssetStatus(assigned);
            assetRepository.save(asset);

            if (!alreadyRecorded) {
                closeActiveAssignment(asset);
                assignmentHistoryRepository.save(AssetAssignmentHistory.builder()
                        .asset(asset)
                        .user(requester)
                        .startDate(LocalDateTime.now())
                        .statusBefore(before)
                        .statusAfter(assigned)
                        .build());
            }
            handedOver++;
        }
        log.info("Request {}: {} issued asset(s) now assigned to the requester", request.getId(), handedOver);
    }

    /**
     * On issuance rejection or fulfilment-movement cancellation: return any of the request's assets
     * still sitting in the "Issued" / "In Transit" limbo to "Available for Issuance". No-op for
     * requests with no issued assets, and never disturbs assets that have already been assigned or
     * moved to maintenance / disposal.
     */
    @Transactional
    public void restoreToAvailable(Request request) throws NotFoundException {
        if (request == null) {
            return;
        }
        Status available = statusService.findByStatusCode(CODE_ISSUANCE_AVAILABLE);
        int restored = 0;
        for (Asset asset : issuedAssetsFor(request.getId())) {
            String code = asset.getAssetStatus() != null ? asset.getAssetStatus().getStatus() : null;
            if (CODE_ASSET_ISSUED.equals(code) || CODE_IN_TRANSIT.equals(code)) {
                asset.setAssetStatus(available);
                assetRepository.save(asset);
                restored++;
            }
        }
        if (restored > 0) {
            log.info("Returned {} issued asset(s) to the pool for request {}", restored, request.getId());
        }
    }

    private void closeActiveAssignment(Asset asset) {
        AssetAssignmentHistory active = assignmentHistoryRepository.findActiveByAssetId(asset.getId());
        if (active != null) {
            active.setEndDate(LocalDateTime.now());
            assignmentHistoryRepository.save(active);
        }
    }
}

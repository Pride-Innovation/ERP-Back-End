package pride.bank.erp.movement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.issuance.Issuance;
import pride.bank.erp.issuedAsset.IssuedAsset;
import pride.bank.erp.issuedAsset.IssuedAssetRepository;
import pride.bank.erp.request.Request;
import pride.bank.erp.requestCommodity.RequestCommodity;
import pride.bank.erp.requestCommodity.RequestCommodityRepository;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreService;
import pride.bank.erp.user.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides how an approved request is fulfilled after issuance approval (CLAUDE.md §6) and records the
 * inter-location transfer when one is needed.
 *
 * <ul>
 *   <li>Source and destination in the <b>same location</b> → {@link FulfillmentType#DIRECT_ISSUANCE};
 *       nothing extra is created (today's issuance already handled it).</li>
 *   <li><b>Different locations</b> → {@link FulfillmentType#STORE_MOVEMENT}; an INITIATED
 *       {@link Movement} is created from the source's ADMIN store to the destination's ADMIN store,
 *       linked to the request, for tracking / logistics.</li>
 * </ul>
 *
 * <p><b>Phase 3 scope:</b> the movement is a <i>record</i> only — its inventory ledger is intentionally
 * not applied here, because the legacy issuance path still moves stock eagerly. Phase 4 makes issuance
 * ledger-driven so the movement becomes the single source of truth for the cross-location transfer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FulfillmentService {

    private final MovementService movementService;
    private final MovementRepository movementRepository;
    private final StoreService storeService;
    private final RequestCommodityRepository requestCommodityRepository;
    private final IssuedAssetRepository issuedAssetRepository;

    /**
     * Evaluates the fulfilment route for {@code request} given the {@code issuance} that produced it,
     * creating a cross-location movement when required. Idempotent: a request that already has a
     * fulfilment movement is left untouched. Never throws on the movement-creation path — a failure
     * is logged so it cannot abort the surrounding issuance-approval transaction.
     */
    @Transactional
    public FulfillmentType handleIssuanceApproval(Request request, Issuance issuance) {
        Branch source = (issuance != null && issuance.getIssuer() != null)
                ? issuance.getIssuer().getBranch() : null;
        Branch destination = (request.getRequester() != null)
                ? request.getRequester().getBranch() : null;

        FulfillmentType decision = decide(source, destination);
        if (decision == FulfillmentType.DIRECT_ISSUANCE) {
            return decision;
        }

        // STORE_MOVEMENT: create the inter-location tracking record (once).
        try {
            if (!movementRepository.findByRequest_Id(request.getId()).isEmpty()) {
                log.info("Fulfilment movement already exists for request {} — skipping", request.getId());
                return decision;
            }
            createFulfillmentMovement(request, issuance, source, destination);
        } catch (Exception e) {
            log.warn("Could not create fulfilment movement for request {}: {}", request.getId(), e.getMessage());
        }
        return decision;
    }

    /** Same/unknown location → direct issuance; different locations → store movement. */
    public FulfillmentType decide(Branch source, Branch destination) {
        if (source == null || destination == null
                || source.getId() == null || destination.getId() == null
                || source.getId().equals(destination.getId())) {
            return FulfillmentType.DIRECT_ISSUANCE;
        }
        return FulfillmentType.STORE_MOVEMENT;
    }

    private void createFulfillmentMovement(Request request, Issuance issuance, Branch source, Branch destination)
            throws NotFoundException {
        List<RequestCommodity> commodities = requestCommodityRepository.findByRequestId(request.getId());
        List<IssuedAsset> issuedAssets = (issuance != null)
                ? issuedAssetRepository.findByIssuance_Id(issuance.getId())
                : List.of();
        if ((commodities == null || commodities.isEmpty()) && issuedAssets.isEmpty()) {
            log.info("Request {} has no consumable or asset lines — no fulfilment movement created", request.getId());
            return;
        }

        Store sourceStore = storeService.resolveAdminStore(source);
        Store destStore = storeService.resolveAdminStore(destination);

        // An asset-tracked request line appears twice — once as a RequestCommodity (the category,
        // e.g. "CPU") and once as the specific serialized asset issued for it (e.g. PBL-2027-CPU06).
        // Carry the physical item once: as its serialized asset. Count issued assets per commodity
        // so the redundant commodity line is dropped (or reduced, for a partly-consumable line),
        // which also avoids a phantom consumable debit on receipt for items with no store balance.
        Map<Long, Integer> assetsByCommodity = new HashMap<>();
        for (IssuedAsset ia : issuedAssets) {
            Asset a = ia.getAsset();
            if (a == null || a.getCommodity() == null || a.getCommodity().getId() == null) continue;
            assetsByCommodity.merge(a.getCommodity().getId(), 1, Integer::sum);
        }

        List<MovementItemDTO> items = new ArrayList<>();
        // Consumable lines — only the quantity NOT already covered by an issued serialized asset.
        if (commodities != null) {
            for (RequestCommodity rc : commodities) {
                if (rc.getCommodity() == null || rc.getQuantity() <= 0) continue;
                int covered = assetsByCommodity.getOrDefault(rc.getCommodity().getId(), 0);
                int remaining = rc.getQuantity() - covered;
                if (remaining > 0) {
                    items.add(MovementItemDTO.builder()
                            .commodityId(rc.getCommodity().getId())
                            .quantity(remaining)
                            .build());
                }
            }
        }
        // Serialized assets — one line each.
        for (IssuedAsset ia : issuedAssets) {
            if (ia.getAsset() == null) continue;
            items.add(MovementItemDTO.builder()
                    .assetId(ia.getAsset().getId())
                    .quantity(1)
                    .build());
        }
        if (items.isEmpty()) return;

        MovementType type = isReplenishment(request.getRequester())
                ? MovementType.REPLENISHMENT
                : MovementType.ISSUANCE_FULFILLMENT;

        // Destination is the requester, not the branch store.
        //
        // This movement used to end at the destination's Admin store, which meant it could only ever
        // describe half the journey; a second movement was then created automatically once it was
        // received, to carry the goods the last step from that store to the person who asked for
        // them. One request, two rows, the second appearing on its own.
        //
        // The custody stop has not been lost — it belongs to the Consignment this movement travels
        // on. Arrival credits the branch store, and this movement completes when the requester
        // signs, drawing its items back out of it. A replenishment, whose requester is the branch's
        // own Operations Manager, still ends with a person for the same reason: somebody signs for
        // the stock.
        MovementDTO dto = MovementDTO.builder()
                .movementType(type)
                .sourceStoreId(sourceStore.getId())
                .recipientUserId(request.getRequester().getId())
                .requestId(request.getId())
                .remarks("Auto-created at issuance approval for cross-location fulfilment")
                .items(items)
                .build();

        Movement movement = movementService.createMovement(dto);
        log.info("Created fulfilment movement {} for request {} ({} → {} for {})",
                movement.getId(), request.getId(), source.getName(), destination.getName(),
                request.getRequester().getId());
    }

    /** A request raised by a branch's BOM is a stock replenishment rather than a personal issuance. */
    private boolean isReplenishment(User requester) {
        return requester != null
                && requester.getBranch() != null
                && requester.getBranch().getBranchOperationsManager() != null
                && requester.getId() != null
                && requester.getId().equals(requester.getBranch().getBranchOperationsManager().getId());
    }
}

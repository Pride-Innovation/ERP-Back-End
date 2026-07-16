package pride.bank.erp.inventory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistory;
import pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistoryRepository;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.movement.Movement;
import pride.bank.erp.movement.MovementItem;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreBalance;
import pride.bank.erp.store.StoreBalanceRepository;
import pride.bank.erp.user.User;

import java.time.LocalDateTime;

/**
 * The single authority over inventory balances (CLAUDE.md §22). All quantity changes — store↔store
 * transfers, store→user issuance, asset assignment/return — flow through here so balances stay
 * consistent and auditable. Effects are applied when a movement <i>completes</i> (i.e. on receipt),
 * never at initiation, so in-transit stock is not double-counted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final StoreBalanceRepository storeBalanceRepository;
    private final AssetRepository assetRepository;
    private final AssetAssignmentHistoryRepository assignmentHistoryRepository;

    // ── Consumable balances ─────────────────────────────────────────────────

    /** Increases (creating if needed) the balance of {@code commodity} in {@code store}. */
    @Transactional
    public void creditConsumable(Store store, Commodity commodity, int quantity) {
        if (quantity <= 0) return;
        StoreBalance balance = storeBalanceRepository
                .findByStore_IdAndCommodity_Id(store.getId(), commodity.getId())
                .orElseGet(() -> StoreBalance.builder()
                        .store(store)
                        .branch(store.getLocation())
                        .commodity(commodity)
                        .quantity(0)
                        .build());
        balance.setQuantity(balance.getQuantity() + quantity);
        storeBalanceRepository.save(balance);
    }

    /** Decreases the balance of {@code commodity} in {@code store}, validating availability. */
    @Transactional
    public void debitConsumable(Store store, Commodity commodity, int quantity) {
        if (quantity <= 0) return;
        StoreBalance balance = storeBalanceRepository
                .findByStore_IdAndCommodity_Id(store.getId(), commodity.getId())
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Commodity '%s' is not held in store '%s'", commodity.getName(), store.getName())));
        if (balance.getQuantity() < quantity) {
            throw new IllegalArgumentException(String.format(
                    "Insufficient stock of '%s' in store '%s'. Available: %d, requested: %d",
                    commodity.getName(), store.getName(), balance.getQuantity(), quantity));
        }
        balance.setQuantity(balance.getQuantity() - quantity);
        storeBalanceRepository.save(balance);
    }

    // ── Serialized assets ───────────────────────────────────────────────────

    /**
     * Moves an asset into a store (e.g. an Admin store, the IT store under repair, or the Disposal
     * store), updating both its location and the store that holds it. Clears any user assignment side
     * via {@link #returnAssetToStore} only when explicitly returning; this method just relocates.
     */
    @Transactional
    public void relocateAsset(Asset asset, Store destStore) {
        if (destStore != null) {
            asset.setCurrentStore(destStore);
            asset.setBranch(destStore.getLocation());
        }
        assetRepository.save(asset);
    }

    /** Assigns an asset to a user, closing any prior open assignment and opening a new one (§8). */
    @Transactional
    public void assignAssetToUser(Asset asset, User user) {
        assignAssetToUser(asset, user, false);
    }

    /** Assigns an asset to a user; {@code temporary} marks a repair-time replacement (§15). */
    @Transactional
    public void assignAssetToUser(Asset asset, User user, boolean temporary) {
        closeActiveAssignment(asset);
        asset.setAssignedTo(user);
        asset.setCurrentStore(null); // held by a user, not a store
        if (user.getBranch() != null) {
            asset.setBranch(user.getBranch());
        }
        assetRepository.save(asset);

        assignmentHistoryRepository.save(AssetAssignmentHistory.builder()
                .asset(asset)
                .user(user)
                .startDate(LocalDateTime.now())
                .temporary(temporary)
                .statusBefore(asset.getAssetStatus())
                .statusAfter(asset.getAssetStatus())
                .build());
    }

    /** Returns an asset to a store: closes its active assignment, clears the holder, sets the store. */
    @Transactional
    public void returnAssetToStore(Asset asset, Store destStore) {
        closeActiveAssignment(asset);
        asset.setAssignedTo(null);
        if (destStore != null) {
            asset.setCurrentStore(destStore);
            asset.setBranch(destStore.getLocation());
        }
        assetRepository.save(asset);
    }

    private void closeActiveAssignment(Asset asset) {
        AssetAssignmentHistory active = assignmentHistoryRepository.findActiveByAssetId(asset.getId());
        if (active != null) {
            active.setEndDate(LocalDateTime.now());
            assignmentHistoryRepository.save(active);
        }
    }

    // ── Movement application ────────────────────────────────────────────────

    /**
     * Applies the inventory effects of a completed {@link Movement}, debiting {@code from} (the
     * movement's own source store, or — when a courier carried it — the courier's custody store)
     * and crediting the destination (a store, or a user assignment / consumption). Idempotency is
     * the caller's responsibility — invoke once, when the movement transitions to COMPLETED.
     */
    @Transactional
    public void applyMovementCompletion(Movement movement, Store from) {
        Store dest = movement.getDestStore();
        User recipient = movement.getRecipientUser();

        for (MovementItem item : movement.getItems()) {
            if (item.getAsset() != null) {
                applyAssetEffect(movement, item.getAsset(), dest, recipient);
            } else if (item.getCommodity() != null) {
                debitConsumable(from, item.getCommodity(), item.getQuantity());
                if (dest != null) {
                    creditConsumable(dest, item.getCommodity(), item.getQuantity());
                }
                // No dest store (issued/consumed by a user) → debit only.
            }
        }
        log.info("Applied inventory effects for movement {} ({} item(s))",
                movement.getId(), movement.getItems().size());
    }

    /**
     * The first ("custody handoff") leg of an inter-location movement's ledger: relocates every
     * item from its source store into the assigned courier's custody store. Purely a location
     * change — no assignment / disposal / temporary-assignment semantics are applied here, since
     * the courier is never the final recipient. Idempotency is the caller's responsibility — invoke
     * once, when the movement transitions to {@code IN_TRANSIT}.
     */
    @Transactional
    public void applyCustodyHandoff(Movement movement, Store courierStore) {
        Store source = movement.getSourceStore();
        for (MovementItem item : movement.getItems()) {
            if (item.getAsset() != null) {
                relocateAsset(item.getAsset(), courierStore);
            } else if (item.getCommodity() != null) {
                debitConsumable(source, item.getCommodity(), item.getQuantity());
                creditConsumable(courierStore, item.getCommodity(), item.getQuantity());
            }
        }
        log.info("Applied custody handoff for movement {} to courier store {} ({} item(s))",
                movement.getId(), courierStore.getId(), movement.getItems().size());
    }

    /** Asset-side effect of a completed movement, keyed by its type (§14-17). */
    private void applyAssetEffect(Movement movement, Asset asset, Store dest, User recipient) {
        switch (movement.getMovementType()) {
            case DISPOSAL_TRANSFER -> {
                relocateAsset(asset, dest);            // into the Disposal store
                asset.setDisposed(true);
                asset.setDisposalDate(LocalDateTime.now());
                assetRepository.save(asset);
            }
            case TEMP_REPLACEMENT -> {
                if (recipient != null) {
                    assignAssetToUser(asset, recipient, true); // temporary assignment
                } else if (dest != null) {
                    relocateAsset(asset, dest);
                }
            }
            // returnAssetToStore (not relocateAsset) so the user's active assignment is actually
            // closed — otherwise the asset keeps showing "assigned to" them while it sits in the
            // IT/Admin store under repair.
            case REPAIR_TRANSFER -> returnAssetToStore(asset, dest);
            case RETURN_AFTER_REPAIR -> {
                if (recipient != null) {
                    assignAssetToUser(asset, recipient); // back to its user
                } else if (dest != null) {
                    // The temp-replacement leg being returned: close its temporary assignment so
                    // it's actually available in the pool again, not just relocated while still
                    // showing as assigned to the borrower.
                    returnAssetToStore(asset, dest);
                }
            }
            default -> {                                 // ISSUANCE_FULFILLMENT / DEPARTMENT_TRANSFER
                if (recipient != null) {
                    assignAssetToUser(asset, recipient);
                } else if (dest != null) {
                    relocateAsset(asset, dest);
                }
            }
        }
    }
}

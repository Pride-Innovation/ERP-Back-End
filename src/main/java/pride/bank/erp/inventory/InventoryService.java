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
import pride.bank.erp.inventory.ledger.LedgerContext;
import pride.bank.erp.inventory.ledger.StockLedgerReason;
import pride.bank.erp.inventory.ledger.StockLedgerService;
import pride.bank.erp.movement.Movement;
import pride.bank.erp.movement.MovementItem;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
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
    private final StatusService statusService;
    private final StockLedgerService ledger;

    /** Asset status stamped while the courier holds custody (§9). */
    private static final String CODE_IN_TRANSIT = "inTransit";

    // ── Consumable balances ─────────────────────────────────────────────────

    /**
     * Increases (creating if needed) the balance of {@code commodity} in {@code store}.
     *
     * @deprecated every balance change should say why it happened. Use
     * {@link #creditConsumable(Store, Commodity, int, LedgerContext)}; this overload records an
     * {@link StockLedgerReason#UNSPECIFIED} entry so the change is at least visible.
     */
    @Deprecated
    @Transactional
    public void creditConsumable(Store store, Commodity commodity, int quantity) {
        creditConsumable(store, commodity, quantity, LedgerContext.unspecified());
    }

    /** Increases the balance of {@code commodity} in {@code store}, recording why. */
    @Transactional
    public void creditConsumable(Store store, Commodity commodity, int quantity, LedgerContext context) {
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
        ledger.record(store, commodity, quantity, balance.getQuantity(), context);
    }

    /**
     * Decreases the balance of {@code commodity} in {@code store}, validating availability.
     *
     * @deprecated see {@link #creditConsumable(Store, Commodity, int)}.
     */
    @Deprecated
    @Transactional
    public void debitConsumable(Store store, Commodity commodity, int quantity) {
        debitConsumable(store, commodity, quantity, LedgerContext.unspecified());
    }

    /** Decreases the balance of {@code commodity} in {@code store}, recording why. */
    @Transactional
    public void debitConsumable(Store store, Commodity commodity, int quantity, LedgerContext context) {
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
        ledger.record(store, commodity, -quantity, balance.getQuantity(), context);
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
            // A courier store has no location, so an asset in transit keeps the branch it left.
            // Overwriting it here made every in-transit asset report as Head Office stock, which is
            // where courier stores used to be pinned. `currentStore` and the In Transit status
            // already say it is on the road.
            if (destStore.getLocation() != null) {
                asset.setBranch(destStore.getLocation());
            }
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
                restorePriorStatus(item);
            } else if (item.getCommodity() != null) {
                // A return has no source store to debit: the quantity already left the store when
                // it was issued to the person handing it back. Crediting only is what makes the
                // store balance go back up when a user returns something.
                if (from != null) {
                    debitConsumable(from, item.getCommodity(), item.getQuantity(),
                            LedgerContext.of(StockLedgerReason.CUSTODY_IN, "MOVEMENT", movement.getId()));
                }
                if (dest != null) {
                    creditConsumable(dest, item.getCommodity(), item.getQuantity(),
                            LedgerContext.of(StockLedgerReason.TRANSFER_IN, "MOVEMENT", movement.getId()));
                }
                // No dest store (issued/consumed by a user) → debit only.
            }
        }
        log.info("Applied inventory effects for movement {} ({} item(s))",
                movement.getId(), movement.getItems().size());
    }

    /**
     * Undoes the "In Transit" stamp on receipt: puts back the status the asset carried before the
     * courier took custody (snapshotted on the movement line). Skips when there is no snapshot
     * (no courier leg) or the status has already been changed to something other than In Transit
     * by the completion effect itself.
     */
    private void restorePriorStatus(MovementItem item) {
        if (item.getPriorStatusCode() == null) return;
        Asset asset = item.getAsset();
        boolean stillInTransit = asset.getAssetStatus() != null
                && CODE_IN_TRANSIT.equals(asset.getAssetStatus().getStatus());
        if (stillInTransit) {
            Status prior = findStatusOrNull(item.getPriorStatusCode());
            if (prior != null) {
                asset.setAssetStatus(prior);
                assetRepository.save(asset);
            }
        }
        item.setPriorStatusCode(null);
    }

    /** Status lookup that degrades to a warning instead of failing the surrounding transaction. */
    private Status findStatusOrNull(String code) {
        try {
            return statusService.findByStatusCode(code);
        } catch (Exception e) {
            log.warn("Status '{}' not found; asset transit status will not be updated", code);
            return null;
        }
    }

    /**
     * The first ("custody handoff") leg of an inter-location movement's ledger: relocates every
     * item from its source store into the assigned courier's custody store. Purely a location
     * change — no assignment / disposal / temporary-assignment semantics are applied here, since
     * the courier is never the final recipient. Each asset is also stamped "In Transit" (its prior
     * status is snapshotted on the movement line and restored at receive). Idempotency is the
     * caller's responsibility — invoke once, when the movement transitions to {@code IN_TRANSIT}.
     */
    @Transactional
    public void applyCustodyHandoff(Movement movement, Store courierStore) {
        Store source = movement.getSourceStore();
        Status inTransit = findStatusOrNull(CODE_IN_TRANSIT);
        for (MovementItem item : movement.getItems()) {
            if (item.getAsset() != null) {
                Asset asset = item.getAsset();
                relocateAsset(asset, courierStore);
                if (inTransit != null) {
                    item.setPriorStatusCode(asset.getAssetStatus() != null ? asset.getAssetStatus().getStatus() : null);
                    asset.setAssetStatus(inTransit);
                    assetRepository.save(asset);
                }
            } else if (item.getCommodity() != null) {
                debitConsumable(source, item.getCommodity(), item.getQuantity(),
                        LedgerContext.of(StockLedgerReason.TRANSFER_OUT, "MOVEMENT", movement.getId()));
                creditConsumable(courierStore, item.getCommodity(), item.getQuantity(),
                        LedgerContext.of(StockLedgerReason.CUSTODY_OUT, "MOVEMENT", movement.getId()));
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
            // A user handing an asset back: it stops being theirs and belongs to the store again.
            case RETURN_TO_STORE -> returnAssetToStore(asset, dest);
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
            default -> {                                 // ISSUANCE_FULFILLMENT / DEPARTMENT_TRANSFER / REPLENISHMENT
                if (recipient != null) {
                    assignAssetToUser(asset, recipient);
                } else if (dest != null) {
                    // returnAssetToStore, not relocateAsset: an asset that lands in a store is no
                    // longer held by anyone, so its assignment has to be closed. relocateAsset only
                    // moved it, leaving the previous holder still recorded — the asset then read as
                    // both "in the Admin store" and "assigned to Jane" at the same time.
                    returnAssetToStore(asset, dest);
                }
            }
        }
    }
}

package pride.bank.erp.movement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.store.StoreType;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Whether a loaner may be lent right now - one rule for the loaner list both repair modals read
 * ({@code GET /inventory/assets/temporary-pool}) and for the server that accepts the loan
 * ({@code RepairMovementService#issueTemporaryReplacement}).
 *
 * <h2>Why a loaner on an undispatched movement is already taken</h2>
 * Creating a temporary replacement only <em>names</em> the loaner on a movement; nothing on the
 * asset changes until that movement is dispatched and received - it still reads "Available for
 * Loan", unassigned, in its store. So the list offered the same chair to the next temporary
 * replacement, and the server accepted it: two movements promising one chair to two people. A
 * loaner is therefore committed from the moment it is on any movement not yet completed or
 * cancelled. Cancelling that movement frees it again; completing it hands it to the holder, which
 * the "unassigned" rule already excludes.
 *
 * <h2>And a loaner must be fit to lend</h2>
 * The list also offered a pool chair that was itself in maintenance. Lendable means "Available for
 * Loan", plus the older "Available for Issuance" that pool stock shelved before loaners had a status
 * of their own may still carry. "Sent to Store" is parked, and maintenance, transit and disposal are
 * the asset being somewhere else.
 */
@Component
@RequiredArgsConstructor
public class LoanerAvailability {

    /** The statuses a loaner may be lent from. */
    public static final Set<String> LENDABLE_STATUS_CODES = Set.of("loanAvailable", "issuanceAvailable");

    /** The stores the loaner pool is kept in - all the temporary-replacement picker has ever read. */
    private static final Set<StoreType> LOANER_STORES = Set.of(StoreType.IT, StoreType.ADMIN);

    private final MovementRepository movementRepository;

    /** The candidates that may be lent now, in their original order. One query for the whole list. */
    @Transactional(readOnly = true)
    public List<Asset> lendable(List<Asset> candidates) {
        List<Asset> shelved = candidates.stream().filter(LoanerAvailability::isShelvedLoaner).toList();
        if (shelved.isEmpty()) return shelved;
        Set<Long> committed = new HashSet<>(movementRepository.findAssetIdsOnOpenMovements(
                shelved.stream().map(Asset::getId).filter(Objects::nonNull).toList()));
        return shelved.stream().filter(a -> !committed.contains(a.getId())).toList();
    }

    /**
     * Refuses a loaner that may not be lent, saying why - so a stale list, a second tab or two people
     * at once cannot put one loaner on two temporary replacements.
     */
    @Transactional(readOnly = true)
    public void requireLendable(Asset loaner) {
        String label = label(loaner);
        if (!loaner.isTemporaryPool()) {
            throw new IllegalArgumentException(label + " is not in the loaner pool, so it cannot be lent as a temporary replacement.");
        }
        if (loaner.getAssignedTo() != null) {
            throw new IllegalArgumentException(label + " is already with somebody, so it cannot be lent again.");
        }
        if (loaner.getCurrentStore() == null || !LOANER_STORES.contains(loaner.getCurrentStore().getStoreType())) {
            throw new IllegalArgumentException(label + " is not in the IT or Admin store, so it cannot be lent from there.");
        }
        String status = loaner.getAssetStatus() == null ? null : loaner.getAssetStatus().getStatus();
        if (status == null || !LENDABLE_STATUS_CODES.contains(status)) {
            String name = loaner.getAssetStatus() == null ? "no status" : loaner.getAssetStatus().getName();
            throw new IllegalArgumentException(label + " reads \"" + name + "\", so it is not available to lend.");
        }
        List<Movement> open = movementRepository.findOpenMovementsForAsset(loaner.getId());
        if (!open.isEmpty()) {
            Movement m = open.get(0);
            String what = m.getMovementType() == MovementType.TEMP_REPLACEMENT
                    ? "temporary replacement #" + m.getId()
                    : "movement #" + m.getId();
            throw new IllegalArgumentException(label + " is already on " + what
                    + ", which has not been completed. Choose another loaner, or cancel that movement first.");
        }
    }

    /** Pool stock sitting unassigned in a loaner store in a lendable status - everything but the movement check. */
    static boolean isShelvedLoaner(Asset a) {
        if (a == null || !a.isTemporaryPool() || a.getAssignedTo() != null) return false;
        if (a.getCurrentStore() == null || !LOANER_STORES.contains(a.getCurrentStore().getStoreType())) return false;
        return a.getAssetStatus() != null && LENDABLE_STATUS_CODES.contains(a.getAssetStatus().getStatus());
    }

    private static String label(Asset a) {
        String name = a.getAssetName() == null ? "This asset" : a.getAssetName();
        return a.getEngravedNumber() == null || a.getEngravedNumber().isBlank()
                ? name : name + " (" + a.getEngravedNumber() + ")";
    }
}

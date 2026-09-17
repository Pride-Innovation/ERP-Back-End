package pride.bank.erp.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreBalance;
import pride.bank.erp.store.StoreBalanceRepository;
import pride.bank.erp.store.StoreRepository;
import pride.bank.erp.store.StoreType;

import java.util.List;

/**
 * Phase 1 (Store foundation) seeder + one-time migration. Idempotent — safe to run on every startup.
 *
 * <ol>
 *   <li>Ensures every location (branch) has its location-level {@link StoreType#ADMIN} store.</li>
 *   <li>Ensures Head Office additionally has {@link StoreType#IT} and {@link StoreType#DISPOSAL}
 *       stores (CLAUDE.md §4.2 / §4.3).</li>
 *   <li>Backfills {@link StoreBalance#getStore()} for legacy balances created before the container
 *       existed, pointing each at its branch's ADMIN store.</li>
 *   <li>Drops the legacy {@code (commodity_id, branch_id)} unique index on the {@code store} table
 *       so a branch can hold the same commodity across multiple store types.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StoreSeeder {

    private final BranchRepository branchRepository;
    private final StoreRepository storeRepository;
    private final StoreBalanceRepository storeBalanceRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void seedStoresAndMigrateBalances() {
        seedStores();
        backfillBalanceContainers();
        relaxLegacyBalanceUniqueness();
        log.info("Store seeding + balance migration complete");
    }

    private void seedStores() {
        List<Branch> branches = branchRepository.findAll();
        for (Branch branch : branches) {
            ensureStore(branch, StoreType.ADMIN, branch.getName() + " Admin Store");
        }

        // The IT and Disposal stores are singletons for the whole organisation, held at Head Office.
        // That is the control that makes "repair only happens at Head Office" true, and the lookups
        // in RepairMovementService (findByStoreType(...).findFirst()) are correct precisely because
        // of it. Seeding them per-branch inside the loop above would quietly break that the moment
        // the head-office flag moved, so they are seeded once, against the one Head Office branch.
        Branch headOffice = branchRepository.findFirstByIsHeadOfficeTrue().orElse(null);
        if (headOffice == null) {
            log.warn("No branch is flagged as Head Office — the IT and Disposal stores cannot be "
                    + "created. Repair transfers and disposals will fail until a branch is flagged "
                    + "and the application is restarted.");
        } else {
            ensureSingletonStore(headOffice, StoreType.IT, headOffice.getName() + " IT Store");
            ensureSingletonStore(headOffice, StoreType.DISPOSAL, headOffice.getName() + " Disposal Store");
        }

        assertSingleton(StoreType.IT);
        assertSingleton(StoreType.DISPOSAL);
    }

    /**
     * Creates an organisation-wide singleton store at Head Office.
     *
     * <p>Unlike {@link #ensureStore}, this checks for a store of the type <i>anywhere</i>, not just
     * at this branch. If one already exists at a different location — which is what happens when the
     * head-office flag is moved from one branch to another — it is left alone and reported, rather
     * than a second one being created. Two stores of the same type would make
     * {@code findByStoreType(...).findFirst()} silently ambiguous.
     */
    private void ensureSingletonStore(Branch headOffice, StoreType storeType, String name) {
        List<Store> existing = storeRepository.findByStoreType(storeType);

        if (existing.isEmpty()) {
            log.info("Creating the organisation's {} store at '{}'", storeType, headOffice.getName());
            storeRepository.save(Store.builder()
                    .location(headOffice)
                    .storeType(storeType)
                    .name(name)
                    .active(true)
                    .build());
            return;
        }

        Store current = existing.get(0);
        boolean atHeadOffice = current.getLocation() != null
                && current.getLocation().getId().equals(headOffice.getId());
        if (!atHeadOffice) {
            log.warn("The {} store is held at '{}' but Head Office is now '{}'. It has NOT been "
                            + "duplicated — move the existing store's location instead, so the "
                            + "organisation keeps exactly one {} store.",
                    storeType,
                    current.getLocation() != null ? current.getLocation().getName() : "an unknown branch",
                    headOffice.getName(), storeType);
        }
    }

    /**
     * Guards the "exactly one" invariant that {@code findByStoreType(...).findFirst()} depends on.
     *
     * <p>Duplicates <b>fail startup</b>: the lookup would still succeed but route to an arbitrary
     * store, so repairs could silently land in the wrong place — worse than not running at all.
     *
     * <p>Zero is loud but non-fatal: the rest of the application works fine, and the branch may
     * simply not be flagged yet. Repair and disposal then fail with an actionable message from
     * {@code RepairMovementService} rather than the silent misbehaviour we are guarding against.
     */
    private void assertSingleton(StoreType storeType) {
        int count = storeRepository.findByStoreType(storeType).size();
        if (count == 1) return;

        if (count == 0) {
            log.error("No {} store exists. Flag a branch as Head Office and restart — repair "
                    + "transfers and disposals cannot run without it.", storeType);
            return;
        }

        throw new IllegalStateException(String.format(
                "%d %s stores exist, but the organisation must have exactly one — repairs and "
                        + "disposals would route to an arbitrary store. Remove the duplicates and restart.",
                count, storeType));
    }

    /** Location-level store (department is null) — created only if absent. */
    private void ensureStore(Branch branch, StoreType storeType, String name) {
        storeRepository
                .findByLocation_IdAndStoreTypeAndDepartmentIsNull(branch.getId(), storeType)
                .orElseGet(() -> {
                    log.info("Creating {} store for branch '{}'", storeType, branch.getName());
                    return storeRepository.save(Store.builder()
                            .location(branch)
                            .storeType(storeType)
                            .name(name)
                            .active(true)
                            .build());
                });
    }

    /** Points every legacy balance at its branch's ADMIN store. */
    private void backfillBalanceContainers() {
        List<StoreBalance> orphans = storeBalanceRepository.findByStoreIsNull();
        if (orphans.isEmpty()) return;

        int updated = 0;
        for (StoreBalance balance : orphans) {
            if (balance.getBranch() == null) continue;
            Store adminStore = storeRepository
                    .findByLocation_IdAndStoreTypeAndDepartmentIsNull(balance.getBranch().getId(), StoreType.ADMIN)
                    .orElse(null);
            if (adminStore == null) continue;
            balance.setStore(adminStore);
            storeBalanceRepository.save(balance);
            updated++;
        }
        log.info("Backfilled store container on {} legacy balance row(s)", updated);
    }

    /**
     * Drops any unique index on {@code store} that covers exactly {@code (commodity_id, branch_id)}.
     * Guarded via {@code information_schema} and idempotent; the new {@code (commodity_id, store_id)}
     * uniqueness is declared on {@link StoreBalance} and ensured here if missing. Never throws — a
     * failure only logs, so a permissions/schema quirk can't abort startup.
     */
    private void relaxLegacyBalanceUniqueness() {
        try {
            @SuppressWarnings("unchecked")
            List<String> legacyIndexes = entityManager.createNativeQuery(
                    "SELECT INDEX_NAME FROM information_schema.STATISTICS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'store' AND NON_UNIQUE = 0 " +
                    "GROUP BY INDEX_NAME " +
                    "HAVING COUNT(*) = 2 " +
                    "  AND SUM(COLUMN_NAME = 'commodity_id') = 1 " +
                    "  AND SUM(COLUMN_NAME = 'branch_id') = 1")
                    .getResultList();

            for (String indexName : legacyIndexes) {
                entityManager.createNativeQuery("ALTER TABLE store DROP INDEX " + indexName).executeUpdate();
                log.info("Dropped legacy unique index '{}' on store(commodity_id, branch_id)", indexName);
            }

            Number hasNew = (Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM (" +
                    "  SELECT INDEX_NAME FROM information_schema.STATISTICS " +
                    "  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'store' AND NON_UNIQUE = 0 " +
                    "  GROUP BY INDEX_NAME " +
                    "  HAVING COUNT(*) = 2 " +
                    "    AND SUM(COLUMN_NAME = 'commodity_id') = 1 " +
                    "    AND SUM(COLUMN_NAME = 'store_id') = 1" +
                    ") t").getSingleResult();

            if (hasNew.intValue() == 0) {
                entityManager.createNativeQuery(
                        "CREATE UNIQUE INDEX uk_store_balance_commodity_store ON store (commodity_id, store_id)")
                        .executeUpdate();
                log.info("Created unique index uk_store_balance_commodity_store on store(commodity_id, store_id)");
            }
        } catch (Exception e) {
            log.warn("Could not relax legacy store uniqueness (commodity_id, branch_id); " +
                    "relax manually before holding a commodity in multiple store types per branch: {}",
                    e.getMessage());
        }
    }
}

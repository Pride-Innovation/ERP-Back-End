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
            if (branch.isHeadOffice()) {
                ensureStore(branch, StoreType.IT, branch.getName() + " IT Store");
                ensureStore(branch, StoreType.DISPOSAL, branch.getName() + " Disposal Store");
            }
        }
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

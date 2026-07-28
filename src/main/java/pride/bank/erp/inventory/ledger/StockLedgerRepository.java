/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.inventory.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockLedgerRepository extends JpaRepository<StockLedgerEntry, Long> {

    /** The movement history of one commodity in one store, oldest first — the audit view. */
    List<StockLedgerEntry> findByStore_IdAndCommodity_IdOrderByIdAsc(Long storeId, Long commodityId);

    /** Everything that touched a store, newest first. */
    List<StockLedgerEntry> findByStore_IdOrderByIdDesc(Long storeId);

    /** Every entry produced by one document, e.g. all lines of a receipt. */
    List<StockLedgerEntry> findBySourceTypeAndSourceId(String sourceType, Long sourceId);

    /**
     * The balance each (store, commodity) pair <i>should</i> have according to the ledger:
     * {@code [storeId, commodityId, sum(delta)]}. Compared against {@code StoreBalance.quantity},
     * any difference is drift — a balance changed by something that did not write an entry.
     */
    @Query("""
            SELECT e.store.id, e.commodity.id, SUM(e.quantityDelta)
            FROM StockLedgerEntry e
            GROUP BY e.store.id, e.commodity.id
            """)
    List<Object[]> sumDeltasByStoreAndCommodity();

    @Query("""
            SELECT e FROM StockLedgerEntry e
            WHERE (:storeId IS NULL OR e.store.id = :storeId)
              AND (:from IS NULL OR e.occurredAt >= :from)
              AND (:to IS NULL OR e.occurredAt <= :to)
            ORDER BY e.id DESC
            """)
    List<StockLedgerEntry> findForReport(@Param("storeId") Long storeId,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to);
}

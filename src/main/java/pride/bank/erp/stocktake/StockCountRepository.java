/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stocktake;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockCountRepository extends JpaRepository<StockCount, Long> {

    List<StockCount> findByStore_IdOrderByIdDesc(Long storeId);

    List<StockCount> findAllByOrderByIdDesc();

    /**
     * An unfinished count on the same store. Two people counting the same shelves at once produce
     * two conflicting truths, so a second count is refused while the first is unresolved.
     */
    @Query("""
            SELECT c FROM StockCount c
            WHERE c.store.id = :storeId
              AND c.status IN (pride.bank.erp.stocktake.StockCountStatus.DRAFT,
                               pride.bank.erp.stocktake.StockCountStatus.IN_PROGRESS,
                               pride.bank.erp.stocktake.StockCountStatus.SUBMITTED,
                               pride.bank.erp.stocktake.StockCountStatus.APPROVED)
            """)
    List<StockCount> findOpenForStore(@Param("storeId") Long storeId);
}

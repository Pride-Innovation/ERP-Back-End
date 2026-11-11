package pride.bank.erp.issuedCommodity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IssuedCommodityRepository extends JpaRepository<IssuedCommodity, Long> {
    @Query("""
            SELECT ic FROM IssuedCommodity ic
            JOIN FETCH ic.issuance i
            JOIN FETCH i.requester r
            WHERE ic.commodity.id = :commodityId
            ORDER BY i.createDate DESC
            """)
    List<IssuedCommodity> findLastIssuedCommodityByCommodityId(@Param("commodityId") Long commodityId, Pageable pageable);

    @Query("""
                SELECT c.assetType.name AS assetType, c.name AS commodityName, SUM(ic.quantity) AS totalQuantity
                FROM IssuedCommodity ic
                JOIN ic.commodity c
                JOIN ic.issuance i
                JOIN i.requester u
                WHERE u.branch.id = :branchId
                  AND FUNCTION('MONTH', i.createDate) = :month
                  AND FUNCTION('YEAR', i.createDate) = :year
                GROUP BY c.assetType.name, c.name
            """)
    List<CommoditySummary> findAllCommodityQuantitiesGroupedByAssetType(
            @Param("branchId") Long branchId,
            @Param("month") int month,
            @Param("year") int year
    );

    /** The lines an issuance carries - what its approval debits. */
    List<IssuedCommodity> findByIssuance_Id(Long issuanceId);

    /**
     * Stock promised by issuances not yet settled, per commodity, at one branch's Admin store: issued,
     * waiting for approval, and not on a rejected or deleted request. The issuer's branch is the store
     * the approval will debit (or the fulfilment movement will draw from).
     *
     * <p>The joins are inner on purpose: every one is to something the predicate requires. The status
     * join is LEFT, because a request with no status still holds what it issued.
     *
     * @return rows of {@code [commodityId, quantity]}
     */
    @Query("""
            SELECT ic.commodity.id, SUM(ic.quantity)
            FROM IssuedCommodity ic
            JOIN ic.issuance i
            JOIN i.issuer u
            JOIN u.branch b
            JOIN i.request r
            LEFT JOIN r.status st
            WHERE i.stockSettled = false
              AND b.id = :branchId
              AND (r.deleted IS NULL OR r.deleted = false)
              AND (st IS NULL OR st.status <> 'requestRejected')
            GROUP BY ic.commodity.id
            """)
    List<Object[]> findPromisedByUnsettledIssuances(@Param("branchId") Long branchId);
}

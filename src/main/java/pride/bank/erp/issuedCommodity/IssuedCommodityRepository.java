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

}

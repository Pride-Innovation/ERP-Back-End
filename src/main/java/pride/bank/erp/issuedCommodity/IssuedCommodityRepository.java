package pride.bank.erp.issuedCommodity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

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

}

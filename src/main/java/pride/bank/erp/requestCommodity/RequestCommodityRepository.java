package pride.bank.erp.requestCommodity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequestCommodityRepository extends JpaRepository<RequestCommodity, Long> {
    @Query("SELECT rc FROM RequestCommodity rc WHERE rc.request.id = :requestId")
    List<RequestCommodity> findByRequestId(@Param("requestId") Long requestId);

    @Modifying
    @Query("DELETE FROM RequestCommodity rc WHERE rc.request.id = :requestId")
    void deleteByRequestId(@Param("requestId") Long requestId);
}

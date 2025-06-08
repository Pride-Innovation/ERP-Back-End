package pride.bank.erp.store;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.commodity.Commodity;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {
    Optional<Store> findByBranchAndCommodity(Branch branch, Commodity commodity);

    @Query("SELECT s FROM Store s " +
            "JOIN FETCH s.commodity " +
            "JOIN FETCH s.branch " +
            "WHERE s.branch.id = :branchId")
    List<Store> findAllByBranchIdWithCommodityAndBranch(Long branchId);

    Page<Store> findByBranchIdAndCommodity_AssetTypeId(Long branchId, Long assetTypeId, Pageable pageable);

    @Query("SELECT s FROM Store s " +
            "WHERE s.branch.id = :branchId " +
            "AND s.commodity.assetType.id = :assetTypeId")
    List<Store> findByBranchAndAssetType(@Param("branchId") Long branchId,
                                         @Param("assetTypeId") Long assetTypeId);

}

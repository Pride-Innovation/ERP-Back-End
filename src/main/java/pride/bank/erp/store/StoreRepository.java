package pride.bank.erp.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the {@link Store} container (location + type + department).
 * On-hand quantities live in {@link StoreBalance} via {@link StoreBalanceRepository}.
 */
@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {

    /** The single location-level store of a given type (department is null). */
    Optional<Store> findByLocation_IdAndStoreTypeAndDepartmentIsNull(Long locationId, StoreType storeType);

    /** A department-level store of a given type. */
    Optional<Store> findByLocation_IdAndStoreTypeAndDepartment_Id(Long locationId, StoreType storeType, Long departmentId);

    List<Store> findByLocation_Id(Long locationId);

    List<Store> findByStoreType(StoreType storeType);

    List<Store> findByDepartment_Id(Long departmentId);

    boolean existsByLocation_IdAndStoreTypeAndDepartmentIsNull(Long locationId, StoreType storeType);

    /** A courier's custody store (see {@link StoreType#COURIER}). */
    Optional<Store> findByCourier_Id(Long courierId);

    boolean existsByCourier_Id(Long courierId);
}

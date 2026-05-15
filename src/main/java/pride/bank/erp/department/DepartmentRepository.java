package pride.bank.erp.department;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    Optional<Department> findByNameIgnoreCase(String name);

    Optional<Department> findByShortCode(String shortCode);

    Page<Department> findAllByNameContainingIgnoreCase(String name, Pageable pageable);
}

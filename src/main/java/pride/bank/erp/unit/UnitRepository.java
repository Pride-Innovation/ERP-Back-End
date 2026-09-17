/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.unit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Long> {

    Page<Unit> findAllByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Unit> findAllByDepartment_Id(Long departmentId, Pageable pageable);

    Page<Unit> findAllByNameContainingIgnoreCaseAndDepartment_Id(String name, Long departmentId, Pageable pageable);

    Optional<Unit> findByNameIgnoreCaseAndDepartment_Id(String name, Long departmentId);

    /** Used by the workflow engine to map a step's group email back to the owning unit. */
    Optional<Unit> findByGroupEmailIgnoreCase(String groupEmail);
}

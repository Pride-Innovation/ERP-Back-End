/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.consultant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsultantRepository extends JpaRepository<Consultant, Long> {
    List<Consultant> findByActiveTrueOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}

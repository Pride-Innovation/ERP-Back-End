/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.title;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TitleRepository extends JpaRepository<Title, Long> {
    Optional<Title> findByName(String name);

    Optional<Title> findByNameIgnoreCaseAndShortCodeIgnoreCaseAndReportsToIsNull(String name, String shortCode);

    Optional<Title> findByNameIgnoreCaseAndShortCodeIgnoreCaseAndReportsTo_Id(String name, String shortCode, Long reportsToId);

    @Query("SELECT t.id FROM Title t WHERE t.reportsTo.id = :titleId")
    List<Long> findSubordinateTitleIds(@Param("titleId") Long titleId);

    Optional<Title> findByNameIgnoreCase(String name);

}

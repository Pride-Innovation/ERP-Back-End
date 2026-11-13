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

    /**
     * Locks one title row for the rest of the transaction, so two people giving the same
     * single-holder title at the same location at the same moment queue instead of both passing the
     * check. Native and on the id alone: an entity lock would also lock every row the title's eager
     * associations join to.
     */
    @Query(value = "SELECT id FROM title WHERE id = :id FOR UPDATE", nativeQuery = true)
    @org.springframework.transaction.annotation.Transactional // joins the caller's; never read-only
    Long lockRow(@Param("id") Long id);

    List<Title> findAllByNameIgnoreCaseOrderByIdAsc(String name);

}

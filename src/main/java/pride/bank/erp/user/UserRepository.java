/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    /**
     * Resolves the holder named on an asset-import row.
     *
     * <p>Staff number rather than name: two people share a name often enough that matching on one
     * would silently assign assets to the wrong person, and a staff number is what an asset register
     * exported from an older system actually carries.
     */
    Optional<User> findByStaffNumberIgnoreCase(String staffNumber);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);

    boolean existsByStaffNumber(String staffNumber);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByStaffNumberAndIdNot(String staffNumber, Long id);

    List<User> findByTitleId(Long tileId);

    /** Members of a unit — used to notify everyone in a unit when a request is routed to it. */
    List<User> findByUnit_Id(Long unitId);

    @Query("SELECT u.id FROM User u WHERE u.title.id IN :titleIds")
    List<Long> findUserIdsByTitleIds(@Param("titleIds") List<Long> titleIds);

    @Query("SELECT u FROM User u WHERE u.title.id = :titleId AND u.branch.id = :branchId")
    List<User> findByTitleIdAndBranchId(@Param("titleId") Long titleId, @Param("branchId") Long branchId);

    @Query("SELECT u.id FROM User u WHERE u.branch.id = :branchId")
    List<Long> findUserIdsByBranchId(@Param("branchId") Long branchId);

    @Query("SELECT u.id FROM User u WHERE u.branch.id = 1")
    List<Long> findUserIdsFromHeadOffice();

    @Query("SELECT b.branchOperationsManager.id FROM Branch b WHERE b.branchOperationsManager IS NOT NULL")
    List<Long> findAllBranchOperationsManagerIds();

    @Query("SELECT u FROM User u " +
            "WHERE LOWER(u.firstName) LIKE LOWER(CONCAT('%', :text, '%')) " +
            "   OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :text, '%')) " +
            "   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :text, '%')) " +
            "   OR LOWER(u.staffNumber) LIKE LOWER(CONCAT('%', :text, '%'))")
    Page<User> searchByNameEmailOrStaffNumber(@Param("text") String text, Pageable pageable);
}
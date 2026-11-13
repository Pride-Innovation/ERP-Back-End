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

    boolean existsByTitle_Id(Long titleId);

    boolean existsByActingTitle_Id(Long titleId);

    /** Members of a unit — used to notify everyone in a unit when a request is routed to it. */
    List<User> findByUnit_Id(Long unitId);

    /**
     * Active members of every unit carrying the named role - the officers who may be recorded as a
     * repair's internal technician.
     *
     * <p>Keyed on the role the unit confers ({@code INFRA_UNIT}, {@code ADMIN_UNIT}), never on the
     * unit's name: a unit is renamed from Settings, and a lookup by display name would empty the
     * technician picker the day somebody did.
     *
     * <p>Inner joins on purpose. A user with no unit, or a unit without the role, is exactly who this
     * must not return - trap #1 is about joins through an association the query treats as optional,
     * and here it is the filter.
     */
    @Query("SELECT DISTINCT u FROM User u JOIN u.unit un JOIN un.roles r "
            + "WHERE r.name = :roleName AND u.isEnabled = true AND u.blocked = false "
            + "ORDER BY u.firstName, u.lastName, u.id")
    List<User> findActiveMembersOfUnitsWithRole(@Param("roleName") String roleName);

    @Query("SELECT u.id FROM User u WHERE u.title.id IN :titleIds")
    List<Long> findUserIdsByTitleIds(@Param("titleIds") List<Long> titleIds);

    /**
     * The same lookup, confined to one branch.
     *
     * <p>A title is organisation-wide — "Teller" is the same title at every branch — so resolving
     * subordinates by title alone answers with people from the whole bank. That is how a Gulu
     * supervisor came to see Mbarara's requests: the reporting line is a property of the title, and
     * the branch has to be applied separately.
     */
    @Query("SELECT u.id FROM User u WHERE u.title.id IN :titleIds AND u.branch.id = :branchId")
    List<Long> findUserIdsByTitleIdsAndBranchId(@Param("titleIds") List<Long> titleIds,
                                                @Param("branchId") Long branchId);

    /**
     * Who actually holds a title: not blocked, and either enabled or <b>on leave</b>.
     *
     * <p>"Disabled" means three things in this application - switched off by an administrator, away
     * on leave ({@code LeaveService} disables the account until the return), and not yet verified.
     * Only the first ends someone's hold on a title. A Branch Manager on leave is still the Branch
     * Manager: their requests go to the acting officer through {@code effectiveApprover}, and the
     * title must not look vacant to whoever creates the next account. A new account awaiting its
     * verification is not counted, so verifying it is checked instead (see
     * {@code AuthenticationService.verifyAccount}).
     */
    String ACTIVE_HOLDER = "u.blocked = false AND (u.isEnabled = true OR EXISTS "
            + "(SELECT l.id FROM leave_request l WHERE l.onLeaveUser = u AND l.active = true))";

    /**
     * The holders of a title at one location, oldest account first.
     *
     * <p>Replaces an unordered, unfiltered lookup whose callers took {@code get(0)}: with two holders
     * MySQL chose the approver, and a departed holder's disabled account could be chosen and leave a
     * request waiting on someone who cannot sign in.
     */
    @Query("SELECT u FROM User u WHERE u.title.id = :titleId AND u.branch.id = :branchId AND "
            + ACTIVE_HOLDER + " ORDER BY u.id")
    List<User> findActiveHoldersInBranch(@Param("titleId") Long titleId, @Param("branchId") Long branchId);

    /** Every active holder of a title that has a location, grouped by location. */
    @Query("SELECT u FROM User u WHERE u.title.id = :titleId AND u.branch IS NOT NULL AND "
            + ACTIVE_HOLDER + " ORDER BY u.branch.id, u.id")
    List<User> findActiveHoldersOfTitle(@Param("titleId") Long titleId);

    /** Every active holder of a single-holder title, for the conflicts report. */
    @Query("SELECT u FROM User u WHERE u.title.singleHolder = true AND u.branch IS NOT NULL AND "
            + ACTIVE_HOLDER + " ORDER BY u.title.id, u.branch.id, u.id")
    List<User> findActiveHoldersOfSingleHolderTitles();

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

    /**
     * The same search, narrowed to one duty station.
     *
     * <p>The branch predicate is written as an explicit <b>LEFT JOIN</b>, not as {@code u.branch.id}.
     * A dotted path there would inner-join through a nullable association and drop every branchless
     * account from the FROM clause before the comparison ran — the trap that has already emptied a
     * branch-scoped movement listing, an approver inbox and a national roll-up. Those accounts should
     * not match a branch filter, but they should be excluded *because the branch differs*, not
     * because the join removed them where nobody could see it happen.
     *
     * <p>The text predicate is parenthesised, which the unscoped version above does not need and this
     * one does: {@code AND} binds tighter than {@code OR}, so without the brackets the branch would
     * apply to the staff-number clause alone and a search on a first name would return the whole bank.
     */
    @Query("SELECT u FROM User u LEFT JOIN u.branch b "
            + "WHERE b.id = :branchId AND ("
            + "     LOWER(u.firstName) LIKE LOWER(CONCAT('%', :text, '%')) "
            + "  OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :text, '%')) "
            + "  OR LOWER(u.email) LIKE LOWER(CONCAT('%', :text, '%')) "
            + "  OR LOWER(u.staffNumber) LIKE LOWER(CONCAT('%', :text, '%')))")
    Page<User> searchByNameEmailOrStaffNumberInBranch(@Param("text") String text,
                                                      @Param("branchId") Long branchId,
                                                      Pageable pageable);
}
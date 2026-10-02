/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.assetAssignmentHostory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssetAssignmentHistoryRepository extends JpaRepository<AssetAssignmentHistory, Long> {
    @Query("SELECT aah FROM AssetAssignmentHistory aah WHERE aah.asset.id = :assetId AND aah.endDate IS NULL")
    AssetAssignmentHistory findActiveByAssetId(Long assetId);

    /**
     * Loaners currently out with one person: open assignments flagged temporary.
     *
     * <p>The assignment is the truth about who holds a loaner, not the movement that issued it. A
     * movement says a loaner was <em>sent</em>; only an open temporary assignment says it is still
     * there. The automatic return this replaced read the movements, and so kept "returning" loaners
     * that had come back months earlier.
     */
    @Query("SELECT aah FROM AssetAssignmentHistory aah "
            + "WHERE aah.user.id = :userId AND aah.temporary = true AND aah.endDate IS NULL "
            + "ORDER BY aah.startDate DESC, aah.id DESC")
    java.util.List<AssetAssignmentHistory> findOpenLoansForUser(@Param("userId") Long userId);

    /** Every loaner currently out, for the storekeepers' outstanding list. Scoped by the caller. */
    @Query("SELECT aah FROM AssetAssignmentHistory aah "
            + "WHERE aah.temporary = true AND aah.endDate IS NULL "
            + "ORDER BY aah.startDate ASC, aah.id ASC")
    java.util.List<AssetAssignmentHistory> findOpenLoans();

    @Query("SELECT aah FROM AssetAssignmentHistory aah WHERE aah.user.id = :userId AND aah.asset.id = :assetId")
    Optional<AssetAssignmentHistory> findByUserIdAndAssetId(
            @Param("userId") Long userId,
            @Param("assetId") Long assetId
    );
}

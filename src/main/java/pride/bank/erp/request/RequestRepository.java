/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.request;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RequestRepository extends JpaRepository<Request, Long> {
    @Query(value = """
                SELECT at.name AS assetType,
                       SUM(rc.quantity) AS totalRequested,
                       SUM(
                         CASE WHEN r.status_id = :acknowledgedStatusId
                              THEN rc.quantity
                              ELSE 0 END
                       ) AS totalDelivered
                FROM request r
                JOIN users u ON r.requester_id = u.id
                JOIN request_commodity rc ON rc.request_id = r.id
                JOIN commodity c ON rc.commodity_id = c.id
                JOIN asset_type at ON c.asset_type_id = at.id
                WHERE YEAR(r.create_date) = YEAR(CURRENT_DATE())
                  AND r.status_id != :rejectedStatusId
                  AND u.branch_id = :branchId
                GROUP BY at.name
            """, nativeQuery = true)
    List<Object[]> fetchAssetRequestSummaries(
            @Param("acknowledgedStatusId") Long acknowledgedStatusId,
            @Param("rejectedStatusId") Long rejectedStatusId,
            @Param("branchId") Long branchId
    );

    /**
     * The same requested-vs-delivered summary, narrowed to the viewer's dashboard scope.
     *
     * <p>Replaces the query above for the dashboard, which took a branch id and nothing else — so a
     * Head Office administrator saw Head Office's own fulfilment rather than the bank's, under a
     * widget titled for the whole organisation. Both filters are null-tolerant so one query serves
     * all three scopes: both null is the national roll-up, a branch id focuses one station, and an
     * owner id restricts it to what that person asked for.
     *
     * <p>Kept as a native query to match its sibling, and the branch is reached through a LEFT JOIN
     * on the requester so a request raised by someone with no duty station still counts towards the
     * national total instead of vanishing from it.
     */
    @Query(value = """
                SELECT at.name AS assetType,
                       SUM(rc.quantity) AS totalRequested,
                       SUM(
                         CASE WHEN r.status_id = :acknowledgedStatusId
                              THEN rc.quantity
                              ELSE 0 END
                       ) AS totalDelivered
                FROM request r
                LEFT JOIN users u ON r.requester_id = u.id
                JOIN request_commodity rc ON rc.request_id = r.id
                JOIN commodity c ON rc.commodity_id = c.id
                JOIN asset_type at ON c.asset_type_id = at.id
                WHERE YEAR(r.create_date) = YEAR(CURRENT_DATE())
                  AND r.status_id != :rejectedStatusId
                  AND (:branchId IS NULL OR u.branch_id = :branchId)
                  AND (:ownerId IS NULL OR r.requester_id = :ownerId)
                GROUP BY at.name
            """, nativeQuery = true)
    List<Object[]> fetchAssetRequestSummariesScoped(
            @Param("acknowledgedStatusId") Long acknowledgedStatusId,
            @Param("rejectedStatusId") Long rejectedStatusId,
            @Param("branchId") Long branchId,
            @Param("ownerId") Long ownerId
    );

    @Query("SELECT r FROM Request r WHERE r.status.id IN :statusIds")
    List<Request> findRequestsByStatusIds(@Param("statusIds") List<Long> statusIds);

    @Query("SELECT r FROM Request r WHERE r.status.id IN :statusIds AND r.createDate BETWEEN :startDate AND :endDate")
    List<Request> findRequestsByStatusIdsAndDateRange(
            @Param("statusIds") List<Long> statusIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

}

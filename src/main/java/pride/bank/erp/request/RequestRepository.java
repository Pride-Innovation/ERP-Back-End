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

    @Query("SELECT r FROM Request r WHERE r.status.id IN :statusIds")
    List<Request> findRequestsByStatusIds(@Param("statusIds") List<Long> statusIds);

}

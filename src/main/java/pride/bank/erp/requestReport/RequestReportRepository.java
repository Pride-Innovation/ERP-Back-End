/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.requestReport;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RequestReportRepository extends JpaRepository<RequestReport, Long> {

    Optional<RequestReport> findByRequestId(Long requestId);

    @Query("SELECT i FROM RequestReport i WHERE i.request.id = :requestId")
    List<RequestReport> findRequestReportsByRequestId(@Param("requestId") Long requestId);
}

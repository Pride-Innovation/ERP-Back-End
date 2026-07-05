/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.branch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {
    Optional<Branch> findByName(String name);
    Optional<Branch> findByNameIgnoreCase(String name);
    Optional<Branch> findByShortCode(String shortCode);
    Optional<Branch> findFirstByIsHeadOfficeTrue();

    @Query("""
            select
                b.id as id,
                b.name as name,
                b.email as email,
                b.telephone as telephone,
                b.managersGroupEmail as managersGroupEmail,
                b.isHeadOffice as headOffice,
                b.region as region,
                b.district as district,
                branchManager.id as branchManagerId,
                branchManager.firstName as branchManagerFirstName,
                branchManager.lastName as branchManagerLastName,
                branchManager.email as branchManagerEmail,
                branchOperationsManager.id as branchOperationsManagerId,
                branchOperationsManager.firstName as branchOperationsManagerFirstName,
                branchOperationsManager.lastName as branchOperationsManagerLastName,
                branchOperationsManager.email as branchOperationsManagerEmail,
                relationshipManager.id as relationshipManagerId,
                relationshipManager.firstName as relationshipManagerFirstName,
                relationshipManager.lastName as relationshipManagerLastName,
                relationshipManager.email as relationshipManagerEmail,
                creditAdministrator.id as creditAdministratorId,
                creditAdministrator.firstName as creditAdministratorFirstName,
                creditAdministrator.lastName as creditAdministratorLastName,
                creditAdministrator.email as creditAdministratorEmail
            from Branch b
            left join b.branchManager branchManager
            left join b.branchOperationsManager branchOperationsManager
            left join b.relationshipManager relationshipManager
            left join b.creditAdministrator creditAdministrator
            where b.id = :id
            """)
    Optional<BranchDetailsProjection> findBranchDetailsById(@Param("id") Long id);
}

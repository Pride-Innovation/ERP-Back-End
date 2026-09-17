/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pride.bank.erp.department.Department;
import pride.bank.erp.department.DepartmentRepository;
import pride.bank.erp.unit.Unit;
import pride.bank.erp.unit.UnitRepository;

/**
 * Seeds the two fulfilment units the workflow routes acknowledge / issuance steps to:
 * the Infrastructure unit (under Business Technology) and the Admin unit (under
 * Administration &amp; Procurement). Each owns a group mailbox; members of the unit
 * are who act on requests routed to that mailbox.
 *
 * <p>Create-only by design: an existing unit is left untouched so a group email edited
 * on the Units page (e.g. to match a workflow step) is never reverted on restart.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UnitSeeder {

    private final UnitRepository unitRepository;
    private final DepartmentRepository departmentRepository;

    @Value("${it-team.email.address:}")
    private String itTeamEmail;

    @Value("${admin-team.email.address:}")
    private String adminTeamEmail;

    public void seedUnits() {
        ensureUnit("Infra Unit", "BT", blankToNull(itTeamEmail));
        ensureUnit("Admin Unit", "ADM", blankToNull(adminTeamEmail));
        log.info("Unit seeding complete");
    }

    private void ensureUnit(String name, String departmentShortCode, String groupEmail) {
        Department department = departmentRepository.findByShortCode(departmentShortCode).orElse(null);
        if (department == null) {
            log.warn("Skipping unit '{}': department with shortCode '{}' not found", name, departmentShortCode);
            return;
        }

        // Create-only: never overwrite an existing unit's (possibly user-edited) group email.
        if (unitRepository.findByNameIgnoreCaseAndDepartment_Id(name, department.getId()).isPresent()) {
            return;
        }

        unitRepository.save(Unit.builder()
                .name(name)
                .groupEmail(groupEmail)
                .department(department)
                .build());
        log.info("Created unit '{}' under department '{}'", name, department.getName());
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}

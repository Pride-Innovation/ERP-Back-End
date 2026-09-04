/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.unit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.department.Department;
import pride.bank.erp.permission.Permission;
import pride.bank.erp.role.Role;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UnitResponse {
    private Long id;
    private String name;
    private String groupEmail;
    private DepartmentSummary department;

    /**
     * The roles membership of this unit confers.
     *
     * <p>Included so Settings can show what a unit actually grants before anyone is added to it. A
     * unit used to be a group mailbox with a department and this field would have been meaningless;
     * it is now the most consequential thing on the screen, because everyone in the unit inherits
     * every permission listed here.
     */
    private List<RoleSummary> roles;

    /**
     * A role and what it grants, flattened.
     *
     * <p>The permission names travel with the role rather than being looked up separately because
     * the confirmation dialog has to spell out the consequences of a grant, and a second round trip
     * to do that invites the two halves disagreeing about what was just approved.
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class RoleSummary {
        private Long id;
        private String name;
        private String description;
        private List<String> permissions;

        public static RoleSummary from(Role role) {
            if (role == null) return null;
            Set<Permission> held = role.getPermissions();
            return RoleSummary.builder()
                    .id(role.getId())
                    .name(role.getName())
                    .description(role.getDescription())
                    .permissions(held == null ? List.of() : held.stream()
                            .map(Permission::getName)
                            .filter(java.util.Objects::nonNull)
                            .sorted()
                            .toList())
                    .build();
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class DepartmentSummary {
        private Long id;
        private String name;
        private String shortCode;

        public static DepartmentSummary from(Department department) {
            if (department == null) return null;
            return DepartmentSummary.builder()
                    .id(department.getId())
                    .name(department.getName())
                    .shortCode(department.getShortCode())
                    .build();
        }
    }

    public static UnitResponse from(Unit unit) {
        if (unit == null) return null;
        return UnitResponse.builder()
                .id(unit.getId())
                .name(unit.getName())
                .groupEmail(unit.getGroupEmail())
                .department(DepartmentSummary.from(unit.getDepartment()))
                .roles(unit.getRoles() == null ? List.of() : unit.getRoles().stream()
                        .map(RoleSummary::from)
                        .sorted(Comparator.comparing(RoleSummary::getName,
                                Comparator.nullsLast(String::compareToIgnoreCase)))
                        .toList())
                .build();
    }
}

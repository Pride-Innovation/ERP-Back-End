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

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UnitResponse {
    private Long id;
    private String name;
    private String groupEmail;
    private DepartmentSummary department;

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
                .build();
    }
}

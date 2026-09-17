package pride.bank.erp.department;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.user.User;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DepartmentResponse {
    private Long id;
    private String name;
    private String managersGroupEmail;
    private String shortCode;
    private UserSummary headOfDepartment;
    private BranchSummary branch;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class UserSummary {
        private Long id;
        private String firstName;
        private String lastName;
        private String email;
        private String staffNumber;

        public static UserSummary from(User user) {
            if (user == null) return null;
            return UserSummary.builder()
                    .id(user.getId())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .email(user.getEmail())
                    .staffNumber(user.getStaffNumber())
                    .build();
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class BranchSummary {
        private Long id;
        private String name;
        private String shortCode;

        public static BranchSummary from(Branch branch) {
            if (branch == null) return null;
            return BranchSummary.builder()
                    .id(branch.getId())
                    .name(branch.getName())
                    .shortCode(branch.getShortCode())
                    .build();
        }
    }

    public static DepartmentResponse from(Department department) {
        if (department == null) return null;
        return DepartmentResponse.builder()
                .id(department.getId())
                .name(department.getName())
                .managersGroupEmail(department.getManagersGroupEmail())
                .shortCode(department.getShortCode())
                .headOfDepartment(UserSummary.from(department.getHeadOfDepartment()))
                .branch(BranchSummary.from(department.getBranch()))
                .build();
    }
}

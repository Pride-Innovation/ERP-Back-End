package pride.bank.erp.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.department.Department;
import pride.bank.erp.title.Title;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDAO {
    private String availability;
    private Branch branch;
    private String email;
    private String firstName;
    private String lastName;
    private String otherName;
    private String gender;
    private String staffNumber;
    private Title title;
    private Department department;
    private boolean isEnabled;
    private boolean isAccountNonLocked;
    private boolean blocked;
}

package pride.bank.erp.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.department.Department;
import pride.bank.erp.helper.Constants;
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

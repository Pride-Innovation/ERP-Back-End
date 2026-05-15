package pride.bank.erp.department;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static pride.bank.erp.helper.Constants.EMAIL_VALIDATOR;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentDTO {
    @NotBlank(message = "Department name is required")
    private String name;

    @NotNull(message = "Head of department is required")
    private Long headOfDepartment;

    @NotNull(message = "Branch is required")
    private Long branch;

    @Email(message = EMAIL_VALIDATOR)
    private String managersGroupEmail;
}

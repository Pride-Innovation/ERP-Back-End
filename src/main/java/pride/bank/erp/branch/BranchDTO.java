package pride.bank.erp.branch;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static pride.bank.erp.helper.Constants.EMAIL_VALIDATOR;
import static pride.bank.erp.helper.Constants.PHONE_REGEX;
import static pride.bank.erp.helper.Constants.PHONE_VALIDATOR;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BranchDTO {
    private Long branchManager;
    private Long branchOperationsManager;
    private Long creditAdministrator;
    private Long relationshipManager;

    @NotBlank(message = "Branch name is required")
    private String name;

    @NotNull(message = "Region is required")
    private Long region;

    @NotBlank(message = "Telephone is required")
    @Pattern(regexp = PHONE_REGEX, message = PHONE_VALIDATOR)
    private String telephone;

    @NotBlank(message = "Email is required")
    @Email(message = EMAIL_VALIDATOR)
    private String email;

    @NotNull(message = "District is required")
    private Long district;
}

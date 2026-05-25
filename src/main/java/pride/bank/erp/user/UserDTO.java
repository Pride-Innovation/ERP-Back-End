package pride.bank.erp.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.helper.Constants;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {
    private String availability;

    @NotNull(message = "Duty Station / Branch is required")
    private Long branch;

    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    @NotBlank(message = Constants.BLANK_FIELD_VALIDATOR)
    @Pattern(
            regexp = "^[A-Za-z0-9._%+-]+@pridebank\\.co\\.ug$",
            message = "Email must be a Pride Bank address ending with @pridebank.co.ug"
    )
    private String email;

    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    @Size(max = 30, min = 2, message = "First name must be between 2 and 30 characters")
    @Pattern(
            regexp = "^[A-Za-z][A-Za-z'\\- ]*$",
            message = "First name may only contain letters, spaces, hyphens and apostrophes"
    )
    private String firstName;

    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    @Size(max = 30, min = 2, message = "Last name must be between 2 and 30 characters")
    @Pattern(
            regexp = "^[A-Za-z][A-Za-z'\\- ]*$",
            message = "Last name may only contain letters, spaces, hyphens and apostrophes"
    )
    private String lastName;

    @Pattern(
            regexp = "^$|^[A-Za-z][A-Za-z'\\- ]*$",
            message = "Other name may only contain letters, spaces, hyphens and apostrophes"
    )
    private String otherName;

    @NotBlank(message = Constants.BLANK_FIELD_VALIDATOR)
    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    @Pattern(regexp = "^(?i)(male|female)$", message = "Gender must be 'Male' or 'Female'")
    private String gender;

    @NotBlank(message = Constants.BLANK_FIELD_VALIDATOR)
    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    private String staffNumber;

    @NotNull(message = "Title is required")
    private Long title;

    private Long department;
    private boolean isEnabled;
    private boolean isAccountNonLocked;
    private boolean blocked;
}

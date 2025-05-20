package pride.bank.erp.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    private Long branch;

    @Email(message = Constants.EMAIL_VALIDATOR)
    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    @NotBlank(message = Constants.BLANK_FIELD_VALIDATOR)
    private String email;

    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    @Size(max = 20, min = 3, message = Constants.NAME_VALIDATOR)
    private String firstName;

    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    @Size(max = 20, min = 3, message = Constants.NAME_VALIDATOR)
    private String lastName;

    private String otherName;

    @NotBlank(message = Constants.BLANK_FIELD_VALIDATOR)
    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    private String gender;

    @NotBlank(message = Constants.BLANK_FIELD_VALIDATOR)
    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    private String staffNumber;
    private Long title;
    private Long department;
}

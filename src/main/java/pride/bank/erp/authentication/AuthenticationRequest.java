package pride.bank.erp.authentication;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.helper.Constants;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticationRequest {

    @Email(message = Constants.EMAIL_VALIDATOR)
    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    @NotBlank(message = Constants.BLANK_FIELD_VALIDATOR)
    private String email;

    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    @Pattern(regexp = Constants.PASSWORD_REGEX, message = Constants.PASSWORD_VALIDATOR)
    private String password;
}

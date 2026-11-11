/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.authentication;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
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

    /**
     * No pattern: this is the employee's AD password, and AD's rules are not ours. A pattern here
     * would refuse a valid AD password before AD was asked. 256 is the API's own limit.
     *
     * <p>Out of {@code toString}: Spring logs request bodies at DEBUG through it, and
     * {@code org.springframework.web} runs at DEBUG in application.yaml - so this class used to write
     * every sign-in password to the log.
     */
    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    @NotBlank(message = Constants.BLANK_FIELD_VALIDATOR)
    @Size(max = 256)
    @ToString.Exclude
    private String password;
}

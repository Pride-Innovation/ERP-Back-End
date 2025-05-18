/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.authentication;

import jakarta.persistence.Column;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.helper.Constants;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RegisterRequest {
    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    @Size(max = 20, min = 3, message = Constants.NAME_VALIDATOR)
    private String firstName;

    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    @Size(max = 20, min = 3, message = Constants.NAME_VALIDATOR)
    private String lastName;

    @Email(message = Constants.EMAIL_VALIDATOR)
    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    @NotBlank(message = Constants.BLANK_FIELD_VALIDATOR)
    @Column(unique = true)
    private String email;

    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    @NotBlank(message = Constants.BLANK_FIELD_VALIDATOR)
    @Pattern(regexp = Constants.PASSWORD_REGEX, message = Constants.PASSWORD_VALIDATOR)
    private String password;

    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    @Pattern(regexp = Constants.PHONE_REGEX, message = Constants.PHONE_VALIDATOR)
    private String staffNumber;

    @NotBlank(message = Constants.BLANK_FIELD_VALIDATOR)
    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    private String gender;
    private boolean available;

}

/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.unit;

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
public class UnitDTO {
    @NotBlank(message = "Unit name is required")
    private String name;

    @NotNull(message = "Department is required")
    private Long departmentId;

    @Email(message = EMAIL_VALIDATOR)
    private String groupEmail;
}

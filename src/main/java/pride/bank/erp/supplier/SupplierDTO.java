/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.supplier;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import static pride.bank.erp.helper.Constants.EMAIL_VALIDATOR;
import static pride.bank.erp.helper.Constants.PHONE_REGEX;
import static pride.bank.erp.helper.Constants.PHONE_VALIDATOR;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SupplierDTO {
    @NotBlank(message = "Supplier name is required")
    private String name;

    private String address;

    @NotBlank(message = "Telephone is required")
    @Pattern(regexp = PHONE_REGEX, message = PHONE_VALIDATOR)
    private String telephone;

    @NotBlank(message = "Email is required")
    @Email(message = EMAIL_VALIDATOR)
    private String email;

    private Long commodity;
    private List<Long> commodities;
}

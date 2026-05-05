/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.title;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TitleRequestDTO {
    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Short code is required")
    private String shortCode;

    private Long reportsTo;

    @NotNull(message = "Role is required")
    private Long role; // E.g. Officer, supervisor manager etc.

}

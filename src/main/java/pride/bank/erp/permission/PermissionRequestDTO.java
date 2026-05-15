package pride.bank.erp.permission;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionRequestDTO {
    @NotBlank(message = "Permission name is required")
    private String name;
}

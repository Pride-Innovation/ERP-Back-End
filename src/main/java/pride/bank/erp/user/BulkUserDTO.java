package pride.bank.erp.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BulkUserDTO {
    private Long no;
    private String firstName;
    private String lastName;
    /** Optional middle / other name. */
    private String otherName;
    private String email;
    private String staffNumber;
    private String gender;
    private String title;
    /** Additional role attached to the user. Optional. */
    private String role;
    private String dutyStation;
    /** Department name. Only meaningful when dutyStation resolves to Head Office. */
    private String department;
}

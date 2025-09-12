package pride.bank.erp.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BulkUserDTO {
    private Long no;
    private String name;
    private String staffNumber;
    private String email;
    private String title;
    private String dutyStation;
    private String gender;
}

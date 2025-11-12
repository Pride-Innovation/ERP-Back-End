package pride.bank.erp.department;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentDTO {
    private String name;
    private Long headOfDepartment;
    private Long branch;
    private String managersGroupEmail;
}

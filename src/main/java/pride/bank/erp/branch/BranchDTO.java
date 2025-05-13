package pride.bank.erp.branch;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BranchDTO {
    private Long branchManager;
    private Long branchOperationsManager;
    private Long creditAdministrator;
    private Long relationshipManager;
    private String name;
    private Long region;
    private String telephone;
    private String email;
    private Long district;
}

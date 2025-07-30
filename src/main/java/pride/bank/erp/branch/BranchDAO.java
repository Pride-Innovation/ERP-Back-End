package pride.bank.erp.branch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.district.District;
import pride.bank.erp.region.Region;
import pride.bank.erp.user.User;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchDAO {
    private Long id;
    private User branchManager;
    private User branchOperationsManager;
    private User creditAdministrator;
    private User relationshipManager;
    private String name;
    private Region region;
    private String telephone;
    private String email;
    private District district;
}

package pride.bank.erp.branch;

import lombok.Builder;
import lombok.Data;
import pride.bank.erp.district.District;
import pride.bank.erp.region.Region;
import pride.bank.erp.user.User;

@Data
@Builder
public class BranchWithManagersDTO {
    private Long id;
    private String name;
    private String email;
    private String telephone;

    private User branchManager;
    private User branchOperationsManager;
    private User relationshipManager;
    private User creditAdministrator;

    private Region region;
    private District district;
}

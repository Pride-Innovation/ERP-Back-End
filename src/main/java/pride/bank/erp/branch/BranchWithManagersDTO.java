package pride.bank.erp.branch;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    private String managersGroupEmail;
    /**
     * Exposed so clients (e.g. the User form's Department picker) can branch on Head Office status.
     * The explicit @JsonProperty pins the JSON name to {@code isHeadOffice}; without it Jackson would
     * strip the {@code is} prefix from the Lombok-generated {@code isHeadOffice()} getter and emit
     * the property as {@code headOffice}, breaking the frontend contract.
     */
    @JsonProperty("isHeadOffice")
    private boolean isHeadOffice;

    private User branchManager;
    private User branchOperationsManager;
    private User relationshipManager;
    private User creditAdministrator;

    private Region region;
    private District district;
}

package pride.bank.erp.branch;

import pride.bank.erp.district.District;
import pride.bank.erp.region.Region;

public interface BranchDetailsProjection {
    Long getId();

    String getName();

    String getEmail();

    String getTelephone();

    String getManagersGroupEmail();

    boolean getHeadOffice();

    Region getRegion();

    District getDistrict();

    Long getBranchManagerId();

    String getBranchManagerFirstName();

    String getBranchManagerLastName();

    String getBranchManagerEmail();

    Long getBranchOperationsManagerId();

    String getBranchOperationsManagerFirstName();

    String getBranchOperationsManagerLastName();

    String getBranchOperationsManagerEmail();

    Long getRelationshipManagerId();

    String getRelationshipManagerFirstName();

    String getRelationshipManagerLastName();

    String getRelationshipManagerEmail();

    Long getCreditAdministratorId();

    String getCreditAdministratorFirstName();

    String getCreditAdministratorLastName();

    String getCreditAdministratorEmail();
}
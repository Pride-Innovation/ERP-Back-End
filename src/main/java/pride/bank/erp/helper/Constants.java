/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.helper;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Constants {
    public static final String DELETED_SUCCESSFULLY = "Deleted successfully";
    public static final String ENTITY_NOT_FOUND = "Entity not found";
    public static final String NAME_VALIDATOR = "Must be a minimum of 3 and maximum of 20 letters";
    public static final String NULL_FIELD_VALIDATOR = "Field cannot be null";
    public static final String BLANK_FIELD_VALIDATOR = "Field cannot be blank";
    public static final String EMAIL_VALIDATOR = "Email address must be valid";
    public static final String PHONE_VALIDATOR = "Invalid Ugandan phone number";
    public static final String PHONE_REGEX = "^\\+256\\d{9}";
    public static final String PASSWORD_REGEX = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=*!])(?=\\S+$).{8,}$";
    public static final String PASSWORD_VALIDATOR = "Password should contain, Lower case letter, uppercase letter, number, a symbol like #, @, ? and its length must be at least 8";
    public static final String GENERAL_ROUTE = "/api/v1";
    public static final String READ_USER = "READ_USER";
    public static final String CREATE_USER = "CREATE_USER";
    public static final String DELETE_USER = "DELETE_USER";
    public static final String UPDATE_USER = "UPDATE_USER";
    public static final String READ_ROLE = "READ_ROLE";
    public static final String CREATE_ROLE = "CREATE_ROLE";
    public static final String DELETE_ROLE = "DELETE_ROLE";
    public static final String UPDATE_ROLE = "UPDATE_ROLE";
    public static final String READ_PERMISSION = "READ_PERMISSION";
    public static final String CREATE_PERMISSION = "CREATE_PERMISSION";
    public static final String DELETE_PERMISSION = "DELETE_PERMISSION";
    public static final String UPDATE_PERMISSION = "UPDATE_PERMISSION";
    public static final String USER_ROUTE = GENERAL_ROUTE + "/users/**";
    public static final String REGISTER_USER_ROUTE = GENERAL_ROUTE + "/auth/register/**";
    public static final String ROLE_ROUTE = GENERAL_ROUTE + "/roles/**";
    public static final String PERMISSION_ROUTE = GENERAL_ROUTE + "/permissions/**";
    public static final String AUTH_ROUTE = GENERAL_ROUTE + "/auth/login";
    public static final String LOGOUT = GENERAL_ROUTE + "/auth/logout";
    public static final String REFRESH_TOKEN_ROUTE = GENERAL_ROUTE + "/auth/refresh-token";
    public static final String REQUEST_PASSWORD_RESET = GENERAL_ROUTE + "/request-password-reset";
    public static final String RESET_PASSWORD = GENERAL_ROUTE + "/reset-password";
    public static final String VERIFY_EMAIL = GENERAL_ROUTE + "/auth/verify-email/**";
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ADMIN = "ADMIN";
    public static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    public static final String NUMBERS = "0123456789";
    public static final String SPECIAL_CHARACTERS = "@#*!";
    public static final int PASSWORD_LENGTH = 10;
    public static final Integer DEFAULT_PAGE_SIZE = 10;
    public static final String DEFAULT_SORT_FIELD = "id";
    public static final Integer DEFAULT_PAGE_NUMBER = 0;
    public static final String ASCENDING_SORT_DIRECTION = "ASC";
    public static final String DESCENDING_SORT_DIRECTION = "DESC";
    public static final String AUDIT_ROUTE = GENERAL_ROUTE + "/audit/**";
    public static final String REQUEST_ROUTE = GENERAL_ROUTE + "/requests/**";
    public static final String STATUS_ROUTE = GENERAL_ROUTE + "/statuses/**";
    public static final String INVENTORY_ROUTE = GENERAL_ROUTE + "/stocks/**";
    public static final String ASSET_ROUTE = GENERAL_ROUTE + "/assets/**";

    /**
     * The caller's own assets. Sits under {@link #ASSET_ROUTE} by URL but is a different kind of
     * resource: the handler takes no id and resolves the holder from the authenticated principal,
     * so it can only ever return the caller's own records. Matched ahead of ASSET_ROUTE so that
     * checking what you are personally accountable for does not require READ_ASSET, which governs
     * the branch-wide register.
     */
    public static final String MY_ASSETS_ROUTE = GENERAL_ROUTE + "/assets/my-assets";

    /*
     * Dashboard reporting routes.
     *
     * These sit outside every resource prefix above (they are not under /assets, /requests or
     * /stocks), so before these constants existed they matched no rule and fell through to
     * `.anyRequest().authenticated()` — meaning any signed-in user could read national asset
     * totals and every pending request in the bank. Each now carries the same authority the
     * dashboard uses to decide whether to render the widget that consumes it.
     */

    /** Per-category asset totals across EVERY branch — unscoped, hence VIEW_ALL_BRANCHES. */
    public static final String REPORT_ALL_BRANCH_ASSETS = GENERAL_ROUTE + "/dashboard-asset-report";
    /** The same totals split by branch. Needs its own matcher — the rule above is an exact path. */
    public static final String REPORT_ASSETS_BY_BRANCH = GENERAL_ROUTE + "/dashboard-asset-report/by-branch";
    /** Monthly stocking volumes. */
    public static final String REPORT_MONTHLY_STOCK = GENERAL_ROUTE + "/monthly-asset-stock";
    /** The open request queue. */
    public static final String REPORT_PENDING_REQUESTS = GENERAL_ROUTE + "/latest-pending-request";
    /** Requested vs delivered per category for the current year. */
    public static final String REPORT_REQUEST_SUMMARY = GENERAL_ROUTE + "/current-year-summary";

    // Request Acknowledgement and Approvals Routes
    public static final String REQUEST_APPROVAL = GENERAL_ROUTE + "/approvals";
    public static final String REQUEST_ISSUANCE = GENERAL_ROUTE + "/issuance/**";
    public static final String REQUEST_ACKNOWLEDGEMENT = GENERAL_ROUTE + "/acknowledge-request";
    public static final String ISSUANCE_APPROVAL = GENERAL_ROUTE + "/approve-issuance";

    public static final String READ_REQUEST = "READ_REQUEST";
    public static final String CREATE_REQUEST = "CREATE_REQUEST";
    public static final String DELETE_REQUEST = "DELETE_REQUEST";
    public static final String UPDATE_REQUEST = "UPDATE_REQUEST";
    public static final String CREATE_STATUS = "CREATE_STATUS";
    public static final String DELETE_STATUS = "DELETE_STATUS";
    public static final String UPDATE_STATUS = "UPDATE_STATUS";
    public static final String Central_A = "Central A";
    public static final String Central_B = "Central B";
    public static final String Eastern = "Eastern";
    public static final String Northern = "Northern";
    public static final String Western = "Western";

    public static final String READ_TRANSPORT = "READ_TRANSPORT";
    public static final String CREATE_TRANSPORT = "CREATE_TRANSPORT";
    public static final String DELETE_TRANSPORT = "DELETE_TRANSPORT";
    public static final String UPDATE_TRANSPORT = "UPDATE_TRANSPORT";

    public static final String READ_INVENTORY = "READ_INVENTORY";
    public static final String CREATE_INVENTORY = "CREATE_INVENTORY";
    public static final String DELETE_INVENTORY = "DELETE_INVENTORY";
    public static final String UPDATE_INVENTORY = "UPDATE_INVENTORY";

    public static final String READ_SETTING = "READ_SETTING";
    public static final String CREATE_SETTING = "CREATE_SETTING";
    public static final String DELETE_SETTING = "DELETE_SETTING";
    public static final String UPDATE_SETTING = "UPDATE_SETTING";

    public static final String READ_AUDIT = "READ_AUDIT";
    public static final String CREATE_AUDIT = "CREATE_AUDIT";
    public static final String DELETE_AUDIT = "DELETE_AUDIT";
    public static final String UPDATE_AUDIT = "UPDATE_AUDIT";

    public static final String READ_STORE = "READ_STORE";
    public static final String CREATE_STORE = "CREATE_STORE";
    public static final String DELETE_STORE = "DELETE_STORE";
    public static final String UPDATE_STORE = "UPDATE_STORE";

    /**
     * Lets a user see data belonging to branches other than their own.
     *
     * <p>Grantable and revocable like any other permission: Head Office staff who need a national
     * view get it, everyone else is scoped to the branch they belong to. Absence of this permission
     * is the default, so a new user is confined to their own branch until someone decides otherwise.
     */
    public static final String VIEW_ALL_BRANCHES = "VIEW_ALL_BRANCHES";

    // Stock take — counting is a designated duty, and the person who counts must not be the
    // person who approves their own variances.
    public static final String READ_STOCK_TAKE = "READ_STOCK_TAKE";
    public static final String PERFORM_STOCK_TAKE = "PERFORM_STOCK_TAKE";
    public static final String APPROVE_STOCK_TAKE = "APPROVE_STOCK_TAKE";

    public static final String READ_ASSET = "READ_ASSET";
    public static final String CREATE_ASSET = "CREATE_ASSET";
    public static final String DELETE_ASSET = "DELETE_ASSET";
    public static final String UPDATE_ASSET = "UPDATE_ASSET";

    public static final String APPROVE_REQUEST = "APPROVE_REQUEST";
    public static final String REJECT_REQUEST = "REJECT_REQUEST";
    public static final String ISSUE_ITEMS = "ISSUE_ITEMS";
    public static final String ACKNOWLEDGE_REQUEST = "ACKNOWLEDGE_REQUEST";
    public static final String APPROVE_ISSUANCE = "APPROVE_ISSUANCE";
}

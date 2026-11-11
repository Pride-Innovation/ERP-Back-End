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
    /**
     * Changing your own password.
     *
     * <p>Matched ahead of {@link #USER_ROUTE} because it is a POST under {@code /users/**} and
     * therefore inherited {@code CREATE_USER} — measured, **22 of 27 accounts could not change their
     * own password**. The handler resolves the account from the JWT and can only ever touch the
     * caller's own record, so authentication is not a weaker guard here; it is the whole question.
     */
    public static final String CHANGE_PASSWORD_ROUTE = GENERAL_ROUTE + "/users/change-password";

    /**
     * Enable / disable / block / unblock / return-from-leave.
     *
     * <p>Also POSTs under {@code /users/**}, so they answered to {@code CREATE_USER}: blocking
     * somebody who already exists is not a creation, and the two are not the same decision — one
     * adds a member of staff, the other takes away their access today.
     */
    public static final String USER_ACCESS_ROUTES = GENERAL_ROUTE + "/users/*/disable";
    public static final String USER_ACCESS_ENABLE = GENERAL_ROUTE + "/users/*/enable";
    public static final String USER_ACCESS_BLOCK = GENERAL_ROUTE + "/users/*/block";
    public static final String USER_ACCESS_UNBLOCK = GENERAL_ROUTE + "/users/*/unblock";
    public static final String USER_ACCESS_LEAVE = GENERAL_ROUTE + "/users/*/return-from-leave";

    /**
     * Your own profile picture.
     *
     * <p>PUTs under {@code /users/**}, so they demanded {@code UPDATE_USER} — which **5 of 27
     * accounts hold**. Setting your own photograph is not administering the staff directory, and it
     * is the same accident as the password endpoint: a self-service action inheriting an
     * administrative permission from where it happens to sit in the URL space.
     *
     * <p>Still scoped in the service, so this does not become a way to change a colleague's picture:
     * {@code requireUserInScope} runs on both, and the page only offers the control on your own
     * profile.
     */
    /**
     * Your own record. Matched ahead of {@link #USER_ROUTE} so that seeing your own profile does not
     * require {@code READ_USER} — which pulled the Users page and {@code READ_ROLE} in behind it.
     */
    public static final String OWN_PROFILE_ROUTE = GENERAL_ROUTE + "/users/me";

    /**
     * The staff picker behind every "choose a person" dropdown in the application.
     *
     * <p>Matched ahead of {@link #USER_ROUTE} because applying for leave otherwise required
     * {@code READ_USER} — the form loaded the whole staff directory to fill one dropdown.
     */
    public static final String STAFF_PICKER_ROUTE = GENERAL_ROUTE + "/users/picker";

    public static final String USER_IMAGE_ROUTE = GENERAL_ROUTE + "/users/image/*";
    public static final String USER_IMAGE_REMOVE_ROUTE = GENERAL_ROUTE + "/users/remove/image/*";

    public static final String ROLE_ROUTE = GENERAL_ROUTE + "/roles/**";
    public static final String PERMISSION_ROUTE = GENERAL_ROUTE + "/permissions/**";
    public static final String AUTH_ROUTE = GENERAL_ROUTE + "/auth/login";
    public static final String LOGOUT = GENERAL_ROUTE + "/auth/logout";
    public static final String REFRESH_TOKEN_ROUTE = GENERAL_ROUTE + "/auth/refresh-token";
    /** Public: the login page asks it before anyone is signed in. Says LDAP or LOCAL, nothing else. */
    public static final String AUTH_MODE_ROUTE = GENERAL_ROUTE + "/auth/mode";
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
     * Asset sub-routes that need a permission of their own.
     *
     * Each must be registered BEFORE the ASSET_ROUTE verb rules in SecurityConfiguration — matchers
     * are evaluated in order and the first match wins, so a generic POST /assets/** rule placed
     * first would swallow every one of these.
     */
    public static final String ASSET_REASSIGN_ROUTE = GENERAL_ROUTE + "/assets/reassign/**";
    public static final String ASSET_REPAIR_ROUTE = GENERAL_ROUTE + "/assets/repairs/**";
    public static final String ASSET_TO_STORE_ROUTE = GENERAL_ROUTE + "/assets/store/**";
    public static final String ASSET_BULK_INSERT_ROUTE = GENERAL_ROUTE + "/assets/bulk-insert";
    public static final String ASSET_IMPORT_TEMPLATE_ROUTE = GENERAL_ROUTE + "/assets/import-template/**";
    public static final String ASSET_STATISTICS_ROUTE = GENERAL_ROUTE + "/assets/statistics";

    /*
     * Disposal lives under /movements, not /assets — it raises a DISPOSAL_TRANSFER rather than
     * flipping a flag. Nothing under /movements/** matched any rule, so it fell through to
     * `.anyRequest().authenticated()`: any signed-in user could dispose of any asset.
     */
    public static final String MOVEMENT_DISPOSAL_ROUTE = GENERAL_ROUTE + "/movements/disposal";
    public static final String MOVEMENT_DISPOSAL_PREVIEW_ROUTE = GENERAL_ROUTE + "/movements/disposal/preview";

    /** Whole-register extract. Bypassed READ_ASSET entirely until it was matched here. */
    public static final String EXPORT_ASSETS_ROUTE = GENERAL_ROUTE + "/export/assets";

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

    /**
     * Lets its holder decide which columns each table puts into its exported PDF / Excel.
     *
     * <p>A formatting right, not a disclosure one. Every column stays visible on screen to everyone
     * who can open the table, and the file is still assembled in the browser from rows it already
     * holds — this only decides what is worth printing, so a register running to eighteen columns
     * can come out as the six people actually read.
     *
     * <p>Separate from {@code UPDATE_SETTING} because it is a far smaller thing to hand out: tidying
     * reports is an everyday editorial job, while UPDATE_SETTING reaches branches, titles, units and
     * the approval workflows. Somebody who should be allowed the first should not need the second.
     */
    public static final String UPDATE_EXPORT_COLUMNS = "UPDATE_EXPORT_COLUMNS";
    /** May raise a request to restock the branch store, rather than for oneself (V30). */
    public static final String RESTOCK_STORE = "RESTOCK_STORE";
    /**
     * May hand stock from a store straight to a person with no request behind it - the exception
     * path (Phase 3). Always with a recorded reason. Ordinary giving goes through a request.
     */
    public static final String ISSUE_WITHOUT_REQUEST = "ISSUE_WITHOUT_REQUEST";

    /**
     * Enabling, disabling, blocking and unblocking an account, and returning somebody from leave.
     *
     * <p>Carved out of {@code CREATE_USER}, which these inherited only because they are POSTs under
     * {@code /users/**}. Creating a member of staff and suspending one are different decisions — the
     * second is the one taken in a hurry, at the moment somebody leaves or is suspended, and it
     * should be grantable to whoever handles that without also handing them the ability to mint
     * accounts.
     */
    public static final String MANAGE_USER_ACCESS = "MANAGE_USER_ACCESS";

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
    /**
     * Reading an asset's chain of custody: every holder, every date, every status change.
     *
     * <p>Split from {@code READ_ASSET} because they answer different questions. Seeing what an asset
     * is, and seeing everyone who has ever had it, are separate disclosures — the second is the one
     * that says where a named member of staff was working and what they were issued.
     *
     * <p>Both trail permissions are <b>narrowing</b>: the asset's own branch scope still applies on
     * top, so holding this grants the trail of assets already within reach, never more.
     */
    public static final String READ_ASSIGNMENT_HISTORY = "READ_ASSIGNMENT_HISTORY";

    /**
     * Reading an asset's repair and maintenance history — what failed, when, and at what cost.
     *
     * <p>Separate from {@link #REPAIR_ASSET}, which is performing the repair. Reading the record and
     * doing the work are different duties and are routinely held by different people.
     */
    public static final String READ_REPAIR_HISTORY = "READ_REPAIR_HISTORY";

    public static final String VIEW_ALL_BRANCHES = "VIEW_ALL_BRANCHES";

    /*
     * ── Dashboard permissions ───────────────────────────────────────────────
     *
     * Separate from the READ_* permissions above, and deliberately so.
     *
     * READ_ASSET answers "may you open the assets page". The dashboard was borrowing it to answer a
     * different question — "how much of the estate may you see summarised" — and the two are not the
     * same. An officer needs READ_ASSET to open his own asset's detail page, and that same grant was
     * making his dashboard show the whole branch's totals.
     *
     * So the dashboard gets two axes of its own:
     *
     *   SUBJECT — which widgets appear at all (DASH_VIEW_*)
     *   SCOPE   — how far each one reaches (DASH_SCOPE_*)
     *
     * A widget renders where the viewer holds its subject, and every query behind it is narrowed to
     * the viewer's scope. That replaces five different ad-hoc scoping rules — one per widget — with
     * one, and lets "Officer" mean different things in a branch and in the Admin Unit without a code
     * change.
     */

    /** Asset widgets: the KPI band, Assets by Category, Asset Condition, the branch heatmap. */
    public static final String DASH_VIEW_ASSETS = "DASH_VIEW_ASSETS";
    /** Request widgets: the work queue, my requests, request fulfilment. */
    public static final String DASH_VIEW_REQUESTS = "DASH_VIEW_REQUESTS";
    /** Stock widgets: the stocking trend. */
    public static final String DASH_VIEW_STOCK = "DASH_VIEW_STOCK";
    /** Movement widgets: consignments and transfers in flight. */
    public static final String DASH_VIEW_MOVEMENTS = "DASH_VIEW_MOVEMENTS";

    /*
     * Scope is a ladder, not a set: the highest one held wins, so granting BRANCH to someone who
     * already has SELF simply widens them. Holding none is not an error — see DashboardScopeService,
     * which falls back to the pre-existing signals so that an estate with no dashboard permissions
     * configured yet behaves exactly as it did before.
     */

    /*
     * ── Cross-branch multipliers ────────────────────────────────────────────
     *
     * Each widens the reach of permissions the holder ALREADY has; none grants anything on its own.
     * VIEW_ALL_BRANCH_ASSETS without READ_ASSET permits nothing, and MANAGE_ALL_BRANCH_ASSETS
     * without UPDATE_ASSET edits nothing. That is what keeps them from ever contradicting the base
     * permissions — they extend where those apply, never what they allow.
     *
     * Two per subject rather than one per verb: eight names instead of the twenty a verb-by-verb
     * scheme would need, while still expressing "may see every branch's assets, may edit only its
     * own".
     *
     * These are what a unit role carries. A branch user should never hold one — a branch is confined
     * to its own data by default, and Head Office's Admin and Infra units are the exception.
     */

    /** Read assets across every branch. Widens READ_ASSET and the asset read endpoints. */
    public static final String VIEW_ALL_BRANCH_ASSETS = "VIEW_ALL_BRANCH_ASSETS";
    /** Act on assets across every branch. Widens UPDATE_/DELETE_/REASSIGN_/DISPOSE_ASSET and friends. */
    public static final String MANAGE_ALL_BRANCH_ASSETS = "MANAGE_ALL_BRANCH_ASSETS";

    /**
     * Read requests across every branch.
     *
     * <p>Deliberately not granted to the units by default. A branch request reaches Admin through
     * the workflow — when a step routes to the unit, {@code Request.currentUnit} is set and every
     * member sees it. That is "visible when it reaches you". This permission makes every branch
     * request visible from the moment it is raised, which is easy to add from Settings later and
     * much harder to walk back once people rely on it.
     */
    public static final String VIEW_ALL_BRANCH_REQUESTS = "VIEW_ALL_BRANCH_REQUESTS";
    public static final String MANAGE_ALL_BRANCH_REQUESTS = "MANAGE_ALL_BRANCH_REQUESTS";

    public static final String VIEW_ALL_BRANCH_MOVEMENTS = "VIEW_ALL_BRANCH_MOVEMENTS";
    public static final String MANAGE_ALL_BRANCH_MOVEMENTS = "MANAGE_ALL_BRANCH_MOVEMENTS";

    public static final String VIEW_ALL_BRANCH_INVENTORY = "VIEW_ALL_BRANCH_INVENTORY";
    public static final String MANAGE_ALL_BRANCH_INVENTORY = "MANAGE_ALL_BRANCH_INVENTORY";

    /**
     * Cross-branch reach over <b>people</b> — the staff directory, and the records behind it.
     *
     * <p>The directory used to borrow {@code VIEW_ALL_BRANCH_ASSETS} to answer this, because there
     * was no people axis. That conflated two different disclosures: granting somebody cross-branch
     * reach over the *asset register* silently gave them every member of staff's duty station,
     * department, title and reporting line as well. The permission model keeps subjects apart
     * precisely so that one grant cannot quietly imply another.
     *
     * <p><b>Migration-safe, by design.</b> Nothing holds these on the day they ship, so the resolver
     * treats the asset answer as a fallback: PEOPLE at ALL <em>or</em> ASSETS at ALL means ALL. The
     * new axis therefore only ever widens until somebody grants it, and the fallback can be removed
     * once it has been. Without that, Head Office would have lost sight of branch staff overnight and
     * cross-branch asset assignment would have stopped working.
     */
    public static final String VIEW_ALL_BRANCH_PEOPLE = "VIEW_ALL_BRANCH_PEOPLE";
    public static final String MANAGE_ALL_BRANCH_PEOPLE = "MANAGE_ALL_BRANCH_PEOPLE";

    /** Role attached to the Admin Unit. Not a title role — see UnitRoleSeeder. */
    public static final String ADMIN_UNIT_ROLE = "ADMIN_UNIT";
    /** Role attached to the Infra Unit. */
    public static final String INFRA_UNIT_ROLE = "INFRA_UNIT";

    /** Only your own records — what you hold and what you asked for. The floor. */
    public static final String DASH_SCOPE_SELF = "DASH_SCOPE_SELF";
    /** Everything at your duty station. */
    public static final String DASH_SCOPE_BRANCH = "DASH_SCOPE_BRANCH";
    /** Every branch and Head Office, with a branch selector to focus one at a time. */
    public static final String DASH_SCOPE_ALL = "DASH_SCOPE_ALL";

    // Stock take — counting is a designated duty, and the person who counts must not be the
    // person who approves their own variances.
    public static final String READ_STOCK_TAKE = "READ_STOCK_TAKE";
    public static final String PERFORM_STOCK_TAKE = "PERFORM_STOCK_TAKE";
    public static final String APPROVE_STOCK_TAKE = "APPROVE_STOCK_TAKE";

    public static final String READ_ASSET = "READ_ASSET";
    public static final String CREATE_ASSET = "CREATE_ASSET";
    public static final String DELETE_ASSET = "DELETE_ASSET";
    public static final String UPDATE_ASSET = "UPDATE_ASSET";

    /*
     * Asset lifecycle actions.
     *
     * Split out of CREATE_ASSET / UPDATE_ASSET because the route rules match on the HTTP verb, so
     * every POST under /assets/** collapsed into one permission: whoever could register an asset
     * could also reassign it to another officer, book it for repair and import a spreadsheet of
     * several thousand, and whoever could edit a field could hand the asset into a store. Those are
     * different duties held by different people, and disposal in particular is the one action that
     * takes an asset off the register for good.
     */
    public static final String REASSIGN_ASSET = "REASSIGN_ASSET";
    public static final String REPAIR_ASSET = "REPAIR_ASSET";
    public static final String RECEIVE_ASSET_IN_STORE = "RECEIVE_ASSET_IN_STORE";
    public static final String DISPOSE_ASSET = "DISPOSE_ASSET";
    public static final String IMPORT_ASSET = "IMPORT_ASSET";
    /** Bulk extraction of the register. Audited, because it is data leaving the building. */
    public static final String EXPORT_ASSET = "EXPORT_ASSET";

    /*
     * Movements and consignments — the module that physically moves stock between buildings.
     *
     * Every endpoint here fell through to `.anyRequest().authenticated()` until 2026-08-27, so any
     * signed-in user could raise a transfer, send stock out of a building, receive it, or cancel a
     * journey in flight.
     *
     * Dispatch and receive are deliberately not folded into CREATE_MOVEMENT: raising a transfer and
     * physically handing custody over are different acts by different people, and the audit trail
     * has always recorded them separately.
     */
    public static final String READ_MOVEMENT = "READ_MOVEMENT";
    public static final String CREATE_MOVEMENT = "CREATE_MOVEMENT";
    public static final String DISPATCH_MOVEMENT = "DISPATCH_MOVEMENT";
    public static final String RECEIVE_MOVEMENT = "RECEIVE_MOVEMENT";
    public static final String CANCEL_MOVEMENT = "CANCEL_MOVEMENT";
    /**
     * Approving or rejecting a movement — and authorising the approval <b>bypass</b>.
     *
     * <p>The bypass is not an endpoint. It is the {@code proceedWithoutApproval} flag on the create
     * payload, which lets a movement proceed when no approver resolves on the initiator's reporting
     * line. It is the one action that moves stock with no approval at all, it is audited CRITICAL,
     * and it was previously available to anyone who could create a movement — which, before this,
     * was everyone. Enforced in {@code MovementApprovalService#startApproval}, the single funnel
     * every create path passes through.
     */
    public static final String APPROVE_MOVEMENT = "APPROVE_MOVEMENT";

    public static final String APPROVE_REQUEST = "APPROVE_REQUEST";
    public static final String REJECT_REQUEST = "REJECT_REQUEST";
    public static final String ISSUE_ITEMS = "ISSUE_ITEMS";
    public static final String ACKNOWLEDGE_REQUEST = "ACKNOWLEDGE_REQUEST";
    public static final String APPROVE_ISSUANCE = "APPROVE_ISSUANCE";
}

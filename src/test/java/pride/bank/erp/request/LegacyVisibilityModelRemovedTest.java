package pride.bank.erp.request;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The role-name visibility model is gone, and must stay gone.
 *
 * <p>What was deleted: a switch on {@code title.role.name} that decided whose requests you could
 * see. It could not express what the bank needs. It read only {@code title.role}, so additional
 * roles were invisible to it. It knew nothing about a duty station, so "Officer" had to mean the
 * same thing in a branch and in the Admin Unit. Two of its branches — {@code ADMIN_OFFICER} and
 * {@code ADMIN_MANAGER} — keyed off role names that are not seeded anywhere, so the code meant to
 * make an Admin Unit officer superior never executed at all. And its {@code default} returned an
 * empty requester list, which means <em>no restriction</em>: every role the switch did not name saw
 * every request in the bank, and adding a role granted it full visibility until someone remembered
 * to add a case.
 *
 * <p>Reach is now one question answered in one place, by {@code AccessScopeService}, for requests
 * and assets and writes alike.
 *
 * <p>A reflection test rather than a behavioural one because what is being guarded is an absence.
 * Nobody can write a query against a method that no longer exists, but someone restoring "the old
 * behaviour" during a debugging session is exactly how a model like this comes back — and it would
 * come back silently, since it fails open.
 */
class LegacyVisibilityModelRemovedTest {

    /** Each of these decided visibility by role name. None may return. */
    private static final List<String> DELETED_METHODS = List.of(
            "determineUserRole",
            "legacyRequestersList",
            "canSeeBranchStationaryRequests",
            "canSeeBranchNonStationaryRequests",
            "determineIfUserIsAdmin",
            "hasExplicitScope",
            "seesEveryBranch");

    @Test
    void theRoleNameSwitchIsGone() {
        List<String> found = Arrays.stream(RequestService.class.getDeclaredMethods())
                .map(Method::getName)
                .filter(DELETED_METHODS::contains)
                .distinct()
                .toList();

        assertThat(found)
                .as("these decided who sees what by role name; reach now comes from AccessScopeService")
                .isEmpty();
    }

    /**
     * The search criteria the deleted helpers populated.
     *
     * <p>{@code isAdmin} drove an "admins see everyone else's non-draft requests" predicate keyed off
     * a role name nobody holds. The two stationery flags predate the workflow engine and were the
     * only way an admin or a BOM got sight of a branch request; once the engine could route a
     * request to a named approver they actively hid assigned requests from that approver, because
     * neither side of {@code requester.branch = HO OR isBranchStationery = true} is true for, say, a
     * branch Computers request sitting with a BOM.
     *
     * <p>Both also reached Head Office by the literal id {@code 1L} and walked a dotted path
     * ({@code requester.branch.id}) that JPA compiles to an INNER JOIN — two of this codebase's
     * recorded traps in one predicate.
     */
    @Test
    void theCriteriaTheyPopulatedAreGone() {
        List<String> found = Arrays.stream(RequestSearchCriteria.class.getDeclaredFields())
                .map(Field::getName)
                .filter(List.of("isAdmin", "isBranchStationery", "isBranchNonStationery")::contains)
                .toList();

        assertThat(found)
                .as("a filter field that nothing populates is a predicate waiting to be re-enabled")
                .isEmpty();
    }

    /**
     * The request's own stationery flags are data, not a visibility rule, and must survive.
     *
     * <p>Same names, different thing — which is exactly why this is pinned. {@code Request} records
     * what kind of request it is at creation, and {@code RequestReportService} routes on it. Deleting
     * these while clearing out the identically-named filter fields would break workflow routing with
     * nothing to say why.
     */
    @Test
    void theRequestsOwnStationeryFlagsSurvive() {
        List<String> fields = Arrays.stream(Request.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        assertThat(fields)
                .as("these are what the request IS, not who may see it — the workflow routes on them")
                .contains("isBranchStationery", "isBranchNonStationery");
    }
}

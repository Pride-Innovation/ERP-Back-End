package pride.bank.erp.user;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The user model's access rules, pinned where they are easiest to erode.
 *
 * <p>Most of these are source assertions rather than behavioural ones, deliberately: what they are
 * worth is that the guards are <em>called at all</em>, and standing up two users in two branches with
 * a bound JWT would pin the fixture far more than the rule. The password policy is pure and is tested
 * as such.
 */
class UserAccessRulesTest {

    private static final String SERVICE = "src/main/java/pride/bank/erp/user/UserService.java";
    private static final String SECURITY = "src/main/java/pride/bank/erp/config/SecurityConfiguration.java";

    /**
     * Changing your own password must not need the permission that creates accounts.
     *
     * <p>Measured when this was found: the handler resolves the account from the JWT, so it can only
     * ever touch the caller's own record — and it sat under the `/users/**` POST matcher, so it asked
     * for `CREATE_USER`. **22 of 27 accounts could not change their own password.**
     */
    @Test
    void changingYourOwnPasswordNeedsOnlyAuthentication() {
        String security = read(SECURITY);

        assertThat(security)
                .as("the self-service path must have its own matcher")
                .contains("CHANGE_PASSWORD_ROUTE")
                .contains("authenticated()");

        assertThat(security.indexOf("CHANGE_PASSWORD_ROUTE"))
                .as("and it must come BEFORE the generic /users/** POST rule — the first match wins, "
                        + "which is how a reject-only role once found itself unable to reject")
                .isLessThan(security.indexOf("USER_ROUTE).hasAuthority(CREATE_USER)"));
    }

    /** The account actions are their own right, not a side effect of being POSTs. */
    @Test
    void theAccountActionsHaveTheirOwnPermission() {
        String security = read(SECURITY);

        assertThat(security)
                .as("enable / disable / block / unblock / return-from-leave answered to CREATE_USER "
                        + "only because of where they sit in the URL space")
                .contains("MANAGE_USER_ACCESS");

        assertThat(security.indexOf("MANAGE_USER_ACCESS"))
                .as("also before the generic POST rule, or it never runs")
                .isLessThan(security.indexOf("USER_ROUTE).hasAuthority(CREATE_USER)"));

        assertThat(read("src/main/java/pride/bank/erp/config/DataInitializer.java"))
                .as("a permission created outside initializePermissions() never reaches the super "
                        + "administrator, silently and forever")
                .contains("ensurePermission(MANAGE_USER_ACCESS)");
    }

    /**
     * And nobody is narrowed on the day it ships.
     *
     * <p>`READ_STOCK_TAKE` is the cautionary case: created in `initializePermissions()`, granted by
     * no seeder, so the page was offered to people every one of whose endpoints refused them. A new
     * permission with no backfill takes the action away from everyone at once.
     */
    @Test
    void theNewPermissionIsBackfilledOnceOntoTodaysHolders() {
        String seeder = read("src/main/java/pride/bank/erp/config/UserAccessPermissionSeeder.java");

        assertThat(seeder)
                .as("existing holders of CREATE_USER keep the actions they had yesterday")
                .contains("CREATE_USER");
        assertThat(seeder)
                .as("and the permission row's own absence is the marker, so it runs exactly once — "
                        + "a re-applying seeder makes revocation impossible")
                .contains("findByName(MANAGE_USER_ACCESS).isPresent()")
                .contains("return;");

        String init = read("src/main/java/pride/bank/erp/config/DataInitializer.java");
        assertThat(init.indexOf("userAccessPermissionSeeder.seedUserAccessPermission()"))
                .as("before initializePermissions(), which would otherwise create the row and erase "
                        + "the marker — backfilling nobody")
                .isLessThan(init.indexOf("List<Permission> permissions = initializePermissions()"));
    }

    /** Every by-id path goes through the scope check, not the bare finder. */
    @Test
    void everyByIdPathIsScoped() {
        String service = read(SERVICE);

        assertThat(service)
                .as("the listing was scoped and everything reached by id was not — the same gap "
                        + "assets had before requireAssetInScope")
                .contains("requireUserInScope")
                .contains("findUserInScope");

        for (String method : new String[] {
                "updateUser", "deleteUser", "blockUser", "enableUser", "unblockUser",
                "returnUserFromLeave", "uploadUserImage", "removeUserImage" }) {
            assertThat(methodBody(service, method))
                    .as("%s must resolve its target through the scoped finder", method)
                    .contains("findUserInScope");
        }
    }

    /**
     * The search endpoint was the way around the directory's scoping.
     *
     * <p>24 of 27 accounts hold `READ_USER`; the listing was narrowed and this went straight to the
     * repository, so the same disclosure was one endpoint away. Scoping a listing and leaving its
     * search open is the easiest version of this mistake to make, because the two look like separate
     * features and are the same data.
     */
    @Test
    void theSearchEndpointIsScopedToo() {
        assertThat(methodBody(read(SERVICE), "searchUserByEmailNameStaffNumber"))
                .contains("resolveVisibleBranch");

        assertThat(read("src/main/java/pride/bank/erp/user/UserRepository.java"))
                .as("and its branch predicate must be an explicit LEFT JOIN — a dotted path would "
                        + "inner-join through a nullable association and drop branchless accounts in "
                        + "the FROM clause")
                .contains("LEFT JOIN u.branch b");
    }

    /** An administrator must not be able to lock the bank out of its own user management. */
    @Test
    void theLastWayInIsProtected() {
        String body = methodBody(read(SERVICE), "requireNotLastWayIn");

        assertThat(body)
                .as("disabling or blocking your own account leaves nobody able to undo it")
                .contains("getId().equals(target.getId())");
        assertThat(body)
                .as("and the count must ignore accounts that are themselves disabled or blocked — "
                        + "one of those cannot rescue anybody")
                .contains("isEnabled")
                .contains("isAccountNonLocked")
                .contains("isBlocked");

        String service = read(SERVICE);
        assertThat(methodBody(service, "deleteUser")).contains("requireNotLastWayIn");
        assertThat(methodBody(service, "blockUser")).contains("requireNotLastWayIn");
    }

    /** A password change is the event "who, and when" is asked about most. */
    @Test
    void aPasswordChangeIsRecorded() {
        assertThat(methodBody(read(SERVICE), "changePassword"))
                .as("block, enable and disable all write an audit row; this wrote none")
                .contains("PASSWORD_CHANGED");
    }

    // ── the policy, which is pure and can be tested as such ──────────────────

    @Test
    void thePolicyRefusesWhatItSaysItRefuses() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Ab1!"))
                .hasMessageContaining("at least " + PasswordPolicy.MIN_LENGTH);

        assertThatThrownBy(() -> PasswordPolicy.validate("abcdefghijkl"))
                .as("one character class is not three")
                .hasMessageContaining("three of");

        assertThatThrownBy(() -> PasswordPolicy.validate(" Str0ngEnough! "))
                .as("a trailing space is invisible and becomes a lockout nobody can diagnose")
                .hasMessageContaining("space");

        assertThatThrownBy(() -> PasswordPolicy.validate(null))
                .hasMessageContaining("Enter a new password");
    }

    /** Every failure at once — otherwise a person discovers the rules one attempt at a time. */
    @Test
    void thePolicyReportsEveryFailureTogether() {
        assertThatThrownBy(() -> PasswordPolicy.validate("abc"))
                .hasMessageContaining("at least")
                .hasMessageContaining("three of");
    }

    @Test
    void thePolicyAcceptsAReasonablePassword() {
        assertThatCode(() -> PasswordPolicy.validate("Kampala2026!")).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.validate("correcthorseBattery9")).doesNotThrowAnyException();
    }

    /** Both ways of setting a password enforce it — a rule on one of two paths is not a rule. */
    @Test
    void bothPasswordPathsEnforceThePolicy() {
        assertThat(methodBody(read(SERVICE), "changePassword"))
                .contains("PasswordPolicy.validate");
        assertThat(read("src/main/java/pride/bank/erp/passwordReset/PasswordResetService.java"))
                .as("the reset link checked nothing at all — a single character was accepted")
                .contains("PasswordPolicy.validate");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String read(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    /** The text of one method, from its declaration to the next member. */
    private static String methodBody(String source, String methodName) {
        String[] lines = source.split("\n", -1);
        int first = -1;
        for (int i = 0; i < lines.length && first < 0; i++) {
            String trimmed = lines[i].trim();
            boolean declares = trimmed.startsWith("public ") || trimmed.startsWith("private ")
                    || trimmed.startsWith("protected ");
            if (declares && trimmed.contains(" " + methodName + "(")) first = i;
        }
        assertThat(first).as("method %s must be declared", methodName).isGreaterThanOrEqualTo(0);

        StringBuilder body = new StringBuilder(lines[first]).append('\n');
        for (int i = first + 1; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            boolean nextMember = trimmed.startsWith("public ") || trimmed.startsWith("private ")
                    || trimmed.startsWith("protected ") || trimmed.startsWith("@Transactional");
            if (nextMember) break;
            body.append(lines[i]).append('\n');
        }
        return body.toString();
    }

    /** Guards against the reader matching nothing and every assertion passing vacuously. */
    @Test
    void theSourceReaderActuallyFindsMethods() {
        assertThat(methodBody(read(SERVICE), "requireUserInScope"))
                .isNotBlank()
                .hasSizeGreaterThan(80);
    }
}

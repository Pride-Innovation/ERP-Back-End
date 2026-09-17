package pride.bank.erp.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pride.bank.erp.permission.PermissionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static pride.bank.erp.helper.Constants.*;

/**
 * Guards the dashboard's new permission axes.
 *
 * <p>Two properties matter. The scope ladder has to collapse several granted scopes into one answer
 * without contradicting itself, and the resolver has to survive being called with nobody signed in —
 * it reads the ambient HTTP request, which does not exist in a scheduled job, a seeder or a test,
 * and a resolver that threw there would be unusable from any of them.
 *
 * <p>The permissions must also exist as rows. Settings → Roles grants by name against the seeded
 * catalogue and refuses with "permission not found" when one is missing, so a skipped seed makes the
 * whole model unassignable with nothing wrong in the code to point at.
 */
@SpringBootTest
class DashboardScopeServiceTest {

    @Autowired
    private DashboardScopeService dashboardScopeService;

    @Autowired
    private PermissionRepository permissionRepository;

    // ── The ladder ───────────────────────────────────────────────────────────

    /** Granting a wider scope to someone holding a narrower one must widen, not conflict. */
    @Test
    void theWidestGrantedScopeWins() {
        assertThat(DashboardScope.SELF.widest(DashboardScope.BRANCH)).isEqualTo(DashboardScope.BRANCH);
        assertThat(DashboardScope.BRANCH.widest(DashboardScope.SELF)).isEqualTo(DashboardScope.BRANCH);
        assertThat(DashboardScope.BRANCH.widest(DashboardScope.ALL)).isEqualTo(DashboardScope.ALL);
        assertThat(DashboardScope.ALL.widest(DashboardScope.SELF)).isEqualTo(DashboardScope.ALL);
        // A missing counterpart must not narrow anyone.
        assertThat(DashboardScope.BRANCH.widest(null)).isEqualTo(DashboardScope.BRANCH);
    }

    /** ALL includes everything BRANCH shows, which includes everything SELF shows. */
    @Test
    void scopeIsInclusiveDownwards() {
        assertThat(DashboardScope.ALL.atLeast(DashboardScope.BRANCH)).isTrue();
        assertThat(DashboardScope.ALL.atLeast(DashboardScope.SELF)).isTrue();
        assertThat(DashboardScope.BRANCH.atLeast(DashboardScope.SELF)).isTrue();

        assertThat(DashboardScope.SELF.atLeast(DashboardScope.BRANCH)).isFalse();
        assertThat(DashboardScope.BRANCH.atLeast(DashboardScope.ALL)).isFalse();
    }

    // ── The resolver ─────────────────────────────────────────────────────────

    /**
     * With nobody signed in the resolver falls to the floor rather than throwing.
     *
     * <p>SELF is the safe answer: it shows a viewer only their own records, so failing to identify
     * the caller can never widen what they see.
     */
    @Test
    void resolvesToTheFloorWhenNobodyIsSignedIn() {
        assertThat(dashboardScopeService.effectiveScope()).isEqualTo(DashboardScope.SELF);
        assertThat(dashboardScopeService.currentUserBranchId()).isNull();
        assertThat(dashboardScopeService.currentUserId()).isNull();
    }

    /** Subject checks must degrade rather than throw. */
    @Test
    void subjectChecksDegradeWithNoUser() {
        assertThat(dashboardScopeService.canView(DASH_VIEW_ASSETS, READ_ASSET)).isFalse();
        assertThat(dashboardScopeService.canView(DASH_VIEW_REQUESTS, READ_REQUEST)).isFalse();
    }

    // ── The catalogue ────────────────────────────────────────────────────────

    @Test
    void everyDashboardPermissionIsSeeded() {
        for (String name : new String[]{
                DASH_VIEW_ASSETS, DASH_VIEW_REQUESTS, DASH_VIEW_STOCK, DASH_VIEW_MOVEMENTS,
                DASH_SCOPE_SELF, DASH_SCOPE_BRANCH, DASH_SCOPE_ALL,
        }) {
            assertThat(permissionRepository.findByName(name))
                    .as("permission '%s' must be seeded or it cannot be granted in Settings", name)
                    .isPresent();
        }
    }
}

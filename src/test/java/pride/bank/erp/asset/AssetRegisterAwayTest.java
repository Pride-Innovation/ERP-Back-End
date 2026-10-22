package pride.bank.erp.asset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pride.bank.erp.access.AccessScopeService;
import pride.bank.erp.access.ScopeAction;
import pride.bank.erp.access.ScopeSubject;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.dashboard.DashboardScope;
import pride.bank.erp.movement.MovementService;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Asset Register report: a branch also sees its own assets while they are away at Head Office.
 *
 * <p>The register scopes on where an asset is or who holds it, and on the bench neither is the
 * sender's - receipt rewrites the branch to Head Office and the transfer clears the holder. Measured
 * when fixed: 7 of the 11 assets in maintenance were Gulu's and hidden from Gulu's register.
 *
 * <p>The widening is <b>asked for</b> (the reports page asks, the assets page does not), applies at
 * BRANCH scope only, and lasts only while the asset is away - the rule the detail page and the store
 * listings already use, so every row it adds is one that opens.
 */
@SpringBootTest
@Transactional
class AssetRegisterAwayTest {

    @Autowired AssetService assetService;
    @Autowired AssetRepository assetRepository;
    @Autowired MovementService movementService;
    @Autowired AccessScopeService accessScopeService;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    @AfterEach
    void signOut() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private Set<Long> register(boolean includeAway) {
        return assetService.fetchAllAssets(5000, "DESC", 0, "id", null, null, null, null, null, null,
                        false, null, null, null, null, null, null, false, false, null, null, null, includeAway, false)
                .getContent().stream().map(AssetDAO::getId).collect(Collectors.toSet());
    }

    /** This branch's away assets, by the detail page's own rule - independent of the query under test. */
    private Set<Long> awayFrom(Long branch) {
        return assetRepository.findAll().stream()
                .filter(a -> !Boolean.TRUE.equals(a.getDeleted()) && !Boolean.TRUE.equals(a.getDisposed()))
                .filter(AssetService::isAwayFromItsBranch)
                .filter(a -> branch.equals(movementService.branchLastSentAwayFrom(a.getId())))
                .map(Asset::getId).collect(Collectors.toSet());
    }

    @Test
    void aBranchSeesItsOwnAwayAssetsWhenTheReportAsks() {
        Optional<User> viewer = branchViewerWithAwayAssets();
        if (viewer.isEmpty()) return;
        signInAs(viewer.get());
        Long own = viewer.get().getBranch().getId();

        Set<Long> plain = register(false);
        Set<Long> away = awayFrom(own);
        Set<Long> expected = new HashSet<>(plain);
        expected.addAll(away);

        Set<Long> hiddenBefore = new HashSet<>(away);
        hiddenBefore.removeAll(plain);
        System.out.printf("asset register test: branch %d has %d asset(s) away, %d hidden without the flag%n",
                own, away.size(), hiddenBefore.size());
        // Some away assets still read their branch (sent, not yet received at Head Office); the rest
        // are the ones the flag exists for.
        assertThat(hiddenBefore).as("the case the flag exists for").isNotEmpty();
        assertThat(register(true)).isEqualTo(expected);
        // Every row the report adds is one the detail page opens.
        for (Long id : away) {
            assertThatCode(() -> assetService.requireAssetReadable(id)).as("asset %d", id).doesNotThrowAnyException();
        }
    }

    @Test
    void headOfficeIsUnchangedByTheFlag() {
        Optional<User> viewer = findViewer(DashboardScope.ALL);
        if (viewer.isEmpty()) return;
        signInAs(viewer.get());
        assertThat(register(true)).isEqualTo(register(false));
    }

    /**
     * An account with no branch never gets the whole register. Depending on how its scope is granted it
     * either falls back to SELF (what it holds) or stays at BRANCH with nothing to match - which is
     * refused, since a null branch reads downstream as "no restriction".
     */
    @Test
    void anAccountWithNoBranchNeverSeesTheWholeRegister() {
        Optional<User> viewer = findViewer(DashboardScope.BRANCH);
        if (viewer.isEmpty()) return;
        User user = viewer.get();
        user.setBranch(null);
        userRepository.saveAndFlush(user);
        signInAs(user);

        DashboardScope scope = accessScopeService.scopeFor(ScopeSubject.ASSETS, ScopeAction.VIEW);
        System.out.printf("asset register test: with no branch, user %d resolves to %s%n", user.getId(), scope);
        if (scope == DashboardScope.BRANCH) {
            assertThatThrownBy(() -> register(false)).isInstanceOf(AccessDeniedException.class);
        } else {
            assertThat(register(false)).allSatisfy(id -> {
                Asset a = assetRepository.findById(id).orElseThrow();
                assertThat(a.getAssignedTo()).isNotNull();
                assertThat(a.getAssignedTo().getId()).isEqualTo(user.getId());
            });
        }
    }

    /** The BRANCH-with-no-branch case, made directly, so the refusal is exercised whatever the data. */
    @Test
    void aBranchScopedCallerWithNoBranchIsRefused() {
        Optional<User> viewer = findViewer(DashboardScope.BRANCH);
        if (viewer.isEmpty()) return;
        signInAs(viewer.get());
        AccessScopeService spy = org.mockito.Mockito.spy(accessScopeService);
        org.mockito.Mockito.doReturn(null).when(spy).resolveBranch(ScopeSubject.ASSETS, null);
        org.springframework.test.util.ReflectionTestUtils.setField(
                (Object) org.springframework.test.util.AopTestUtils.getUltimateTargetObject(assetService), "accessScopeService", spy);
        try {
            assertThatThrownBy(() -> register(false)).isInstanceOf(AccessDeniedException.class);
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(
                    (Object) org.springframework.test.util.AopTestUtils.getUltimateTargetObject(assetService), "accessScopeService", accessScopeService);
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Optional<User> branchViewerWithAwayAssets() {
        for (User u : userRepository.findAll()) {
            if (u.getBranch() == null || awayFrom(u.getBranch().getId()).isEmpty()) continue;
            if (scopeOf(u) == DashboardScope.BRANCH) return Optional.of(u);
        }
        System.out.println("asset register test: NO branch viewer with assets away - unexercised");
        return Optional.empty();
    }

    private Optional<User> findViewer(DashboardScope wanted) {
        for (User u : userRepository.findAll()) {
            if (u.getBranch() != null && scopeOf(u) == wanted) return Optional.of(u);
        }
        System.out.printf("asset register test: NO %s viewer%n", wanted);
        return Optional.empty();
    }

    private DashboardScope scopeOf(User u) {
        if (u.getAuthorities().stream().noneMatch(a -> "READ_ASSET".equals(a.getAuthority()))) return null;
        signInAs(u);
        DashboardScope scope = accessScopeService.scopeFor(ScopeSubject.ASSETS, ScopeAction.VIEW);
        signOut();
        return scope;
    }

    private void signInAs(User user) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(user));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}

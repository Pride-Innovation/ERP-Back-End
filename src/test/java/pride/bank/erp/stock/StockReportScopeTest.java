package pride.bank.erp.stock;

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
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The stock report (and the inventory listing behind it): an order belongs to the branch that placed
 * it. Head Office sees every branch and may pick one; a branch sees its own and may not name another;
 * an account with no branch sees nothing rather than everything.
 */
@SpringBootTest
@Transactional
class StockReportScopeTest {

    @Autowired StockService stockService;
    @Autowired StockRepository stockRepository;
    @Autowired AccessScopeService accessScopeService;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    @AfterEach
    void signOut() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private Set<Long> branchesListed(Long branchId) {
        return stockService.fetchAllStocks(5000, "DESC", 0, null, null, null, null, branchId, null, null, null)
                .getContent().stream()
                .map(s -> s.getBranch() == null ? null : s.getBranch().getId())
                .collect(Collectors.toSet());
    }

    @Test
    void headOfficeSeesEveryBranchAndMayPickOne() {
        Optional<User> viewer = findViewer(true);
        if (viewer.isEmpty()) return;
        signInAs(viewer.get());

        Set<Long> ordering = stockRepository.findAll().stream()
                .map(s -> s.getBranch() == null ? null : s.getBranch().getId())
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        assertThat(branchesListed(null)).containsAll(ordering);
        for (Long branch : ordering) {
            assertThat(branchesListed(branch)).as("branch %d", branch).containsOnly(branch);
        }
    }

    @Test
    void aBranchSeesOnlyItsOwnOrdersAndMayNotNameAnother() {
        Optional<User> viewer = findViewer(false);
        if (viewer.isEmpty()) return;
        signInAs(viewer.get());
        Long own = accessScopeService.currentUserBranchId();

        assertThat(branchesListed(null)).allSatisfy(b -> assertThat(b).isEqualTo(own));
        assertThatThrownBy(() -> branchesListed(own + 100000)).isInstanceOf(AccessDeniedException.class);
    }

    /**
     * The resolver answers null both for "every branch" and for "no branch on record", and null reads
     * as no restriction. Nobody lacks a branch today, so one is made here, inside the rolled-back
     * transaction.
     */
    @Test
    void anAccountWithNoBranchIsRefusedRatherThanShownEveryBranch() {
        Optional<User> viewer = findViewer(false);
        if (viewer.isEmpty()) return;
        User user = viewer.get();
        user.setBranch(null);
        userRepository.saveAndFlush(user);
        signInAs(user);

        assertThatThrownBy(() -> branchesListed(null)).isInstanceOf(AccessDeniedException.class);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Optional<User> findViewer(boolean allBranches) {
        for (User u : userRepository.findAll()) {
            if (u.getBranch() == null) continue;
            if (u.getAuthorities().stream().noneMatch(a -> "READ_INVENTORY".equals(a.getAuthority()))) continue;
            signInAs(u);
            boolean all = accessScopeService.scopeFor(ScopeSubject.INVENTORY, ScopeAction.VIEW) == DashboardScope.ALL;
            signOut();
            if (all == allBranches) return Optional.of(u);
        }
        System.out.printf("stock report test: NO %s viewer found%n", allBranches ? "all-branches" : "branch");
        return Optional.empty();
    }

    private void signInAs(User user) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(user));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}

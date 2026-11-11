package pride.bank.erp.request;

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

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The requests report's Branch filter, now on the server: the requester's branch, offered to callers
 * who see every branch. It compared branch names in the browser after the page cap before.
 */
@SpringBootTest
@Transactional
class RequestReportBranchFilterTest {

    @Autowired RequestService requestService;
    @Autowired RequestRepository requestRepository;
    @Autowired AccessScopeService accessScopeService;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    @AfterEach
    void signOut() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private Set<Long> list(Long requesterBranchId) {
        return requestService.fetchAllRequests(5000, "DESC", 0, null, null, null, null, null, null,
                        null, null, null, null, null, requesterBranchId, false, null, null, null)
                .getContent().stream().map(RequestDTO::getId).collect(Collectors.toSet());
    }

    @Test
    void headOfficeGetsExactlyTheRequestsRaisedAtTheBranchItPicks() {
        Optional<User> viewer = findViewer(true);
        if (viewer.isEmpty()) return;
        signInAs(viewer.get());

        Set<Long> everything = list(null);
        Set<Long> branches = requestRepository.findAll().stream()
                .filter(r -> everything.contains(r.getId()))
                .map(r -> r.getRequester().getBranch() == null ? null : r.getRequester().getBranch().getId())
                .filter(Objects::nonNull).collect(Collectors.toSet());
        assertThat(branches).isNotEmpty();
        for (Long branch : branches) {
            Set<Long> expected = requestRepository.findAll().stream()
                    .filter(r -> everything.contains(r.getId()))
                    .filter(r -> r.getRequester().getBranch() != null && branch.equals(r.getRequester().getBranch().getId()))
                    .map(Request::getId).collect(Collectors.toSet());
            assertThat(list(branch)).as("branch %d", branch).isEqualTo(expected);
        }
    }

    @Test
    void aBranchMayNameOnlyItsOwn() {
        Optional<User> viewer = findViewer(false);
        if (viewer.isEmpty()) return;
        signInAs(viewer.get());
        Long own = viewer.get().getBranch().getId();

        assertThat(list(null)).containsAll(list(own));
        assertThatThrownBy(() -> list(own + 100000)).isInstanceOf(AccessDeniedException.class);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Optional<User> findViewer(boolean allBranches) {
        for (User u : userRepository.findAll()) {
            if (u.getBranch() == null) continue;
            if (u.getAuthorities().stream().noneMatch(a -> "READ_REQUEST".equals(a.getAuthority()))) continue;
            signInAs(u);
            boolean all = accessScopeService.scopeFor(ScopeSubject.REQUESTS, ScopeAction.VIEW) == DashboardScope.ALL;
            signOut();
            if (all == allBranches) return Optional.of(u);
        }
        System.out.printf("request report test: NO %s viewer found%n", allBranches ? "all-branches" : "branch");
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

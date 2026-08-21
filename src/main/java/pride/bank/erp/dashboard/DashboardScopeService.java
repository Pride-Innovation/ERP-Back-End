/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.dashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.user.User;

import java.util.Set;
import java.util.stream.Collectors;

import static pride.bank.erp.helper.Constants.*;

/**
 * The one place that decides how far a dashboard query may reach.
 *
 * <p>Before this, the dashboard had six widgets and five different scoping rules: three endpoints
 * pinned to the caller's own branch with no way to widen them, one national and guarded, one
 * national and guarded by nothing at all, and a work queue scoped by a hardcoded switch on the
 * user's role name. A Head Office administrator's headline figures showed Head Office's own assets
 * rather than the bank's, and nothing on the page said so.
 *
 * <p>Every dashboard query now asks this service two questions — how far may I look, and which
 * branch am I looking at — and applies the answer the same way.
 *
 * <h2>Migrating without a blackout</h2>
 * Gating on the new permissions alone would empty every dashboard in the bank the moment this
 * deploys, because no role holds them yet. So a user with no {@code DASH_SCOPE_*} permission falls
 * back to the signals that governed them before: {@code VIEW_ALL_BRANCHES} means ALL, an
 * organisational read permission plus a duty station means BRANCH, and anything else means SELF.
 * Day-one behaviour is therefore unchanged, and the explicit model takes over for a role the moment
 * someone grants it a scope.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardScopeService {

    private final CurrentUserService currentUserService;

    /** Permissions that imply the holder is meant to see beyond their own records. */
    private static final Set<String> ORG_READ = Set.of(
            READ_ASSET, READ_REQUEST, READ_STORE, READ_INVENTORY);

    /**
     * The signed-in user, or null.
     *
     * <p>Wrapped because {@code CurrentUserService.getCurrentUser()} reads the ambient HTTP request
     * through {@code Objects.requireNonNull} and throws when there is none — which is the case in a
     * scheduled job, a seeder, or a test. A scope resolver that blew up outside a web request would
     * make itself unusable from any of those.
     */
    private User currentUserOrNull() {
        try {
            return currentUserService.getCurrentUser();
        } catch (RuntimeException e) {
            log.debug("No bound request or authenticated user while resolving dashboard scope");
            return null;
        }
    }

    private Set<String> authorities(User user) {
        if (user == null) return Set.of();
        return user.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toSet());
    }

    /**
     * The widest scope this user holds.
     *
     * <p>Explicit grants win outright. Only when none is held does the fallback apply — see the
     * class note on why that matters for the first deployment.
     */
    public DashboardScope effectiveScope() {
        User user = currentUserOrNull();
        Set<String> held = authorities(user);

        DashboardScope explicit = null;
        if (held.contains(DASH_SCOPE_ALL)) explicit = DashboardScope.ALL;
        else if (held.contains(DASH_SCOPE_BRANCH)) explicit = DashboardScope.BRANCH;
        else if (held.contains(DASH_SCOPE_SELF)) explicit = DashboardScope.SELF;
        if (explicit != null) return explicit;

        // Legacy fallback.
        if (held.contains(VIEW_ALL_BRANCHES)) return DashboardScope.ALL;
        boolean readsOrgData = held.stream().anyMatch(ORG_READ::contains);
        boolean hasStation = user != null && user.getBranch() != null;
        return (readsOrgData && hasStation) ? DashboardScope.BRANCH : DashboardScope.SELF;
    }

    /** Whether this user may see a given subject at all, with the same legacy fallback. */
    public boolean canView(String dashSubject, String legacyEquivalent) {
        Set<String> held = authorities(currentUserService.getCurrentUser());
        if (held.contains(dashSubject)) return true;
        // Only fall back when the estate has not configured this axis for anyone yet.
        boolean anySubjectConfigured = held.contains(DASH_VIEW_ASSETS)
                || held.contains(DASH_VIEW_REQUESTS)
                || held.contains(DASH_VIEW_STOCK)
                || held.contains(DASH_VIEW_MOVEMENTS);
        return !anySubjectConfigured && held.contains(legacyEquivalent);
    }

    public Long currentUserBranchId() {
        User user = currentUserOrNull();
        return (user != null && user.getBranch() != null) ? user.getBranch().getId() : null;
    }

    public Long currentUserId() {
        User user = currentUserOrNull();
        return user != null ? user.getId() : null;
    }

    /**
     * The branch a dashboard query should actually filter on.
     *
     * <p>Returning {@code null} means "no branch restriction" — the national view. Anything else is
     * a branch id to filter by.
     *
     * @param requestedBranchId the branch the page's selector is focused on, or null for the default
     * @throws AccessDeniedException when a user asks for a branch that is not theirs without ALL.
     *         Naming another branch is a deliberate act, and quietly rewriting it to the caller's own
     *         would show one branch's figures under another's name — worse than refusing.
     */
    public Long resolveBranch(Long requestedBranchId) {
        DashboardScope scope = effectiveScope();

        if (scope == DashboardScope.ALL) {
            // Null means the roll-up; a named branch focuses it.
            return requestedBranchId;
        }

        Long own = currentUserBranchId();
        if (requestedBranchId != null && !requestedBranchId.equals(own)) {
            throw new AccessDeniedException(
                    "You do not have access to another branch's dashboard figures.");
        }
        return own;
    }

    /**
     * The user whose own records a query should be limited to, or {@code null} for no such limit.
     *
     * <p>Non-null only at {@link DashboardScope#SELF}. This is the piece that lets a branch officer
     * keep {@code READ_ASSET} — so he can open his own asset's detail page — while his dashboard
     * counts only what is his, which is the distinction the old model could not express.
     */
    public Long resolveOwner() {
        return effectiveScope() == DashboardScope.SELF ? currentUserId() : null;
    }
}

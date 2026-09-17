package pride.bank.erp.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.permission.Permission;
import pride.bank.erp.role.Role;
import pride.bank.erp.title.Title;
import pride.bank.erp.user.User;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static pride.bank.erp.helper.Constants.*;

/**
 * Reach over a record that touches more than one branch.
 *
 * <p>A movement is the case this exists for. It has a source and a destination, and either end may
 * be a store's location or a person's duty station — so "which branch is this movement at" has up to
 * four answers and no movement has all four. The rule is that the caller is in reach if <b>any</b>
 * end is theirs, which is what the movements listing has always done.
 *
 * <p>That matching matters more than the rule itself. A guard that computed the branch differently
 * from the listing would show someone a row in their table that they could not then open, or worse,
 * hide one they are responsible for — and the mismatch would look like an intermittent bug rather
 * than a policy difference.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MultiBranchReachTest {

    private static final Long KAMPALA = 1L;
    private static final Long GULU = 2L;
    private static final Long MBARARA = 3L;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private AccessScopeService accessScopeService;

    private void signedInAt(Long branchId, String... permissions) {
        Set<Permission> held = Arrays.stream(permissions)
                .map(name -> Permission.builder().name(name).build())
                .collect(Collectors.toCollection(HashSet::new));

        User user = User.builder()
                .id(100L)
                .title(Title.builder()
                        .role(Role.builder().name("TEST_ROLE").permissions(held).build())
                        .build())
                .branch(branchId == null ? null : Branch.builder().id(branchId).build())
                .build();

        when(currentUserService.getCurrentUser()).thenReturn(user);
    }

    @Test
    void oneEndAtYourBranchIsEnough() {
        signedInAt(GULU, READ_INVENTORY, DASH_SCOPE_BRANCH);

        // Head Office dispatching to Gulu: the receiving storekeeper must be able to act on it.
        assertThatCode(() -> accessScopeService.requireCanManageAny(
                ScopeSubject.MOVEMENTS, List.of(KAMPALA, GULU)))
                .doesNotThrowAnyException();
    }

    @Test
    void noEndAtYourBranchIsRefused() {
        signedInAt(GULU, READ_INVENTORY, DASH_SCOPE_BRANCH);

        assertThatThrownBy(() -> accessScopeService.requireCanManageAny(
                ScopeSubject.MOVEMENTS, List.of(KAMPALA, MBARARA)))
                .as("a branch storekeeper could otherwise dispatch another branch's consignment by id")
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aMovementTouchingNoKnownBranchStaysReachable() {
        signedInAt(GULU, READ_INVENTORY, DASH_SCOPE_BRANCH);

        // Every end null — nothing resolved a location. Same treatment as an unassigned asset, and
        // for the same reason: it must not become invisible and unfixable to everyone but a
        // cross-branch holder.
        assertThatCode(() -> accessScopeService.requireCanManageAny(ScopeSubject.MOVEMENTS, List.of()))
                .doesNotThrowAnyException();
        assertThatCode(() -> accessScopeService.requireCanManageAny(ScopeSubject.MOVEMENTS, null))
                .doesNotThrowAnyException();
    }

    @Test
    void nullEndsAreIgnoredRatherThanTreatedAsABranch() {
        signedInAt(GULU, READ_INVENTORY, DASH_SCOPE_BRANCH);

        // A return has no source store and an issuance no source user, so nulls among the four ends
        // are the ordinary case, not a fault.
        assertThatThrownBy(() -> accessScopeService.requireCanManageAny(
                ScopeSubject.MOVEMENTS, Arrays.asList(null, KAMPALA, null)))
                .as("one known end, and it is not theirs")
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aUnitOfficerReachesAMovementBetweenTwoOtherBranches() {
        signedInAt(KAMPALA, READ_INVENTORY, MANAGE_ALL_BRANCH_MOVEMENTS);

        assertThatCode(() -> accessScopeService.requireCanManageAny(
                ScopeSubject.MOVEMENTS, List.of(GULU, MBARARA)))
                .doesNotThrowAnyException();
    }

    @Test
    void seeingAMovementIsNotActingOnIt() {
        signedInAt(KAMPALA, READ_INVENTORY, VIEW_ALL_BRANCH_MOVEMENTS);

        assertThatCode(() -> accessScopeService.requireCanViewAny(
                ScopeSubject.MOVEMENTS, List.of(GULU, MBARARA)))
                .as("the view multiplier is a reporting right and does its job")
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> accessScopeService.requireCanManageAny(
                ScopeSubject.MOVEMENTS, List.of(GULU, MBARARA)))
                .as("but it is not a licence to dispatch or cancel another branch's stock")
                .isInstanceOf(AccessDeniedException.class);
    }

    /**
     * Inventory reads travel through {@code BranchScopeService}, which is now a façade over this
     * resolver. The multiplier has to reach that far or the façade is still a second model.
     */
    @Test
    void theInventoryMultiplierWidensStoreAndStockReads() {
        signedInAt(KAMPALA, READ_INVENTORY, VIEW_ALL_BRANCH_INVENTORY);

        assertThatCode(() -> accessScopeService.requireCanView(ScopeSubject.INVENTORY, GULU, null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> accessScopeService.requireCanManage(ScopeSubject.INVENTORY, GULU, null))
                .as("reporting on another branch's stock is not a licence to reorganise its stores")
                .isInstanceOf(AccessDeniedException.class);
    }
}

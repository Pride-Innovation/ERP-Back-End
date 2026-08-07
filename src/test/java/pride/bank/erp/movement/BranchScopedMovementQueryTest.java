package pride.bank.erp.movement;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards {@link MovementRepository#findForBranch} against the join semantics that silently emptied it.
 *
 * <p>The query was written with dotted paths — {@code m.sourceStore.location.id = :branchId OR
 * m.destStore.location.id = :branchId OR …}. JPQL turns each of those into an INNER join, so a row
 * survived only if <i>all four</i> of sourceStore, destStore, sourceUser and recipientUser were
 * non-null. No movement is ever shaped like that: a return has no source store, a fulfilment has no
 * source user, a department transfer has no destination store. The result was zero rows for every
 * branch-scoped user, which the UI showed as an ordinary empty table.
 *
 * <p>It hid for as long as it did because anyone holding {@code VIEW_ALL_BRANCHES} takes the
 * unscoped {@code findAll} path and never executes this query at all — so it only surfaced the first
 * time an officer logged in.
 *
 * <p>These tests assert the property that matters: <b>a movement touching a branch must appear in
 * that branch's listing, whichever of its four association slots happen to be filled.</b>
 */
@SpringBootTest
class BranchScopedMovementQueryTest {

    @Autowired
    private MovementRepository movementRepository;

    @Test
    @Transactional(readOnly = true)
    void everyMovementIsVisibleToAtLeastOneBranch() {
        List<Movement> all = movementRepository.findAll(PageRequest.of(0, 50)).getContent();
        if (all.isEmpty()) return; // nothing seeded to assert against

        for (Movement m : all) {
            Long branchId = anyBranchTouchedBy(m);
            if (branchId == null) continue; // genuinely unattached at both ends

            boolean found = movementRepository
                    .findForBranch(branchId, PageRequest.of(0, 500))
                    .stream()
                    .anyMatch(found2 -> found2.getId().equals(m.getId()));

            assertThat(found)
                    .as("movement %s (%s) touches branch %s and must appear in that branch's listing",
                            m.getId(), m.getMovementType(), branchId)
                    .isTrue();
        }
    }

    /**
     * The specific shape that broke: a movement with a recipient user and <b>no destination store</b>
     * — which is now every request fulfilment, since a movement runs source → requester rather than
     * stopping at the branch store.
     */
    @Test
    @Transactional(readOnly = true)
    void aMovementEndingWithAPersonIsVisibleToTheirBranch() {
        Optional<Movement> toAPerson = movementRepository.findAll(PageRequest.of(0, 100)).stream()
                .filter(m -> m.getDestStore() == null)
                .filter(m -> m.getRecipientUser() != null && m.getRecipientUser().getBranch() != null)
                .findFirst();
        if (toAPerson.isEmpty()) return;

        Movement m = toAPerson.get();
        Long branchId = m.getRecipientUser().getBranch().getId();

        assertThat(movementRepository.findForBranch(branchId, PageRequest.of(0, 500)))
                .as("a movement delivered to a person must be visible to that person's branch")
                .anyMatch(found -> found.getId().equals(m.getId()));
    }

    /** The mirror shape: a hand-back that leaves from a person and has no source store. */
    @Test
    @Transactional(readOnly = true)
    void aMovementStartingFromAPersonIsVisibleToTheirBranch() {
        Optional<Movement> fromAPerson = movementRepository.findAll(PageRequest.of(0, 100)).stream()
                .filter(m -> m.getSourceStore() == null)
                .filter(m -> m.getSourceUser() != null && m.getSourceUser().getBranch() != null)
                .findFirst();
        if (fromAPerson.isEmpty()) return;

        Movement m = fromAPerson.get();
        Long branchId = m.getSourceUser().getBranch().getId();

        assertThat(movementRepository.findForBranch(branchId, PageRequest.of(0, 500)))
                .as("a hand-back must be visible to the branch of the person returning it")
                .anyMatch(found -> found.getId().equals(m.getId()));
    }

    /** Any branch this movement touches, at either end, by store or by person. */
    private Long anyBranchTouchedBy(Movement m) {
        if (m.getSourceStore() != null && m.getSourceStore().getLocation() != null) {
            return m.getSourceStore().getLocation().getId();
        }
        if (m.getDestStore() != null && m.getDestStore().getLocation() != null) {
            return m.getDestStore().getLocation().getId();
        }
        if (m.getSourceUser() != null && m.getSourceUser().getBranch() != null) {
            return m.getSourceUser().getBranch().getId();
        }
        if (m.getRecipientUser() != null && m.getRecipientUser().getBranch() != null) {
            return m.getRecipientUser().getBranch().getId();
        }
        return null;
    }
}

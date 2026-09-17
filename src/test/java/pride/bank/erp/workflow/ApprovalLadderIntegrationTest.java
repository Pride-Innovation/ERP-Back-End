/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.workflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.title.Title;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Exercises the {@code reportsTo} approval ladder against real persisted data.
 *
 * <p>{@link ApproverChainResolverTest} covers the routing *rules*, but it is a Mockito test that
 * hands the resolver hand-built {@link Title} objects — so it stays green regardless of how those
 * titles are actually fetched from the database. Nothing else pinned the ladder down, which matters
 * because it is the single source of truth for request, issuance and movement approval: a change to
 * the fetch strategy on {@code Title.reportsTo} (or {@code User.title}) could break approval routing
 * in production while every unit test still passed.
 *
 * <p>This walks the real chain instead: that it can be traversed at all, that it terminates rather
 * than looping, and that resolution completes without a lazy-initialisation failure.
 */
@SpringBootTest
class ApprovalLadderIntegrationTest {

    private static final int DEPTH_GUARD = 30;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApproverChainResolver approverChainResolver;

    /** A seeded user who actually has someone above them, or empty if the data has none. */
    private Optional<User> userWithLadder() {
        return userRepository.findAll().stream()
                .filter(u -> u.getTitle() != null && u.getTitle().getReportsTo() != null)
                .findFirst();
    }

    @Test
    @Transactional(readOnly = true)
    void reportsToChainIsTraversableAndTerminates() {
        Optional<User> candidate = userWithLadder();
        if (candidate.isEmpty()) {
            return; // no hierarchy seeded in this environment
        }

        Title rung = candidate.get().getTitle().getReportsTo();
        Set<Long> seen = new HashSet<>();
        int depth = 0;

        while (rung != null && depth < DEPTH_GUARD) {
            assertThat(rung.getId()).as("every rung must be a persisted title").isNotNull();
            assertThat(seen.add(rung.getId()))
                    .as("reporting line must not contain a cycle (revisited title %s)", rung.getId())
                    .isTrue();
            // Role drives whitelist matching in the resolver; a rung without one silently drops out.
            assertThat(rung.getRole()).as("rung %s must carry a role", rung.getId()).isNotNull();
            rung = rung.getReportsTo();
            depth++;
        }

        assertThat(depth).as("ladder should climb at least one tier").isGreaterThan(0);
        assertThat(depth).as("ladder must terminate well inside the resolver's depth guard").isLessThan(DEPTH_GUARD);
    }

    @Test
    @Transactional(readOnly = true)
    void initiatorChainResolvesWithoutLazyInitialisationFailure() {
        Optional<User> candidate = userWithLadder();
        if (candidate.isEmpty()) {
            return;
        }
        User initiator = candidate.get();

        assertThatCode(() -> {
            List<ResolvedApprover> chain = approverChainResolver.resolveInitiatorChain(initiator);
            assertThat(chain).isNotNull();
            // Every resolved stop must be actionable — an approver or a group mailbox, never neither,
            // and never the initiator approving their own movement.
            chain.forEach(stop -> {
                assertThat(stop.getApprover() != null || stop.getGroupEmail() != null)
                        .as("a resolved stop must name an approver or a group mailbox")
                        .isTrue();
                if (stop.getApprover() != null) {
                    assertThat(stop.getApprover().getId()).isNotEqualTo(initiator.getId());
                }
            });
        }).doesNotThrowAnyException();
    }
}

/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Runs a seeding step exactly once, ever.
 *
 * <p>For the steps that are migrations rather than reconciliations — "on the day this ships, give
 * every existing role a default scope so nobody is narrowed". Those must not re-run, because after
 * the first time the estate belongs to whoever administers it, and re-running silently reverses
 * their decisions.
 *
 * <p>The distinction is worth stating plainly, because getting it wrong is invisible: a
 * reconciliation that re-runs is correct, and a migration that re-runs looks correct too — right up
 * until someone revokes a permission, restarts, and finds it back without a word.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OneTimeSeed {

    private final SeederStateRepository seederStateRepository;

    /**
     * Runs {@code step} unless this marker has been recorded before, then records it.
     *
     * @param marker a stable name. Changing it re-runs the step, so treat it as permanent.
     * @param note   what the step did, for whoever later reads the table wondering what happened.
     * @return true when the step ran, false when it had already been applied
     */
    @Transactional
    public boolean once(String marker, String note, Runnable step) {
        if (seederStateRepository.existsById(marker)) {
            log.debug("Seed '{}' already applied — skipping", marker);
            return false;
        }

        log.info("Applying one-time seed '{}': {}", marker, note);
        step.run();

        seederStateRepository.save(SeederState.builder()
                .marker(marker)
                .appliedAt(LocalDateTime.now())
                .note(note)
                .build());
        return true;
    }
}

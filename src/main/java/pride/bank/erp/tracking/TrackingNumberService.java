/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.tracking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.user.User;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Issues tracking numbers: {@code TRK-2026-000042-7}, unique across movements and consignments.
 *
 * <h2>One number per trip</h2>
 * A tracking number identifies a physical journey: a movement dispatched on its own, or a
 * consignment, whose number every movement riding on it carries. So the registry row belongs to
 * whichever of the two is travelling.
 *
 * <h2>Issued before dispatch, not at it</h2>
 * The dispatch form prints the note the courier signs, and the signed copy is uploaded before
 * Dispatch is pressed - so the number must exist while the note is being printed. The form reserves
 * it as it opens; {@link #ensure} returns the same number however often it is asked, so reopening
 * the form or reprinting the note never spends another.
 *
 * <h2>The format</h2>
 * <ul>
 *   <li>{@code TRK} - the same everywhere;</li>
 *   <li>the year of issue, so a number reads in context at a glance;</li>
 *   <li>the registry id, six digits and growing past that if it ever needs to - one sequence shared
 *       by movements and consignments, which is what makes the two impossible to confuse;</li>
 *   <li>a Luhn check digit over the year and sequence, so a number mistyped by one digit, or with two
 *       neighbouring digits swapped, is recognisably wrong rather than silently "not found".</li>
 * </ul>
 * Sequential rather than random: short to read out, ordered, and with nothing to retry. Being
 * guessable costs nothing here - knowing a tracking number opens no screen; everything behind one is
 * permission- and branch-scoped. Dashes rather than slashes, because people type and search these
 * and a slash breaks a query string.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrackingNumberService {

    private static final Pattern FORMAT = Pattern.compile("^TRK-(\\d{4})-(\\d{6,})-(\\d)$");

    private final TrackingNumberRepository repository;
    private final CurrentUserService currentUserService;

    /**
     * The active number for this trip, issuing one if it has none. Idempotent by design.
     */
    @Transactional
    public String ensure(TrackingNumber.OwnerType ownerType, Long ownerId) {
        return repository.findFirstByOwnerTypeAndOwnerIdAndStatusOrderByIdDesc(
                        ownerType, ownerId, TrackingNumber.Status.ACTIVE)
                .map(TrackingNumber::getNumber)
                .orElseGet(() -> issue(ownerType, ownerId));
    }

    /**
     * Sets aside a movement's own number when it is loaded onto a consignment - it travels under the
     * consignment's number from then on. Kept as SUPERSEDED rather than deleted so the gap is
     * explained. Returns whether there was one.
     */
    @Transactional
    public boolean supersede(TrackingNumber.OwnerType ownerType, Long ownerId) {
        return repository.findFirstByOwnerTypeAndOwnerIdAndStatusOrderByIdDesc(
                        ownerType, ownerId, TrackingNumber.Status.ACTIVE)
                .map(row -> {
                    row.setStatus(TrackingNumber.Status.SUPERSEDED);
                    repository.save(row);
                    log.info("Tracking number {} superseded ({} {})", row.getNumber(), ownerType, ownerId);
                    return true;
                })
                .orElse(false);
    }

    private String issue(TrackingNumber.OwnerType ownerType, Long ownerId) {
        User issuer = currentUserService.getCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        // Insert first: the id the database assigns is the sequence. The number is written straight
        // after, inside the same transaction, so nothing ever reads a row without one.
        TrackingNumber row = repository.saveAndFlush(TrackingNumber.builder()
                .ownerType(ownerType)
                .ownerId(ownerId)
                .issuedAt(now)
                .issuedBy(issuer != null ? issuer.getId() : null)
                .build());
        row.setNumber(format(now.getYear(), row.getId()));
        repository.save(row);
        log.info("Issued tracking number {} to {} {}", row.getNumber(), ownerType, ownerId);
        return row.getNumber();
    }

    // ── Format ───────────────────────────────────────────────────────────────

    /** {@code TRK-<year>-<sequence, at least six digits>-<Luhn check digit>}. */
    public static String format(int year, long sequence) {
        String seq = String.format("%06d", sequence);
        return "TRK-" + year + "-" + seq + "-" + luhnCheckDigit(year + seq);
    }

    /**
     * Whether a string is a well-formed tracking number with a valid check digit. False for typed
     * numbers from before the system issued them - those are not in this format at all.
     */
    public static boolean isValid(String candidate) {
        if (candidate == null) return false;
        var m = FORMAT.matcher(candidate.trim().toUpperCase());
        if (!m.matches()) return false;
        return luhnCheckDigit(m.group(1) + m.group(2)) == Integer.parseInt(m.group(3));
    }

    /**
     * The Luhn digit to append to {@code payload}: from the right, every second digit starting with
     * the last is doubled (less 9 when that passes 9), and the digit brings the sum to a multiple of
     * ten. Catches every single-digit error and nearly every swap of neighbouring digits.
     */
    static int luhnCheckDigit(String payload) {
        int sum = 0;
        boolean dbl = true;
        for (int i = payload.length() - 1; i >= 0; i--) {
            int d = payload.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            dbl = !dbl;
        }
        return (10 - (sum % 10)) % 10;
    }
}

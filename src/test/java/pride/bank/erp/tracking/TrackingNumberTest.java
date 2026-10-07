package pride.bank.erp.tracking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.consignment.ConsignmentDispatchDTO;
import pride.bank.erp.movement.MovementDispatchDTO;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tracking numbers are issued by the system, once per trip, and are unique.
 *
 * <p>They were free text typed at dispatch - any format, nothing stopping two journeys sharing one.
 * Rolled back throughout; the owner ids used are far beyond any real row, since only the registry
 * is exercised here.
 */
@SpringBootTest
@Transactional
class TrackingNumberTest {

    @Autowired TrackingNumberService service;
    @Autowired TrackingNumberRepository repository;

    private static final long FAR_AWAY = 9_000_000_000L;

    // ── The format ───────────────────────────────────────────────────────────

    @Test
    void theFormatIsYearSequenceAndCheckDigit() {
        String number = TrackingNumberService.format(2026, 42);

        // A fixed vector, asserted by the browser's trackingNumber.test.ts as well, so the two copies
        // of the check digit cannot drift apart.
        assertThat(number).isEqualTo("TRK-2026-000042-5");
        assertThat(TrackingNumberService.isValid(number)).isTrue();
    }

    /** The point of the check digit: a one-digit slip is recognisably wrong, not merely "not found". */
    @Test
    void everySingleDigitMistakeIsCaught() {
        String number = TrackingNumberService.format(2026, 4217);
        String digits = number.replace("TRK-", "").replace("-", "");

        for (int i = 0; i < digits.length(); i++) {
            for (char d = '0'; d <= '9'; d++) {
                if (d == digits.charAt(i)) continue;
                String typo = digits.substring(0, i) + d + digits.substring(i + 1);
                String candidate = "TRK-" + typo.substring(0, 4) + "-" + typo.substring(4, typo.length() - 1)
                        + "-" + typo.charAt(typo.length() - 1);
                assertThat(TrackingNumberService.isValid(candidate))
                        .as("%s should be rejected as a mistyping of %s", candidate, number)
                        .isFalse();
            }
        }
    }

    @Test
    void swappingTwoNeighbouringDigitsIsCaught() {
        // 0 and 9 swapped is the one neighbour swap Luhn cannot see; every other pair is caught.
        String number = TrackingNumberService.format(2026, 123456);
        String seq = "123456";
        String swapped = "213456";
        assertThat(TrackingNumberService.isValid(number.replace(seq, swapped))).isFalse();
    }

    @Test
    void numbersTypedBeforeTheSystemIssuedThemAreSimplyNotInTheFormat() {
        assertThat(TrackingNumberService.isValid("TRK-463728")).isFalse();
        assertThat(TrackingNumberService.isValid(null)).isFalse();
    }

    // ── Issuing ──────────────────────────────────────────────────────────────

    /** Reopening the dispatch form or reprinting the note must not spend another number. */
    @Test
    void askingTwiceForTheSameTripReturnsTheSameNumber() {
        String first = service.ensure(TrackingNumber.OwnerType.MOVEMENT, FAR_AWAY + 1);
        String again = service.ensure(TrackingNumber.OwnerType.MOVEMENT, FAR_AWAY + 1);

        assertThat(again).isEqualTo(first);
        assertThat(TrackingNumberService.isValid(first)).isTrue();
    }

    /** One sequence for both kinds of trip, so a movement and a consignment can never share a number. */
    @Test
    void differentTripsAlwaysGetDifferentNumbers() {
        Set<String> issued = new HashSet<>();
        for (int i = 0; i < 25; i++) {
            issued.add(service.ensure(TrackingNumber.OwnerType.MOVEMENT, FAR_AWAY + 100 + i));
            issued.add(service.ensure(TrackingNumber.OwnerType.CONSIGNMENT, FAR_AWAY + 100 + i));
        }
        assertThat(issued).hasSize(50);
    }

    /** The guarantee behind the sequence: the database refuses a duplicate outright. */
    @Test
    void theDatabaseRefusesADuplicateNumber() {
        String taken = service.ensure(TrackingNumber.OwnerType.MOVEMENT, FAR_AWAY + 7);

        assertThatThrownBy(() -> repository.saveAndFlush(TrackingNumber.builder()
                .number(taken)
                .ownerType(TrackingNumber.OwnerType.CONSIGNMENT)
                .ownerId(FAR_AWAY + 8)
                .issuedAt(LocalDateTime.now())
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Loaded onto a consignment: its own number is set aside, kept, and never handed out again. */
    @Test
    void aSupersededNumberIsKeptAndATripThenGetsAFreshOne() {
        String own = service.ensure(TrackingNumber.OwnerType.MOVEMENT, FAR_AWAY + 9);

        assertThat(service.supersede(TrackingNumber.OwnerType.MOVEMENT, FAR_AWAY + 9)).isTrue();

        assertThat(repository.findByNumber(own)).get()
                .extracting(TrackingNumber::getStatus).isEqualTo(TrackingNumber.Status.SUPERSEDED);
        assertThat(service.ensure(TrackingNumber.OwnerType.MOVEMENT, FAR_AWAY + 9))
                .as("taken back off the van, it gets a new number rather than the old one")
                .isNotEqualTo(own);
    }

    // ── The client cannot supply one ─────────────────────────────────────────

    /** A field the client could fill is a way to type a duplicate - so neither payload has one. */
    @Test
    void neitherDispatchPayloadAcceptsATrackingNumber() {
        for (Class<?> dto : new Class<?>[]{MovementDispatchDTO.class, ConsignmentDispatchDTO.class}) {
            assertThat(Arrays.stream(dto.getDeclaredFields()).map(java.lang.reflect.Field::getName))
                    .as("%s must not carry a tracking number", dto.getSimpleName())
                    .doesNotContain("trackingNumber")
                    .contains("courierReference");
        }
    }
}

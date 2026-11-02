package pride.bank.erp.scanrelay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import pride.bank.erp.user.User;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The scan relay's rules, without a server: a pairing code works once and briefly, a scan reaches
 * only the user who showed the code, and a pairing ends when its owner ends it, signs out, or
 * eight hours pass.
 */
class ScanRelayServiceTest {

    private SimpMessagingTemplate messaging;
    private SimpUserRegistry registry;
    private ScanRelayService relay;
    private Instant now;
    private final User alice = user(11L, "Alice", "Guzman");
    private final User bob = user(12L, "Bob", "Okello");

    @BeforeEach
    void setUp() {
        messaging = mock(SimpMessagingTemplate.class);
        registry = mock(SimpUserRegistry.class);
        relay = new ScanRelayService(messaging, registry);
        now = Instant.parse("2026-10-28T08:00:00Z");
        relay.useClock(Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void scanningTheCodeOnTheScreenConnectsThePdaAndTellsTheComputer() {
        var started = relay.start(alice);
        assertThat(started.qrValue()).isEqualTo(ScanRelayService.PAIR_PREFIX + started.pairCode());

        var connected = relay.connect(started.qrValue());

        assertThat(connected).isPresent();
        assertThat(connected.get().relayToken()).hasSizeGreaterThanOrEqualTo(40);
        assertThat(connected.get().ownerName()).isEqualTo("Alice Guzman");
        assertThat(pushedTo("11")).extracting(ScanRelayService.Message::type).isEqualTo("CONNECTED");
        assertThat(relay.status(started.pairingId(), alice)).get().extracting(s -> s.connected()).isEqualTo(true);
    }

    @Test
    void theCodeWorksOnceSoSomebodyReadingItLaterHasNothing() {
        var started = relay.start(alice);
        assertThat(relay.connect(started.pairCode())).isPresent();
        assertThat(relay.connect(started.pairCode())).as("second use").isEmpty();
    }

    @Test
    void theCodeCanBeTypedAsShownOnScreen() {
        var started = relay.start(alice);
        String typed = (started.pairCode().substring(0, 4) + "-" + started.pairCode().substring(4)).toLowerCase();
        assertThat(relay.connect(" " + typed + " ")).isPresent();
    }

    @Test
    void theCodeExpiresAfterFiveMinutes() {
        var started = relay.start(alice);
        relay.useClock(Clock.fixed(now.plus(ScanRelayService.PAIR_CODE_LIFETIME).plusSeconds(1), ZoneOffset.UTC));
        assertThat(relay.connect(started.pairCode())).isEmpty();
    }

    @Test
    void aScanReachesOnlyTheOwnerAndIsCleanedOfScannerControlCharacters() {
        var started = relay.start(alice);
        String token = relay.connect(started.pairCode()).orElseThrow().relayToken();
        when(registry.getUser("11")).thenReturn(mock(SimpUser.class));

        var sent = relay.send(token, " PBL-2026-LPT07\r\n");

        assertThat(sent).get().extracting(s -> s.delivered()).isEqualTo(true);
        ScanRelayService.Message message = pushedTo("11");
        assertThat(message.type()).isEqualTo("SCAN");
        assertThat(message.code()).isEqualTo("PBL-2026-LPT07");
        assertThat(message.pairingId()).as("so other tabs of the same user can ignore it").isEqualTo(started.pairingId());
        verify(messaging, never()).convertAndSendToUser(eq("12"), any(), any());
    }

    @Test
    void thePdaIsToldWhenNoBrowserOfTheOwnerIsListening() {
        String token = relay.connect(relay.start(alice).pairCode()).orElseThrow().relayToken();
        when(registry.getUser("11")).thenReturn(null);
        assertThat(relay.send(token, "PBL-2026-LPT07")).get().extracting(s -> s.delivered()).isEqualTo(false);
    }

    @Test
    void anUnknownTokenSendsNothing() {
        assertThat(relay.send("not-a-token", "PBL-2026-LPT07")).isEmpty();
        assertThat(relay.send(null, "PBL-2026-LPT07")).isEmpty();
        verify(messaging, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void emptyAndOverlongScansAreRefused() {
        String token = relay.connect(relay.start(alice).pairCode()).orElseThrow().relayToken();
        assertThatThrownBy(() -> relay.send(token, "\r\n ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> relay.send(token, "X".repeat(ScanRelayService.MAX_CODE_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPairingEndsAfterEightHours() {
        String token = relay.connect(relay.start(alice).pairCode()).orElseThrow().relayToken();
        relay.useClock(Clock.fixed(now.plus(ScanRelayService.PAIRING_LIFETIME).plus(Duration.ofMinutes(1)), ZoneOffset.UTC));
        assertThat(relay.send(token, "PBL-2026-LPT07")).isEmpty();
    }

    @Test
    void onlyTheOwnerCanEndOrReadAPairing() {
        var started = relay.start(alice);
        String token = relay.connect(started.pairCode()).orElseThrow().relayToken();

        assertThat(relay.end(started.pairingId(), bob)).isFalse();
        assertThat(relay.status(started.pairingId(), bob)).isEmpty();
        assertThat(relay.send(token, "PBL-1")).as("still paired after somebody else tried").isPresent();

        assertThat(relay.end(started.pairingId(), alice)).isTrue();
        assertThat(relay.send(token, "PBL-1")).as("over once the owner ends it").isEmpty();
    }

    @Test
    void thePdaUnpairingTellsTheComputer() {
        var started = relay.start(alice);
        String token = relay.connect(started.pairCode()).orElseThrow().relayToken();

        assertThat(relay.disconnect(token)).isTrue();

        ScanRelayService.Message message = pushedTo("11");
        assertThat(message.type()).isEqualTo("ENDED");
        assertThat(message.pairingId()).isEqualTo(started.pairingId());
        assertThat(relay.send(token, "PBL-1")).isEmpty();
        assertThat(relay.disconnect(token)).as("a second unpair finds nothing").isFalse();
    }

    @Test
    void signingOutEndsEveryPairingTheUserHolds() {
        String first = relay.connect(relay.start(alice).pairCode()).orElseThrow().relayToken();
        String second = relay.connect(relay.start(alice).pairCode()).orElseThrow().relayToken();
        String bobs = relay.connect(relay.start(bob).pairCode()).orElseThrow().relayToken();

        relay.endAllFor(alice.getId());

        assertThat(relay.send(first, "PBL-1")).isEmpty();
        assertThat(relay.send(second, "PBL-1")).isEmpty();
        assertThat(relay.send(bobs, "PBL-1")).as("another user's pairing is untouched").isPresent();
    }

    @Test
    void aUserKeepsOnlyTheirNewestFewPairings() {
        var oldest = relay.start(alice);
        for (int i = 1; i < ScanRelayService.MAX_PAIRINGS_PER_USER; i++) {
            relay.useClock(Clock.fixed(now.plusSeconds(i), ZoneOffset.UTC));
            relay.start(alice);
        }
        relay.useClock(Clock.fixed(now.plusSeconds(100), ZoneOffset.UTC));
        var newest = relay.start(alice);

        assertThat(relay.connect(oldest.pairCode())).as("the oldest was dropped").isEmpty();
        assertThat(relay.connect(newest.pairCode())).isPresent();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** The last message pushed to that user. */
    private ScanRelayService.Message pushedTo(String userKey) {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messaging, atLeastOnce()).convertAndSendToUser(eq(userKey), eq(ScanRelayService.DESTINATION), payload.capture());
        var all = payload.getAllValues();
        return (ScanRelayService.Message) all.get(all.size() - 1);
    }

    private static User user(Long id, String first, String last) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getFirstName()).thenReturn(first);
        when(user.getLastName()).thenReturn(last);
        return user;
    }
}

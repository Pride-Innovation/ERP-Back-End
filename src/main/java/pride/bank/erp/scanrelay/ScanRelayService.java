/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.scanrelay;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;
import pride.bank.erp.user.User;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Carries a scan from a handheld PDA to the computer browser its user is working in.
 *
 * <h2>Why it exists</h2>
 * The PDA's scan engine types into the device it is built into, so a label scanned with it lands in
 * the PDA's own browser. The person is working on a laptop. This relays each scan through the server
 * to that laptop's browser, over the live connection notifications already use.
 *
 * <h2>Pairing: a short code, then a long secret</h2>
 * <ol>
 *   <li>The laptop asks for a pairing ({@link #start}) and shows an 8-character code as a QR code.
 *       It is valid for {@link #PAIR_CODE_LIFETIME} and works <b>once</b>.</li>
 *   <li>The PDA scans it ({@link #connect}) and gets a 256-bit relay token, which it keeps for the
 *       session. The code on the screen is spent the moment it is used, so somebody who reads it
 *       over a shoulder afterwards has nothing.</li>
 *   <li>Each scan is sent with that token ({@link #send}) and pushed to the laptop's user.</li>
 * </ol>
 *
 * <p><b>The PDA needs no login.</b> The token grants exactly one thing: putting text into the search
 * box of the browser that showed the code. Whatever that text then fetches is fetched by the laptop,
 * under the laptop user's own login and scope - the relay widens nobody's access to anything.
 *
 * <h2>Held in memory</h2>
 * Nothing about a pairing is worth keeping and scans are not stored. Memory is right for the single
 * server this runs on; several servers behind a balancer would need these maps moved to the database.
 * A restart ends every pairing, and the PDA says so on its next scan.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScanRelayService {

    /** How long the code on the laptop's screen may be scanned. */
    static final Duration PAIR_CODE_LIFETIME = Duration.ofMinutes(5);
    /** How long a connected PDA keeps relaying, unless ended sooner. */
    static final Duration PAIRING_LIFETIME = Duration.ofHours(8);
    /** Longer than any engraved number, short enough that nobody relays a document. */
    static final int MAX_CODE_LENGTH = 128;
    /** A user opening pairings in many tabs keeps their newest few; the oldest is dropped. */
    static final int MAX_PAIRINGS_PER_USER = 5;

    /** Where the laptop listens. Sent per user, so only that user's browsers can receive it. */
    public static final String DESTINATION = "/queue/scans";

    /** No 0/O, 1/I/L: the code may be read off a screen and typed. */
    private static final String PAIR_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int PAIR_CODE_LENGTH = 8;
    /** What the laptop's QR code carries before the code, so the PDA page knows it from an asset label. */
    public static final String PAIR_PREFIX = "ERPPAIR:";

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;

    private final SecureRandom random = new SecureRandom();
    private Clock clock = Clock.systemUTC();

    private final Map<String, Pairing> byId = new ConcurrentHashMap<>();
    private final Map<String, Pairing> byPairCode = new ConcurrentHashMap<>();
    private final Map<String, Pairing> byRelayToken = new ConcurrentHashMap<>();

    /** Tests move time forward instead of waiting for it. */
    void useClock(Clock clock) {
        this.clock = clock;
    }

    private static final class Pairing {
        final String id;
        final Long ownerId;
        final String ownerName;
        final String pairCode;
        final Instant pairCodeExpiresAt;
        final Instant createdAt;
        volatile String relayToken;
        volatile Instant expiresAt;

        Pairing(String id, Long ownerId, String ownerName, String pairCode, Instant now) {
            this.id = id;
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.pairCode = pairCode;
            this.createdAt = now;
            this.pairCodeExpiresAt = now.plus(PAIR_CODE_LIFETIME);
            this.expiresAt = this.pairCodeExpiresAt;
        }

        boolean connected() {
            return relayToken != null;
        }
    }

    public record Started(String pairingId, String pairCode, String qrValue, Instant pairCodeExpiresAt) {}

    public record Connected(String relayToken, String ownerName, Instant expiresAt) {}

    public record Status(boolean connected, Instant expiresAt) {}

    public record Sent(boolean delivered) {}

    /** What the laptop's browser receives on {@link #DESTINATION}. */
    public record Message(String type, String pairingId, String code) {
        static final String CONNECTED = "CONNECTED";
        static final String SCAN = "SCAN";
        static final String ENDED = "ENDED";
    }

    /** The laptop asks for a code to show. */
    public Started start(User owner) {
        purgeExpired();
        Instant now = clock.instant();

        // Keep at most a few per user, so an open-and-forget habit cannot grow the maps without end.
        var mine = byId.values().stream().filter(p -> p.ownerId.equals(owner.getId()))
                .sorted(Comparator.comparing((Pairing p) -> p.createdAt)).toList();
        for (int i = 0; i <= mine.size() - MAX_PAIRINGS_PER_USER; i++) {
            remove(mine.get(i));
        }

        String code;
        do {
            code = pairCode();
        } while (byPairCode.containsKey(code));

        Pairing pairing = new Pairing(UUID.randomUUID().toString(), owner.getId(), displayName(owner), code, now);
        byId.put(pairing.id, pairing);
        byPairCode.put(code, pairing);
        return new Started(pairing.id, code, PAIR_PREFIX + code, pairing.pairCodeExpiresAt);
    }

    /**
     * The PDA scanned (or typed) the code on the laptop's screen. Single use: the code is spent here
     * whether or not anything is ever scanned afterwards.
     */
    public Optional<Connected> connect(String scannedOrTyped) {
        purgeExpired();
        String code = normalisePairCode(scannedOrTyped);
        if (code == null) return Optional.empty();

        Pairing pairing = byPairCode.remove(code);
        if (pairing == null || pairing.connected()) return Optional.empty();

        pairing.relayToken = relayToken();
        pairing.expiresAt = clock.instant().plus(PAIRING_LIFETIME);
        byRelayToken.put(pairing.relayToken, pairing);
        push(pairing, Message.CONNECTED, null);
        return Optional.of(new Connected(pairing.relayToken, pairing.ownerName, pairing.expiresAt));
    }

    /**
     * One scan, to the laptop. Empty when the pairing is unknown or over, which the PDA reports as
     * "disconnected".
     *
     * @throws IllegalArgumentException when the scan is empty or implausibly long
     */
    public Optional<Sent> send(String relayToken, String scanned) {
        if (relayToken == null) return Optional.empty();
        Pairing pairing = byRelayToken.get(relayToken);
        if (pairing == null) return Optional.empty();
        if (isOver(pairing)) {
            remove(pairing);
            return Optional.empty();
        }

        String code = cleanScan(scanned);
        push(pairing, Message.SCAN, code);
        // Whether the owner has a browser connected right now. Not proof that this exact tab took it
        // - nothing short of a reply from the laptop could say that - but it tells the person holding
        // the PDA when nobody is listening, which is the case worth telling them about.
        boolean delivered = userRegistry.getUser(String.valueOf(pairing.ownerId)) != null;
        return Optional.of(new Sent(delivered));
    }

    /** The laptop asks whether its PDA has connected - after a reload, it cannot remember. */
    public Optional<Status> status(String pairingId, User caller) {
        Pairing pairing = byId.get(pairingId);
        if (pairing == null || !pairing.ownerId.equals(caller.getId())) return Optional.empty();
        if (isOver(pairing)) {
            remove(pairing);
            return Optional.empty();
        }
        return Optional.of(new Status(pairing.connected(), pairing.expiresAt));
    }

    /** The laptop disconnects. Only its owner can end a pairing. */
    public boolean end(String pairingId, User caller) {
        Pairing pairing = byId.get(pairingId);
        if (pairing == null || !pairing.ownerId.equals(caller.getId())) return false;
        remove(pairing);
        push(pairing, Message.ENDED, null);
        return true;
    }

    /**
     * The PDA unpairs itself. The computer is told, so its scanner button stops claiming a
     * connection that no longer exists.
     */
    public boolean disconnect(String relayToken) {
        if (relayToken == null) return false;
        Pairing pairing = byRelayToken.get(relayToken);
        if (pairing == null) return false;
        remove(pairing);
        push(pairing, Message.ENDED, null);
        return true;
    }

    /** Signing out ends every pairing the user holds. */
    public void endAllFor(Long userId) {
        if (userId == null) return;
        byId.values().stream().filter(p -> p.ownerId.equals(userId)).toList().forEach(this::remove);
    }

    // ── internals ────────────────────────────────────────────────────────────

    /**
     * The scan as text: control characters (a GS1 separator, a stray CR from the scanner's
     * terminator) and surrounding spaces removed, nothing else changed.
     */
    static String cleanScan(String scanned) {
        String code = scanned == null ? "" : scanned.replaceAll("\\p{Cntrl}", "").trim();
        if (code.isEmpty()) {
            throw new IllegalArgumentException("The scan was empty.");
        }
        if (code.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException(
                    "The scan was " + code.length() + " characters long; a label carries at most "
                            + MAX_CODE_LENGTH + ".");
        }
        return code;
    }

    /** Accepts the QR's value, or the code as shown on screen - any case, with or without the dash. */
    static String normalisePairCode(String raw) {
        if (raw == null) return null;
        String value = raw.replaceAll("\\p{Cntrl}", "").trim().toUpperCase();
        if (value.startsWith(PAIR_PREFIX)) value = value.substring(PAIR_PREFIX.length());
        value = value.replaceAll("[\\s-]", "");
        return value.length() == PAIR_CODE_LENGTH ? value : null;
    }

    private boolean isOver(Pairing pairing) {
        Instant now = clock.instant();
        return pairing.connected() ? now.isAfter(pairing.expiresAt) : now.isAfter(pairing.pairCodeExpiresAt);
    }

    private void purgeExpired() {
        byId.values().stream().filter(this::isOver).toList().forEach(this::remove);
    }

    private void remove(Pairing pairing) {
        byId.remove(pairing.id);
        byPairCode.remove(pairing.pairCode, pairing);
        if (pairing.relayToken != null) byRelayToken.remove(pairing.relayToken, pairing);
    }

    private void push(Pairing pairing, String type, String code) {
        try {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(pairing.ownerId), DESTINATION, new Message(type, pairing.id, code));
        } catch (Exception e) {
            // A push that fails must not fail the PDA's request; the PDA is told "not delivered".
            log.warn("Scan relay: push to user {} failed: {}", pairing.ownerId, e.getMessage());
        }
    }

    private String pairCode() {
        StringBuilder code = new StringBuilder(PAIR_CODE_LENGTH);
        for (int i = 0; i < PAIR_CODE_LENGTH; i++) {
            code.append(PAIR_ALPHABET.charAt(random.nextInt(PAIR_ALPHABET.length())));
        }
        return code.toString();
    }

    private String relayToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String displayName(User user) {
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isEmpty() ? "your computer" : name;
    }
}

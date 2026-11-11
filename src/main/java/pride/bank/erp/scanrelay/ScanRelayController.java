/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.scanrelay;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.user.User;

import java.util.Map;

/**
 * Pairing a PDA with a computer browser, and relaying its scans. See {@link ScanRelayService}.
 *
 * <h2>Two audiences, two rules</h2>
 * <ul>
 *   <li><b>{@code /scan-pairings}</b> - the computer. Authentication alone: a pairing belongs to the
 *       signed-in user and can only ever deliver to that user, so there is no permission to ask
 *       for. Listed in {@code EndpointGuardCoverageTest.DELIBERATELY_AUTHENTICATED_ONLY}.</li>
 *   <li><b>{@code /scan-relay}</b> - the PDA, which is not signed in. Public in
 *       {@code SecurityConfiguration}; the pairing code and then the relay token are the whole of
 *       the authorisation, and they authorise only text into one browser's search box.</li>
 * </ul>
 *
 * <h2>Statuses</h2>
 * Only codes the firewall admits. An unknown or finished pairing is a <b>404</b> carrying
 * {@code errorCode: PAIRING_ENDED} - returned directly rather than thrown, because this application's
 * {@code NotFoundException} handler answers 200. A bad scan is a 400 carrying {@code INVALID_SCAN}.
 * Both carry an {@code errorCode}, so the front end's interceptor leaves them to the page that asked.
 */
@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class ScanRelayController {

    public static final String PAIRING_ENDED = "PAIRING_ENDED";
    public static final String INVALID_SCAN = "INVALID_SCAN";

    private final ScanRelayService scanRelayService;
    private final CurrentUserService currentUserService;

    public record ConnectRequest(String code) {}

    public record ScanRequest(String token, String code) {}

    // ── the computer ─────────────────────────────────────────────────────────

    @PostMapping("/scan-pairings")
    public ResponseEntity<?> startPairing() {
        User caller = currentUserService.getCurrentUser();
        if (caller == null) return ended("You need to be signed in to pair a scanner.");
        return ResponseEntity.ok(scanRelayService.start(caller));
    }

    @GetMapping("/scan-pairings/{pairingId}")
    public ResponseEntity<?> pairingStatus(@PathVariable String pairingId) {
        User caller = currentUserService.getCurrentUser();
        if (caller == null) return ended("This pairing has ended.");
        return scanRelayService.status(pairingId, caller)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ended("This pairing has ended."));
    }

    @DeleteMapping("/scan-pairings/{pairingId}")
    public ResponseEntity<?> endPairing(@PathVariable String pairingId) {
        User caller = currentUserService.getCurrentUser();
        if (caller != null) scanRelayService.end(pairingId, caller);
        // Ending something already over is not an error: the outcome the caller wanted holds.
        return ResponseEntity.ok(Map.of("ended", true));
    }

    // ── the PDA ──────────────────────────────────────────────────────────────

    @PostMapping("/scan-relay/connect")
    public ResponseEntity<?> connect(@RequestBody(required = false) ConnectRequest body) {
        return scanRelayService.connect(body == null ? null : body.code())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ended("That pairing code is not valid or has expired. "
                        + "Show a new one on your computer and scan it again."));
    }

    @PostMapping("/scan-relay/scans")
    public ResponseEntity<?> scan(@RequestBody(required = false) ScanRequest body) {
        try {
            return scanRelayService.send(body == null ? null : body.token(), body == null ? null : body.code())
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ended("This scanner is no longer paired with a computer. "
                            + "Pair it again from the computer."));
        } catch (IllegalArgumentException invalid) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", invalid.getMessage(), "errorCode", INVALID_SCAN));
        }
    }

    @PostMapping("/scan-relay/disconnect")
    public ResponseEntity<?> disconnect(@RequestBody(required = false) ScanRequest body) {
        scanRelayService.disconnect(body == null ? null : body.token());
        // Already over is the outcome the PDA asked for.
        return ResponseEntity.ok(Map.of("ended", true));
    }

    private static ResponseEntity<?> ended(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", message, "errorCode", PAIRING_ENDED));
    }
}

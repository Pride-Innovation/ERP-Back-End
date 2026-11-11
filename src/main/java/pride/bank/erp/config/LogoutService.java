/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Service;
import pride.bank.erp.auditing.trail.AuditEventType;
import pride.bank.erp.auditing.trail.AuditModule;
import pride.bank.erp.auditing.trail.AuditService;
import pride.bank.erp.auditing.trail.AuditSeverity;
import pride.bank.erp.scanrelay.ScanRelayService;
import pride.bank.erp.token.TokenRepository;
import pride.bank.erp.user.User;

@Service
@RequiredArgsConstructor
public class LogoutService implements LogoutHandler {

    private final TokenRepository tokenRepository;
    private final AuditService auditService;
    /*
     * A provider, not the service: this bean is built while the security chain is, and the relay
     * reaches the websocket broker. Resolving it at sign-out keeps the two construction orders apart.
     */
    private final ObjectProvider<ScanRelayService> scanRelayService;

    /**
     * Revokes the presented token and records the sign-out.
     *
     * <p>Worth knowing when reading the trail: this only fires when the client actually calls
     * {@code /auth/logout}. A closed tab, a crashed browser or a token left to expire is not a logout
     * and cannot be observed from here, so logouts will always be fewer than logins. That is the
     * nature of stateless tokens, not a gap in the recording.
     */
    @Override
    public void logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }

        jwt = authHeader.substring(7);
        var storedToken = tokenRepository.findByToken(jwt)
                .orElse(null);

        if (storedToken != null) {
            storedToken.setExpired(true);
            storedToken.setRevoked(true);
            tokenRepository.save(storedToken);
        }

        // The token itself names the user, which is more reliable here than the security context:
        // the logout filter runs outside the JWT filter's authenticated request, so `authentication`
        // is often null by the time this is reached.
        User user = storedToken != null ? storedToken.getUser() : null;
        // Signing out also disconnects any handheld scanner paired with this user's browsers.
        if (user != null) {
            scanRelayService.ifAvailable(relay -> relay.endAllFor(user.getId()));
        }
        auditService.recordAs(user, user == null ? null : user.getEmail(),
                AuditEventType.LOGOUT, AuditModule.SYSTEM, "LOGOUT",
                "Signed out", AuditSeverity.INFO);
    }
}

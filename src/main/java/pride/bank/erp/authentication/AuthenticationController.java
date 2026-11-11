/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.authentication;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.authentication.directory.DirectoryAuthSettings;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final DirectoryAuthSettings directoryAuthSettings;

    @PostMapping("/auth/register/{titleId}")
    public ResponseEntity<AuthenticationResponse> register(
            @PathVariable Long titleId,
            @Valid @RequestBody RegisterRequest request
    ) throws NotFoundException {
        return ResponseEntity.ok(authenticationService.register(request, titleId));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<AuthenticationResponse> authenticate(
            @Valid @RequestBody AuthenticationRequest request
    ) {
        return ResponseEntity.ok(authenticationService.authenticate(request));
    }

    /**
     * Whether passwords are checked by AD or by this application, so the pages offer the right
     * actions: no "Forgot password?" and no "Change password" while AD owns the password. Public,
     * because the login page asks before anyone signs in; it says nothing about any account.
     */
    @GetMapping("/auth/mode")
    public ResponseEntity<Map<String, Object>> authMode() {
        return ResponseEntity.ok(Map.of(
                "mode", directoryAuthSettings.getMode().name(),
                "passwordsManagedByDirectory", directoryAuthSettings.isLdap()));
    }

    @PostMapping("/auth/refresh-token")
    public void refreshToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        authenticationService.refreshToken(request, response);
    }

    @PostMapping("/auth/verify-email/{token}")
    public ResponseEntity<Map<String, Object>> verifyAccount(@PathVariable String token) {
        Map<String, Object> response = authenticationService.verifyAccount(token);
        HttpStatus status = "success".equals(response.get("status")) ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }
}

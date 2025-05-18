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
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;

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
    ) throws NotFoundException {
        return ResponseEntity.ok(authenticationService.authenticate(request));
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

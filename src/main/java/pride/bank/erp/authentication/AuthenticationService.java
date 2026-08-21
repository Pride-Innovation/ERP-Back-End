/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import pride.bank.erp.auditing.trail.AuditEventType;
import pride.bank.erp.auditing.trail.AuditModule;
import pride.bank.erp.auditing.trail.AuditService;
import pride.bank.erp.auditing.trail.AuditSeverity;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.exception.AccountBlockedException;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.PasswordGenerator;
import pride.bank.erp.notification.Email;
import pride.bank.erp.title.Title;
import pride.bank.erp.title.TitleService;
import pride.bank.erp.token.Token;
import pride.bank.erp.token.TokenRepository;
import pride.bank.erp.token.TokenType;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final TokenRepository tokenRepository;
    private final Email email;
    private final TitleService titleService;
    /**
     * Sign-in activity is invisible to Hibernate Envers — logging in changes no entity — so it is
     * recorded explicitly here or not at all.
     */
    private final AuditService auditService;

    public AuthenticationResponse register(RegisterRequest request, Long titleId)
            throws NotFoundException {

        Title title = titleService.findTitle(titleId);
        String generatedPassword = PasswordGenerator.generatePassword();

        var user = User.builder()
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .password(passwordEncoder.encode(generatedPassword))
                .staffNumber(request.getStaffNumber())
                .availability(request.getAvailability())
                .gender(request.getGender())
                .title(title)
                .isAccountNonLocked(false)
                .build();
        User savedUser = userRepository.save(user);
        auditService.record(AuditEventType.CREATED, AuditModule.USERS, "USER_REGISTERED",
                "Registered user account " + savedUser.getEmail(),
                AuditSeverity.WARNING, "User", savedUser.getId());
        AuthenticationResponse response = generateToken(savedUser, title);
        email.sendEmailVerification(
                user.getEmail(),
                "Account Verification",
                response.getAccessToken(),
                "Verify Your Account",
                "Verify Account",
                generatedPassword,
                "authentication",
                null,
                null,
                null,
                null,
                null
        );
        return response;
    }

    /**
     * Signs a user in.
     *
     * <p>Every exit from this method is audited, successful or not. A trail that recorded only
     * successful logins would be silent on exactly the activity worth watching: someone working
     * through passwords against a real account, or repeatedly probing one that is already blocked.
     * Those records are written with {@code REQUIRES_NEW} inside {@link AuditService}, so the throw
     * that follows a refusal unwinds the attempt without taking the record of it along.
     */
    public AuthenticationResponse authenticate(AuthenticationRequest request) throws NotFoundException {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    // No user, so no id and no name — the attempted address is the whole of what is
                    // known, and is worth keeping: repeated attempts against addresses that do not
                    // exist look quite different from a member of staff mistyping their own.
                    auditService.recordAs(null, request.getEmail(),
                            AuditEventType.REJECTED, AuditModule.SYSTEM, "LOGIN_FAILED_UNKNOWN_USER",
                            "Failed sign-in attempt for unknown address " + request.getEmail(),
                            AuditSeverity.WARNING);
                    return new NotFoundException("User not found");
                });

        if (!user.isEnabled()) {
            auditRefusal(user, "LOGIN_REFUSED_ACCOUNT_DISABLED", "disabled");
            throw new DisabledException("Account is disabled. Please contact Admin");
        }

        if (user.isBlocked()) {
            auditRefusal(user, "LOGIN_REFUSED_ACCOUNT_BLOCKED", "blocked");
            throw new AccountBlockedException("Your account has been blocked. Please contact your admin.");
        }

        if (!user.isAccountNonLocked()) {
            auditRefusal(user, "LOGIN_REFUSED_ACCOUNT_LOCKED", "locked pending email verification");
            throw new LockedException("Account is locked. Please verify your email.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= 3) {
                user.setBlocked(true);
                user.setAccountNonLocked(false);
                user.setFailedLoginAttempts(0);
                userRepository.save(user);
                // Critical: an account locking itself out is either an attack or a member of staff
                // about to be unable to work. Both want someone to look.
                auditService.recordAs(user, user.getEmail(),
                        AuditEventType.REJECTED, AuditModule.SYSTEM, "ACCOUNT_AUTO_BLOCKED",
                        "Account blocked automatically after 3 failed sign-in attempts",
                        AuditSeverity.CRITICAL);
                throw new AccountBlockedException("Your account has been blocked after multiple failed login attempts. Please contact your admin.");
            }
            userRepository.save(user);
            int remaining = 3 - attempts;
            auditService.recordAs(user, user.getEmail(),
                    AuditEventType.REJECTED, AuditModule.SYSTEM, "LOGIN_FAILED_BAD_PASSWORD",
                    "Failed sign-in attempt (" + attempts + " of 3) — incorrect password",
                    AuditSeverity.WARNING);
            throw new BadCredentialsException("Invalid email or password. " + remaining + " attempt(s) remaining before account lockout.");
        }

        // Reset failed login counter on successful login
        if (user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        revokeAllUserTokens(user);
        AuthenticationResponse response = generateToken(user, user.getTitle());
        auditService.recordAs(user, user.getEmail(),
                AuditEventType.LOGIN, AuditModule.SYSTEM, "LOGIN_SUCCEEDED",
                "Signed in", AuditSeverity.INFO);
        return response;
    }

    /** A sign-in refused by the state of the account rather than by the password. */
    private void auditRefusal(User user, String action, String state) {
        auditService.recordAs(user, user.getEmail(),
                AuditEventType.REJECTED, AuditModule.SYSTEM, action,
                "Sign-in refused — account is " + state, AuditSeverity.WARNING);
    }

    private AuthenticationResponse generateToken(User user, Title title) {
        var jwtToken = jwtService.generateToken(user);
        var refreshToken = jwtService.generateRefreshToken(user);

        saveUserToken(user, jwtToken);

        return AuthenticationResponse.builder()
                .accessToken(jwtToken)
                .refreshToken(refreshToken)
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .title(title)
                .gender(user.getGender())
                .unit(user.getUnit())
                // Carried so the browser can scope anything to a branch and resolve every
                // permission the user actually holds — see AuthenticationResponse.
                .branch(user.getBranch())
                .additionalRoles(user.getAdditionalRoles())
                .build();
    }

    private void revokeAllUserTokens(User user) {
        var validUserTokens = tokenRepository.findAllValidTokensByUser(user.getId());
        if (validUserTokens.isEmpty()) {
            return;
        }
        validUserTokens.forEach(token -> {
            token.setRevoked(true);
            token.setExpired(true);
        });
        tokenRepository.saveAll(validUserTokens);
    }

    private void saveUserToken(User user, String jwtToken) {
        var token = Token
                .builder()
                .user(user)
                .token(jwtToken)
                .tokenType(TokenType.BEARER)
                .revoked(false)
                .expired(false)
                .build();
        tokenRepository.save(token);
    }

    public void refreshToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        final String refreshToken;
        final String userEmail;
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }

        refreshToken = authHeader.substring(7);
        userEmail = jwtService.extractUserEmail(refreshToken);

        if (userEmail != null) {
            var user = userRepository.findByEmail(userEmail).orElseThrow();
            if (jwtService.isTokenValid(refreshToken, user)) {
                var accessToken = jwtService.generateToken(user);
                revokeAllUserTokens(user);
                saveUserToken(user, accessToken);
                var authenticationResponse = AuthenticationResponse
                        .builder()
                        .accessToken(accessToken)
                        .id(user.getId())
                        .refreshToken(refreshToken)
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .email((user.getEmail()))
                        .gender(user.getGender())
                        .unit(user.getUnit())
                        // Same fields as the login response: a refresh rewrites the stored session
                        // user, so omitting them here would silently strip the branch and the
                        // additional roles the moment a token renewed.
                        .branch(user.getBranch())
                        .additionalRoles(user.getAdditionalRoles())
                        .title(user.getTitle())
                        .build();
                new ObjectMapper().writeValue(response.getOutputStream(), authenticationResponse);
            }
        }
    }

    public Map<String, Object> verifyAccount(String token) {
        String email = jwtService.extractUserEmail(token);

        if (email == null || email.isEmpty()) {
            return createResponse("failed", "Invalid or expired token. Account verification failed.");
        }

        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            return createResponse("failed", "Account does not exist.");
        }

        User user = optionalUser.get();
        user.setEnabled(true);
        userRepository.save(user);

        auditService.recordAs(user, user.getEmail(),
                AuditEventType.UPDATED, AuditModule.USERS, "ACCOUNT_VERIFIED",
                "Verified their account by email", AuditSeverity.INFO);

        return createResponse("success", "Account verified successfully.");
    }

    private Map<String, Object> createResponse(String status, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", status);
        response.put("message", message);
        return response;
    }

}
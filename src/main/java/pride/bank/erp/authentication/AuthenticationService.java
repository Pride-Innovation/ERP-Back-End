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
import org.springframework.http.HttpStatus;
import pride.bank.erp.advice.WafStatus;
import pride.bank.erp.authentication.directory.DirectoryAuthClient;
import pride.bank.erp.authentication.directory.DirectoryAuthSettings;
import pride.bank.erp.authentication.directory.DirectoryResult;
import pride.bank.erp.authentication.directory.DirectoryUsername;
import pride.bank.erp.exception.SignInException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
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
    private final DirectoryAuthSettings directoryAuthSettings;
    private final DirectoryAuthClient directoryAuthClient;

    /** One message for an unknown address and a wrong password, so neither can be told apart. */
    static final String WRONG_CREDENTIALS = "Incorrect email or password.";
    /** The login page's code for "the service, not you": unavailable, rate-limited, or misconfigured. */
    public static final String SIGN_IN_UNAVAILABLE = "SIGN_IN_UNAVAILABLE";

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
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    // No user, so no id and no name — the attempted address is the whole of what is
                    // known, and is worth keeping: repeated attempts against addresses that do not
                    // exist look quite different from a member of staff mistyping their own.
                    auditService.recordAs(null, request.getEmail(),
                            AuditEventType.REJECTED, AuditModule.SYSTEM, "LOGIN_FAILED_UNKNOWN_USER",
                            "Failed sign-in attempt for unknown address " + request.getEmail(),
                            AuditSeverity.WARNING);
                    // The same answer as a wrong password. This was a NotFoundException saying "User
                    // not found", which told anyone typing addresses which ones belong to staff.
                    return new BadCredentialsException(WRONG_CREDENTIALS);
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

        // The account's own state is checked above, before AD is asked: a disabled or blocked account
        // must not spend its owner's five-attempts AD allowance, which every bank system shares.
        if (directoryAuthSettings.isLdap()) {
            return authenticateAgainstDirectory(user, request.getPassword());
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

    /**
     * Checks the password with the bank's LDAP Authentication API, then issues this application's own
     * tokens exactly as a local sign-in does. Authorization is untouched: roles and permissions come
     * from this database, and the AD groups the API returns are ignored.
     *
     * <p>The AD username is the email's local part without trailing digits ({@link DirectoryUsername}),
     * so test accounts such as {@code sodong12@} sign in with the password of {@code sodong}.
     *
     * <p>No failed-attempt counter and no automatic block here. AD throttles a username after five
     * failures in fifteen minutes across every bank system; a counter of our own would block sooner
     * and separately for each test account sharing one AD identity. Every failure is still audited.
     */
    private AuthenticationResponse authenticateAgainstDirectory(User user, String password) {
        String username = DirectoryUsername.of(user.getEmail());
        if (username == null) {
            auditService.recordAs(user, user.getEmail(),
                    AuditEventType.REJECTED, AuditModule.SYSTEM, "LOGIN_REFUSED_NO_DIRECTORY_USERNAME",
                    "Sign-in refused — no AD username can be derived from the account's email",
                    AuditSeverity.WARNING);
            throw new BadCredentialsException(WRONG_CREDENTIALS);
        }

        DirectoryResult result = directoryAuthClient.authenticate(username, password);

        if (!result.authenticated()) {
            refuseDirectorySignIn(user, username, result.code());
        }

        // The identity AD vouched for must be the one this account signs in as. A mismatch would mean
        // the password just checked belongs to somebody else, so it is refused, not reconciled. (The
        // returned email is not compared: AD emails and usernames differ legitimately - jdoe /
        // jane.doe - and both sides would lose their digits the same way, so it could only refuse
        // real people without catching anything the username does not.)
        if (!username.equalsIgnoreCase(result.username().trim())) {
            auditService.recordAs(user, user.getEmail(),
                    AuditEventType.REJECTED, AuditModule.SYSTEM, "LOGIN_REFUSED_IDENTITY_MISMATCH",
                    "Sign-in refused — AD authenticated a different identity than this account's ("
                            + username + ")",
                    AuditSeverity.CRITICAL);
            throw new BadCredentialsException(WRONG_CREDENTIALS);
        }

        if (user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
        }
        revokeAllUserTokens(user);
        AuthenticationResponse response = generateToken(user, user.getTitle());
        auditService.recordAs(user, user.getEmail(),
                AuditEventType.LOGIN, AuditModule.SYSTEM, "LOGIN_SUCCEEDED",
                "Signed in with AD credentials (" + username + ")", AuditSeverity.INFO);
        return response;
    }

    /** Audits the refusal and throws what the login page shows. Never returns. */
    private void refuseDirectorySignIn(User user, String username, String code) {
        switch (code) {
            case DirectoryResult.INVALID_CREDENTIALS -> {
                auditService.recordAs(user, user.getEmail(),
                        AuditEventType.REJECTED, AuditModule.SYSTEM, "LOGIN_FAILED_BAD_PASSWORD",
                        "Failed sign-in attempt — AD refused the credentials for " + username,
                        AuditSeverity.WARNING);
                throw new BadCredentialsException(WRONG_CREDENTIALS);
            }
            case DirectoryResult.USER_THROTTLED -> {
                auditService.recordAs(user, user.getEmail(),
                        AuditEventType.REJECTED, AuditModule.SYSTEM, "LOGIN_REFUSED_AD_THROTTLED",
                        "Sign-in refused — AD is throttling " + username + " after repeated failures",
                        AuditSeverity.CRITICAL);
                throw new SignInException(HttpStatus.UNAUTHORIZED, DirectoryResult.USER_THROTTLED,
                        "Too many failed sign-in attempts. Please wait 15 minutes and try again.");
            }
            case DirectoryResult.VALIDATION_ERROR -> {
                throw new SignInException(HttpStatus.BAD_REQUEST, DirectoryResult.VALIDATION_ERROR,
                        "Enter your email address and password.");
            }
            default -> {
                // The service, its quota, or this application's own registration - never the employee.
                boolean ourCredentials = DirectoryResult.INVALID_CLIENT.equals(code)
                        || DirectoryResult.CLIENT_INACTIVE.equals(code);
                auditService.recordAs(user, user.getEmail(),
                        AuditEventType.REJECTED, AuditModule.SYSTEM, "LOGIN_REFUSED_DIRECTORY_UNAVAILABLE",
                        "Sign-in refused — the LDAP Authentication API could not check the password (" + code + ")",
                        ourCredentials ? AuditSeverity.CRITICAL : AuditSeverity.WARNING);
                throw new SignInException(WafStatus.SERVER_FAULT, SIGN_IN_UNAVAILABLE,
                        "Sign-in is temporarily unavailable. Please try again shortly.");
            }
        }
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
        /*
         * Every failure here used to be a silent 200 with an empty body.
         *
         * No Authorization header returned early; an expired or malformed refresh token left
         * `userEmail` null; an invalid one failed the check — and all three fell out of the method
         * having written nothing, so the response was `200 OK` with zero bytes. Measured: a refresh
         * with no header and a refresh with an expired token both answered 200.
         *
         * The browser then read `data.accessToken` off an empty body, got `undefined`, and stored the
         * string "undefined" as the access token before deciding the refresh had failed. A response
         * that says "fine" while handing back nothing is the worst shape a failure can take: every
         * caller has to know that success is not success.
         *
         * It refuses properly now, which is also what lets the front end tell "your session ended"
         * apart from "the refresh endpoint is down".
         */
        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InsufficientAuthenticationException(
                    "A refresh token is required to renew this session.");
        }

        final String refreshToken = authHeader.substring(7);
        final String userEmail = jwtService.extractUserEmail(refreshToken);

        if (userEmail == null) {
            throw new InsufficientAuthenticationException(
                    "This session has ended. Please sign in again.");
        }

        {
            var user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new InsufficientAuthenticationException(
                            "This session has ended. Please sign in again."));
            if (!jwtService.isTokenValid(refreshToken, user)) {
                throw new InsufficientAuthenticationException(
                        "This session has ended. Please sign in again.");
            }
            {
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
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import pride.bank.erp.config.JwtService;
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

    public AuthenticationResponse register(RegisterRequest request, Long titleId)
            throws NotFoundException {

        Title title = titleService.findTitle(titleId);
        String generatedPassword = PasswordGenerator.generatePassword();

        var user = User.builder()
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .password(passwordEncoder.encode(generatedPassword))
                .address(request.getAddress())
                .phone(request.getPhone())
                .title(title)
                .isEnabled(false)
                .build();
        User savedUser = userRepository.save(user);
        AuthenticationResponse response = generateToken(savedUser, title);
        email.sendEmailVerification(
                user.getEmail(),
                "Account Verification",
                response.getAccessToken(),
                "Verify Your Account",
                "Verify Account",
                generatedPassword,
                "authentication",
                null
        );
        return response;
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request) throws NotFoundException {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (!user.isEnabled()) {
            throw new DisabledException("Account is not verified. Please check your email to verify your account.");
        }
        revokeAllUserTokens(user);
        return generateToken(user, user.getTitle());
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

        return createResponse("success", "Account verified successfully.");
    }

    private Map<String, Object> createResponse(String status, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", status);
        response.put("message", message);
        return response;
    }

}
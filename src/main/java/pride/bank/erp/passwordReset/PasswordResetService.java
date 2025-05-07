package pride.bank.erp.passwordReset;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.notification.Email;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final Email emailService;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final PasswordResetRepository passwordResetRepository;

    @Transactional
    public Map<String, Object> requestPasswordReset(String email) {
        return userRepository.findByEmail(email)
                .map(this::sendResetPasswordEmail)
                .orElseGet(() -> createResponse(false, "Incorrect user email address", null));
    }

    private Map<String, Object> sendResetPasswordEmail(User user) {
        String token = jwtService.generateToken(user);
        boolean emailSent = emailService.sendEmailVerification(
                user.getEmail(),
                "Password Reset",
                token,
                "Reset Your Password",
                "Reset Password",
                null,
                "authentication",
                null
        );

        if (!emailSent) {
            return createResponse(
                    false,
                    "Failed to send verification email to: " + user.getEmail(),
                    null
            );
        }

        return createResponse(
                true,
                "Email sent to this email address: " + user.getEmail(),
                user.getEmail()
        );
    }

    @Transactional
    public Map<String, Object> resetPassword(String token, String newPassword) {
        String email = jwtService.extractUserEmail(token);

        if (email == null || email.isEmpty()) {
            return createResponse(
                    false,
                    "Invalid or expired token. Account verification failed.",
                    null
            );
        }

        return userRepository.findByEmail(email)
                .map(user -> {
                            saveNewPassword(user, token, newPassword);
                            return updatePassword(user, newPassword);
                        }
                )
                .orElseGet(() -> createResponse(
                                false,
                                "Account for: " + email + " does not exist.",
                                email
                        )
                );
    }

    private Map<String, Object> updatePassword(User user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return createResponse(true, "Password reset successfully.", user.getEmail());
    }

    private Map<String, Object> createResponse(boolean status, String message, String email) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", status);
        response.put("message", message);
        response.put("email", email);
        return response;
    }

    private void saveNewPassword(User user, String token, String password) {
        PasswordReset passwordReset = PasswordReset.builder()
                .user(user)
                .token(token)
                .password(passwordEncoder.encode(password))
                .build();
        passwordResetRepository.save(passwordReset);
    }
}

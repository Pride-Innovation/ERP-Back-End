/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.passwordReset;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.auditing.trail.AuditEventType;
import pride.bank.erp.auditing.trail.AuditModule;
import pride.bank.erp.auditing.trail.AuditService;
import pride.bank.erp.auditing.trail.AuditSeverity;
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
    /** Password changes are credential events; they belong in the trail whoever initiated them. */
    private final AuditService auditService;

    @Transactional
    public Map<String, Object> requestPasswordReset(String email) {
        return userRepository.findByEmail(email)
                .map(this::sendResetPasswordEmail)
                .orElseGet(() -> {
                    // Recorded even though the response tells the caller nothing useful: a run of
                    // reset requests against addresses that do not exist is someone guessing at who
                    // works here.
                    auditService.recordAs(null, email,
                            AuditEventType.REJECTED, AuditModule.SYSTEM, "PASSWORD_RESET_UNKNOWN_USER",
                            "Password reset requested for unknown address " + email,
                            AuditSeverity.WARNING);
                    return createResponse(false, "Incorrect user email address", null);
                });
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
                null,
                null,
                null,
                null,
                "reset"
        );

        if (!emailSent) {
            return createResponse(
                    false,
                    "Failed to send verification email to: " + user.getEmail(),
                    null
            );
        }

        auditService.recordAs(user, user.getEmail(),
                AuditEventType.SYSTEM, AuditModule.SYSTEM, "PASSWORD_RESET_REQUESTED",
                "Requested a password reset link", AuditSeverity.WARNING);

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

        // Critical: a completed password change is the point at which control of an account can pass
        // to someone else. If a reset was not expected, this is the row that says when.
        auditService.recordAs(user, user.getEmail(),
                AuditEventType.UPDATED, AuditModule.USERS, "PASSWORD_RESET_COMPLETED",
                "Password was reset", AuditSeverity.CRITICAL);

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

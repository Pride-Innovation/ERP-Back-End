/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.authentication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import pride.bank.erp.advice.WafStatus;
import pride.bank.erp.auditing.trail.AuditService;
import pride.bank.erp.auditing.trail.AuditSeverity;
import pride.bank.erp.authentication.directory.DirectoryAuthClient;
import pride.bank.erp.authentication.directory.DirectoryAuthSettings;
import pride.bank.erp.authentication.directory.DirectoryResult;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.exception.SignInException;
import pride.bank.erp.notification.Email;
import pride.bank.erp.title.TitleService;
import pride.bank.erp.token.TokenRepository;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Sign-in in LDAP mode: AD checks the password, this application keeps authorization and tokens.
 */
@ExtendWith(MockitoExtension.class)
class DirectorySignInTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private TokenRepository tokenRepository;
    @Mock private Email email;
    @Mock private TitleService titleService;
    @Mock private AuditService auditService;
    @Mock private DirectoryAuthSettings settings;
    @Mock private DirectoryAuthClient client;

    @InjectMocks
    private AuthenticationService service;

    private User account(String address) {
        User user = User.builder().id(7L).email(address).firstName("Sam").lastName("Odong")
                .isEnabled(true).isAccountNonLocked(true).build();
        when(userRepository.findByEmail(address)).thenReturn(Optional.of(user));
        return user;
    }

    private AuthenticationRequest request(String address, String password) {
        return AuthenticationRequest.builder().email(address).password(password).build();
    }

    private void ldap() {
        when(settings.isLdap()).thenReturn(true);
    }

    @Test
    void aTestAccountSignsInWithThePasswordOfThePersonItBelongsTo() {
        ldap();
        account("sodong12@pridebank.co.ug");
        when(client.authenticate("sodong", "AD-pass")).thenReturn(DirectoryResult.success("SOdong", "sodong@pridebank.co.ug"));
        when(jwtService.generateToken(any(User.class))).thenReturn("access");
        when(jwtService.generateRefreshToken(any(User.class))).thenReturn("refresh");

        AuthenticationResponse response = service.authenticate(request("sodong12@pridebank.co.ug", "AD-pass"));

        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getEmail()).isEqualTo("sodong12@pridebank.co.ug");
        // AD alone checks the password: the local hash is not consulted, by either path.
        verifyNoInteractions(passwordEncoder, authenticationManager);
        verify(auditService).recordAs(any(), anyString(), any(), any(), eq("LOGIN_SUCCEEDED"), anyString(), eq(AuditSeverity.INFO));
    }

    @Test
    void anUnknownAddressIsAnsweredLikeAWrongPasswordAndAdIsNotAsked() {
        when(userRepository.findByEmail("nobody@pridebank.co.ug")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate(request("nobody@pridebank.co.ug", "x")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(AuthenticationService.WRONG_CREDENTIALS);
        verifyNoInteractions(client);
    }

    /** A disabled account must not spend its owner's shared five-attempt AD allowance. */
    @Test
    void aDisabledAccountIsRefusedBeforeAdIsAsked() {
        User user = account("jdoe@pridebank.co.ug");
        user.setEnabled(false);

        assertThatThrownBy(() -> service.authenticate(request("jdoe@pridebank.co.ug", "x")))
                .isInstanceOf(DisabledException.class);
        verifyNoInteractions(client);
    }

    /** AD throttles; a counter of our own would block sooner, and separately per test account. */
    @Test
    void aWrongPasswordIsRefusedWithoutBlockingTheAccount() {
        ldap();
        User user = account("jdoe@pridebank.co.ug");
        when(client.authenticate("jdoe", "wrong")).thenReturn(DirectoryResult.failure(DirectoryResult.INVALID_CREDENTIALS));

        assertThatThrownBy(() -> service.authenticate(request("jdoe@pridebank.co.ug", "wrong")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage(AuthenticationService.WRONG_CREDENTIALS);
        assertThat(user.isBlocked()).isFalse();
        assertThat(user.getFailedLoginAttempts()).isZero();
        verify(userRepository, never()).save(any());
    }

    @Test
    void aThrottledUsernameSaysToWait() {
        ldap();
        account("jdoe@pridebank.co.ug");
        when(client.authenticate("jdoe", "x")).thenReturn(DirectoryResult.failure(DirectoryResult.USER_THROTTLED));

        assertThatThrownBy(() -> service.authenticate(request("jdoe@pridebank.co.ug", "x")))
                .isInstanceOfSatisfying(SignInException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(DirectoryResult.USER_THROTTLED);
                    assertThat(e.getStatus().value()).isEqualTo(401);
                });
    }

    /** The service's fault or ours, never the employee's: a 440 the firewall admits, and an alert. */
    @Test
    void brokenApplicationCredentialsAreUnavailableNotAWrongPassword() {
        ldap();
        account("jdoe@pridebank.co.ug");
        when(client.authenticate("jdoe", "x")).thenReturn(DirectoryResult.failure(DirectoryResult.INVALID_CLIENT));

        assertThatThrownBy(() -> service.authenticate(request("jdoe@pridebank.co.ug", "x")))
                .isInstanceOfSatisfying(SignInException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(AuthenticationService.SIGN_IN_UNAVAILABLE);
                    assertThat(e.getStatus()).isEqualTo(WafStatus.SERVER_FAULT);
                });
        verify(auditService).recordAs(any(), anyString(), any(), any(),
                eq("LOGIN_REFUSED_DIRECTORY_UNAVAILABLE"), anyString(), eq(AuditSeverity.CRITICAL));
    }

    /** AD vouching for someone else means the password belonged to someone else. */
    @Test
    void aDifferentIdentityFromAdIsRefused() {
        ldap();
        account("jdoe2@pridebank.co.ug");
        when(client.authenticate("jdoe", "x")).thenReturn(DirectoryResult.success("jdoe.other", null));

        assertThatThrownBy(() -> service.authenticate(request("jdoe2@pridebank.co.ug", "x")))
                .isInstanceOf(BadCredentialsException.class);
        verify(auditService).recordAs(any(), anyString(), any(), any(),
                eq("LOGIN_REFUSED_IDENTITY_MISMATCH"), anyString(), eq(AuditSeverity.CRITICAL));
        verify(jwtService, never()).generateToken(any(User.class));
    }

    @Test
    void localModeNeverCallsAd() {
        when(settings.isLdap()).thenReturn(false);
        account("jdoe@pridebank.co.ug");
        lenient().when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.authenticate(request("jdoe@pridebank.co.ug", "x")))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(client);
    }

    @Test
    void theLoginRequestNeverPrintsThePassword() {
        assertThat(request("jdoe@pridebank.co.ug", "Secret#123").toString()).doesNotContain("Secret#123");
    }
}

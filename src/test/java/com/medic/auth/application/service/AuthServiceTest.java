package com.medic.auth.application.service;

import com.medic.auth.domain.exception.*;
import com.medic.auth.domain.model.*;
import com.medic.auth.infrastructure.messaging.OutboxUserEventPublisher;
import com.medic.auth.infrastructure.persistence.*;
import com.medic.auth.infrastructure.rest.dto.LoginResponse;
import com.medic.auth.infrastructure.rest.dto.UserResponse;
import com.medic.auth.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private JpaUserRepository userRepository;
    @Mock
    private JpaRoleRepository roleRepository;
    @Mock
    private JpaEmailVerificationTokenRepository verificationTokenRepository;
    @Mock
    private JpaRefreshTokenRepository refreshTokenRepository;
    @Mock
    private JpaPasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private OutboxUserEventPublisher eventPublisher;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private AuthenticationManager authenticationManager;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, roleRepository, verificationTokenRepository,
                refreshTokenRepository, passwordResetTokenRepository, eventPublisher,
                passwordEncoder, tokenProvider, authenticationManager
        );
    }

    @Test
    void register_ShouldCreateUserAndPublishEvent_WhenEmailDoesNotExist() {
        // Given
        String email = "test@test.com";
        String password = "password123";
        String fullName = "Test User";
        String phone = "+56912345678";

        Role patientRole = new Role(UserRole.PACIENTE);
        patientRole.setId(1L);

        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(roleRepository.findByName(UserRole.PACIENTE)).thenReturn(Optional.of(patientRole));
        when(passwordEncoder.encode(password)).thenReturn("hashedPassword");

        User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setEmail(email);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        when(verificationTokenRepository.save(any(EmailVerificationToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        authService.register(email, password, fullName, phone);

        // Then
        verify(userRepository).existsByEmail(email);
        verify(userRepository).save(any(User.class));
        verify(verificationTokenRepository).save(any(EmailVerificationToken.class));
        verify(eventPublisher).publishUserRegistered(any(User.class), anyString());
    }

    @Test
    void register_ShouldThrowException_WhenEmailAlreadyExists() {
        // Given
        String email = "existing@test.com";
        when(userRepository.existsByEmail(email)).thenReturn(true);

        // When & Then
        assertThrows(UserAlreadyExistsException.class, () ->
                authService.register(email, "password", "Test", "+56912345678")
        );

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishUserRegistered(any(), any());
    }

    @Test
    void login_ShouldThrowException_WhenCredentialsAreInvalid() {
        // Given
        String email = "test@test.com";
        String password = "wrongpassword";

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        // When & Then
        assertThrows(InvalidCredentialsException.class, () ->
                authService.login(email, password)
        );

        verify(userRepository, never()).findByEmail(any());
        verify(tokenProvider, never()).generateAccessToken(any());
    }

    @Test
    void verifyEmail_ShouldThrowException_WhenTokenIsExpired() {
        // Given
        String tokenString = "expired-token";
        EmailVerificationToken token = new EmailVerificationToken();
        token.setToken(tokenString);
        token.setUserId(1L);
        token.setExpiresAt(LocalDateTime.now().minusHours(1));

        when(verificationTokenRepository.findByToken(tokenString))
                .thenReturn(Optional.of(token));

        // When & Then
        assertThrows(TokenExpiredException.class, () ->
                authService.verifyEmail(tokenString)
        );

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEmailVerified(any());
    }

    @Test
    void verifyEmail_ShouldActivateUser_WhenTokenIsValid() {
        // Given
        String tokenString = "valid-token";
        EmailVerificationToken token = new EmailVerificationToken();
        token.setToken(tokenString);
        token.setUserId(1L);
        token.setExpiresAt(LocalDateTime.now().plusHours(1));

        User user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        user.setEmailVerified(false);
        user.setActive(false);

        when(verificationTokenRepository.findByToken(tokenString)).thenReturn(Optional.of(token));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        // When
        authService.verifyEmail(tokenString);

        // Then
        verify(userRepository).save(argThat(u -> u.getEmailVerified() && u.getActive()));
        verify(eventPublisher).publishEmailVerified(any(User.class));
    }

    @Test
    void login_ShouldReturnLoginResponse_WhenCredentialsAreValid() {
        // Given
        String email = "test@test.com";
        String password = "password123";

        Role role = new Role(UserRole.PACIENTE);
        role.setId(1L);

        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setRole(role);
        user.setEmailVerified(true);
        user.setActive(true);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mock(Authentication.class));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(tokenProvider.generateAccessToken(user)).thenReturn("access.token.here");
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        LoginResponse response = authService.login(email, password);

        // Then
        assertNotNull(response);
        assertEquals("access.token.here", response.accessToken());
        assertEquals(1L, response.userId());
        verify(refreshTokenRepository).revokeAllByUserId(1L);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void login_ShouldThrowException_WhenEmailNotVerified() {
        // Given
        String email = "unverified@test.com";

        Role role = new Role(UserRole.PACIENTE);
        User user = new User();
        user.setId(2L);
        user.setEmail(email);
        user.setRole(role);
        user.setEmailVerified(false);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mock(Authentication.class));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        // When & Then
        assertThrows(InvalidCredentialsException.class, () -> authService.login(email, "pass"));
        verify(tokenProvider, never()).generateAccessToken(any());
    }

    @Test
    void refreshToken_ShouldRotateTokens_WhenRefreshTokenIsValid() {
        // Given
        String tokenString = "valid-refresh-token";

        Role role = new Role(UserRole.PACIENTE);
        User user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        user.setRole(role);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(tokenString);
        refreshToken.setUserId(1L);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(7));
        refreshToken.setRevoked(false);

        when(refreshTokenRepository.findByToken(tokenString)).thenReturn(Optional.of(refreshToken));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(tokenProvider.generateAccessToken(user)).thenReturn("new.access.token");
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        LoginResponse response = authService.refreshToken(tokenString);

        // Then
        assertNotNull(response);
        assertEquals("new.access.token", response.accessToken());
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void refreshToken_ShouldThrowException_WhenTokenIsRevoked() {
        // Given
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("revoked-token");
        refreshToken.setUserId(1L);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(7));
        refreshToken.setRevoked(true);

        when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(refreshToken));

        // When & Then
        assertThrows(InvalidTokenException.class, () -> authService.refreshToken("revoked-token"));
    }

    @Test
    void refreshToken_ShouldThrowException_WhenTokenIsExpired() {
        // Given
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("expired-refresh-token");
        refreshToken.setUserId(1L);
        refreshToken.setExpiresAt(LocalDateTime.now().minusDays(1));
        refreshToken.setRevoked(false);

        when(refreshTokenRepository.findByToken("expired-refresh-token")).thenReturn(Optional.of(refreshToken));

        // When & Then
        assertThrows(TokenExpiredException.class, () -> authService.refreshToken("expired-refresh-token"));
    }

    @Test
    void logout_ShouldRevokeRefreshToken() {
        // Given
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("some-refresh-token");
        refreshToken.setUserId(1L);
        refreshToken.setRevoked(false);

        when(refreshTokenRepository.findByToken("some-refresh-token")).thenReturn(Optional.of(refreshToken));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        authService.logout("some-refresh-token");

        // Then
        verify(refreshTokenRepository).save(argThat(rt -> rt.getRevoked()));
    }

    @Test
    void forgotPassword_ShouldCreateTokenAndPublishEvent_WhenUserExists() {
        // Given
        String email = "user@test.com";
        Role role = new Role(UserRole.PACIENTE);
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setRole(role);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        authService.forgotPassword(email);

        // Then
        verify(passwordResetTokenRepository).deleteByUserId(1L);
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(eventPublisher).publishPasswordResetRequested(any(User.class), anyString());
    }

    @Test
    void forgotPassword_ShouldDoNothing_WhenUserDoesNotExist() {
        // Given
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        // When
        authService.forgotPassword("nobody@test.com");

        // Then
        verify(passwordResetTokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishPasswordResetRequested(any(), any());
    }

    @Test
    void resetPassword_ShouldChangePassword_WhenTokenIsValid() {
        // Given
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken("reset-token");
        resetToken.setUserId(1L);
        resetToken.setExpiresAt(LocalDateTime.now().plusHours(1));
        resetToken.setUsed(false);

        User user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");

        when(passwordResetTokenRepository.findByToken("reset-token")).thenReturn(Optional.of(resetToken));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword123!")).thenReturn("newHashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        authService.resetPassword("reset-token", "newPassword123!");

        // Then
        verify(userRepository).save(argThat(u -> "newHashedPassword".equals(u.getPasswordHash())));
        verify(passwordResetTokenRepository).save(argThat(t -> t.getUsed()));
        verify(refreshTokenRepository).revokeAllByUserId(1L);
    }

    @Test
    void resetPassword_ShouldThrowException_WhenTokenAlreadyUsed() {
        // Given
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken("used-token");
        resetToken.setUserId(1L);
        resetToken.setExpiresAt(LocalDateTime.now().plusHours(1));
        resetToken.setUsed(true);

        when(passwordResetTokenRepository.findByToken("used-token")).thenReturn(Optional.of(resetToken));

        // When & Then
        assertThrows(InvalidTokenException.class,
                () -> authService.resetPassword("used-token", "newPassword123!"));
    }

    @Test
    void resetPassword_ShouldThrowException_WhenTokenIsExpired() {
        // Given
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken("expired-token");
        resetToken.setUserId(1L);
        resetToken.setExpiresAt(LocalDateTime.now().minusHours(1));
        resetToken.setUsed(false);

        when(passwordResetTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(resetToken));

        // When & Then
        assertThrows(TokenExpiredException.class,
                () -> authService.resetPassword("expired-token", "newPassword123!"));
    }

    @Test
    void updateProfile_ShouldUpdateUser_WhenUserExists() {
        // Given
        Role role = new Role(UserRole.PACIENTE);
        role.setId(1L);

        User user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        user.setFullName("Old Name");
        user.setPhone("+56900000000");
        user.setRole(role);
        user.setActive(true);
        user.setEmailVerified(true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        // When
        UserResponse response = authService.updateProfile(1L, "New Name", "+56911111111");

        // Then
        verify(userRepository).save(argThat(u ->
                "New Name".equals(u.getFullName()) && "+56911111111".equals(u.getPhone())));
        assertNotNull(response);
    }

    @Test
    void updateProfile_ShouldThrowException_WhenUserDoesNotExist() {
        // Given
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(UserNotFoundException.class,
                () -> authService.updateProfile(99L, "Name", "+56911111111"));
    }

    @Test
    void resendVerification_ShouldCreateNewToken_WhenUserNotVerified() {
        // Given
        User user = new User();
        user.setId(1L);
        user.setEmail("unverified@test.com");
        user.setEmailVerified(false);

        when(userRepository.findByEmail("unverified@test.com")).thenReturn(Optional.of(user));
        when(verificationTokenRepository.save(any(EmailVerificationToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        authService.resendVerification("unverified@test.com");

        // Then
        verify(verificationTokenRepository).deleteByUserId(1L);
        verify(verificationTokenRepository).save(any(EmailVerificationToken.class));
        verify(eventPublisher).publishUserRegistered(any(User.class), anyString());
    }

    @Test
    void resendVerification_ShouldDoNothing_WhenUserAlreadyVerified() {
        // Given
        User user = new User();
        user.setId(1L);
        user.setEmail("verified@test.com");
        user.setEmailVerified(true);

        when(userRepository.findByEmail("verified@test.com")).thenReturn(Optional.of(user));

        // When
        authService.resendVerification("verified@test.com");

        // Then
        verify(verificationTokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishUserRegistered(any(), any());
    }
}

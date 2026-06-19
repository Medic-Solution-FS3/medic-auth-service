package com.medic.auth.application.service;

import com.medic.auth.domain.exception.*;
import com.medic.auth.domain.model.*;
import com.medic.auth.infrastructure.messaging.OutboxUserEventPublisher;
import com.medic.auth.infrastructure.persistence.*;
import com.medic.auth.infrastructure.rest.dto.LoginResponse;
import com.medic.auth.infrastructure.rest.dto.UserResponse;
import com.medic.auth.infrastructure.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    @Value("${auth.token.email-verification-expiry-hours:24}")
    private long emailVerificationExpiryHours;

    @Value("${auth.token.password-reset-expiry-hours:1}")
    private long passwordResetExpiryHours;

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final JpaUserRepository userRepository;
    private final JpaRoleRepository roleRepository;
    private final JpaEmailVerificationTokenRepository verificationTokenRepository;
    private final JpaRefreshTokenRepository refreshTokenRepository;
    private final JpaPasswordResetTokenRepository passwordResetTokenRepository;
    private final OutboxUserEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;

    public AuthService(JpaUserRepository userRepository,
                       JpaRoleRepository roleRepository,
                       JpaEmailVerificationTokenRepository verificationTokenRepository,
                       JpaRefreshTokenRepository refreshTokenRepository,
                       JpaPasswordResetTokenRepository passwordResetTokenRepository,
                       OutboxUserEventPublisher eventPublisher,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.eventPublisher = eventPublisher;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public void register(String email, String password, String fullName, String phone) {
        logger.info("Registering new user");

        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException(email);
        }

        Role patientRole = roleRepository.findByName(UserRole.PACIENTE)
                .orElseThrow(() -> new IllegalStateException("PACIENTE role not found"));

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setRole(patientRole);
        user.setActive(false);
        user.setEmailVerified(false);

        User savedUser = userRepository.save(user);
        logger.info("User created with id: {}", savedUser.getId());

        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId(savedUser.getId());
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(LocalDateTime.now().plusHours(emailVerificationExpiryHours));
        verificationTokenRepository.save(token);

        eventPublisher.publishUserRegistered(savedUser, token.getToken());
        logger.info("User registration event published for userId: {}", savedUser.getId());
    }

    /**
     * Verifies the user's email address and activates the account.
     * <p>
     * State transition: {@code emailVerified=false, active=false} → {@code emailVerified=true, active=true}.
     * The verification token is single-use and deleted after successful verification.
     * An {@link com.medic.auth.infrastructure.messaging.event.EmailVerifiedEvent} is published via the outbox.
     *
     * @throws com.medic.auth.domain.exception.InvalidTokenException if the token does not exist
     * @throws com.medic.auth.domain.exception.TokenExpiredException if the token is older than 24 hours
     */
    @Transactional
    public void verifyEmail(String tokenString) {
        logger.info("Verifying email with token");

        EmailVerificationToken token = verificationTokenRepository.findByToken(tokenString)
                .orElseThrow(() -> new InvalidTokenException("Invalid verification token"));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException("Verification token expired");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new UserNotFoundException(token.getUserId()));

        user.setEmailVerified(true);
        user.setActive(true);
        userRepository.save(user);

        verificationTokenRepository.deleteByUserId(user.getId());

        eventPublisher.publishEmailVerified(user);
        logger.info("Email verified for userId: {}", user.getId());
    }

    /**
     * Authenticates a user and issues a new token pair.
     * <p>
     * Email verification is enforced before issuing tokens; an unverified account returns the same
     * {@link com.medic.auth.domain.exception.InvalidCredentialsException} as a wrong password to prevent
     * user enumeration. All existing refresh tokens for the user are revoked before a new one is created.
     *
     * @return {@link com.medic.auth.infrastructure.rest.dto.LoginResponse} containing the RS256 access token
     *         and a new refresh token UUID
     * @throws com.medic.auth.domain.exception.InvalidCredentialsException if credentials are wrong or email is unverified
     */
    @Transactional
    public LoginResponse login(String email, String password) {
        logger.info("Login attempt");

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );
        } catch (AuthenticationException e) {
            logger.warn("Authentication failed");
            throw new InvalidCredentialsException();
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.getEmailVerified()) {
            // Return same error as invalid credentials to prevent user enumeration
            throw new InvalidCredentialsException();
        }

        String accessToken = tokenProvider.generateAccessToken(user);

        // Revoke all existing refresh tokens before issuing a new one
        refreshTokenRepository.revokeAllByUserId(user.getId());

        String refreshTokenString = UUID.randomUUID().toString();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setToken(refreshTokenString);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(7));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);

        logger.info("Login successful for userId: {}", user.getId());
        return new LoginResponse(accessToken, refreshTokenString, user.getId(), user.getEmail());
    }

    /**
     * Rotates the refresh token and issues a new access token.
     * <p>
     * The incoming refresh token is immediately revoked and a new one is persisted before returning,
     * so replaying a stolen token after the first use will be rejected. Expiry and revocation are
     * checked before any token is issued.
     *
     * @throws com.medic.auth.domain.exception.InvalidTokenException  if the token is unknown or already revoked
     * @throws com.medic.auth.domain.exception.TokenExpiredException  if the refresh token lifetime has elapsed
     */
    @Transactional
    public LoginResponse refreshToken(String refreshTokenString) {
        logger.info("Refreshing token");

        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenString)
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException("Refresh token expired");
        }

        if (refreshToken.getRevoked()) {
            throw new InvalidTokenException("Refresh token revoked");
        }

        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new UserNotFoundException(refreshToken.getUserId()));

        String newAccessToken = tokenProvider.generateAccessToken(user);

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        String newRefreshTokenString = UUID.randomUUID().toString();
        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setUserId(user.getId());
        newRefreshToken.setToken(newRefreshTokenString);
        newRefreshToken.setExpiresAt(LocalDateTime.now().plusDays(7));
        newRefreshToken.setRevoked(false);
        refreshTokenRepository.save(newRefreshToken);

        logger.info("Token rotation complete for userId: {}", user.getId());
        return new LoginResponse(newAccessToken, newRefreshTokenString, user.getId(), user.getEmail());
    }

    @Transactional
    public void logout(String refreshTokenString) {
        logger.info("Logging out user");

        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenString)
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        logger.info("Refresh token revoked for userId: {}", refreshToken.getUserId());
    }

    @Transactional
    public void forgotPassword(String email) {
        logger.info("Forgot password requested");

        userRepository.findByEmail(email).ifPresent(user -> {
            passwordResetTokenRepository.deleteByUserId(user.getId());

            PasswordResetToken token = new PasswordResetToken();
            token.setUserId(user.getId());
            token.setToken(UUID.randomUUID().toString());
            token.setExpiresAt(LocalDateTime.now().plusHours(passwordResetExpiryHours));
            passwordResetTokenRepository.save(token);

            eventPublisher.publishPasswordResetRequested(user, token.getToken());
            logger.info("PasswordResetRequested event published for userId: {}", user.getId());
        });
    }

    /**
     * Resets the user's password using a single-use token.
     * <p>
     * The token is validated for existence, expiry, and prior use before the password is changed.
     * On success the token is marked as used (preventing replay) and all active refresh tokens are
     * revoked, forcing re-authentication with the new credentials across all sessions.
     *
     * @throws com.medic.auth.domain.exception.InvalidTokenException if the token is unknown or already used
     * @throws com.medic.auth.domain.exception.TokenExpiredException if the reset link has expired (1 hour TTL)
     */
    @Transactional
    public void resetPassword(String tokenString, String newPassword) {
        logger.info("Reset password attempt");

        PasswordResetToken token = passwordResetTokenRepository.findByToken(tokenString)
                .orElseThrow(() -> new InvalidTokenException("Invalid password reset token"));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException("Password reset token expired");
        }

        if (token.getUsed()) {
            throw new InvalidTokenException("Password reset token already used");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new UserNotFoundException(token.getUserId()));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setUsed(true);
        passwordResetTokenRepository.save(token);

        // Revoke all sessions: new password means all existing refresh tokens are invalid
        refreshTokenRepository.revokeAllByUserId(user.getId());

        logger.info("Password reset successful for userId: {}", user.getId());
    }

    @Transactional
    public UserResponse updateProfile(Long userId, String fullName, String phone) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName);
        }
        if (phone != null && !phone.isBlank()) {
            user.setPhone(phone);
        }

        User saved = userRepository.save(user);
        logger.info("Profile updated for userId: {}", userId);

        return new UserResponse(
                saved.getId(),
                saved.getEmail(),
                saved.getFullName(),
                saved.getPhone(),
                saved.getRole().getName().name(),
                saved.getActive(),
                saved.getEmailVerified(),
                saved.getCreatedAt()
        );
    }

    @Transactional
    public void resendVerification(String email) {
        logger.info("Resend verification requested");

        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getEmailVerified()) {
                return;
            }
            verificationTokenRepository.deleteByUserId(user.getId());

            EmailVerificationToken token = new EmailVerificationToken();
            token.setUserId(user.getId());
            token.setToken(UUID.randomUUID().toString());
            token.setExpiresAt(LocalDateTime.now().plusHours(emailVerificationExpiryHours));
            verificationTokenRepository.save(token);

            eventPublisher.publishUserRegistered(user, token.getToken());
            logger.info("Verification email re-queued for userId: {}", user.getId());
        });
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
    }
}

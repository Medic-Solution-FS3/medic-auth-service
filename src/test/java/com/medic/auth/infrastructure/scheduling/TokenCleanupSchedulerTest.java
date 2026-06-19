package com.medic.auth.infrastructure.scheduling;

import com.medic.auth.infrastructure.persistence.JpaEmailVerificationTokenRepository;
import com.medic.auth.infrastructure.persistence.JpaPasswordResetTokenRepository;
import com.medic.auth.infrastructure.persistence.JpaRefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TokenCleanupSchedulerTest {

    @Mock
    private JpaEmailVerificationTokenRepository verificationTokenRepository;
    @Mock
    private JpaRefreshTokenRepository refreshTokenRepository;
    @Mock
    private JpaPasswordResetTokenRepository passwordResetTokenRepository;

    private TokenCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TokenCleanupScheduler(
                verificationTokenRepository, refreshTokenRepository, passwordResetTokenRepository);
    }

    @Test
    void cleanupExpiredTokens_ShouldDeleteExpiredVerificationTokens() {
        scheduler.cleanupExpiredTokens();
        verify(verificationTokenRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
    }

    @Test
    void cleanupExpiredTokens_ShouldDeleteExpiredPasswordResetTokens() {
        scheduler.cleanupExpiredTokens();
        verify(passwordResetTokenRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
    }

    @Test
    void cleanupExpiredTokens_ShouldDeleteOnlyRevokedExpiredRefreshTokens() {
        scheduler.cleanupExpiredTokens();
        verify(refreshTokenRepository).deleteByExpiresAtBeforeAndRevokedTrue(any(LocalDateTime.class));
    }
}

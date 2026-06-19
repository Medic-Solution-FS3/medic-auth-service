package com.medic.auth.infrastructure.scheduling;

import com.medic.auth.infrastructure.persistence.JpaEmailVerificationTokenRepository;
import com.medic.auth.infrastructure.persistence.JpaPasswordResetTokenRepository;
import com.medic.auth.infrastructure.persistence.JpaRefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class TokenCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(TokenCleanupScheduler.class);

    private final JpaEmailVerificationTokenRepository verificationTokenRepository;
    private final JpaRefreshTokenRepository refreshTokenRepository;
    private final JpaPasswordResetTokenRepository passwordResetTokenRepository;

    public TokenCleanupScheduler(
            JpaEmailVerificationTokenRepository verificationTokenRepository,
            JpaRefreshTokenRepository refreshTokenRepository,
            JpaPasswordResetTokenRepository passwordResetTokenRepository) {
        this.verificationTokenRepository = verificationTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @Scheduled(fixedRateString = "${auth.token.cleanup.interval-ms:3600000}")
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        logger.info("Running token cleanup at {}", now);

        verificationTokenRepository.deleteByExpiresAtBefore(now);
        passwordResetTokenRepository.deleteByExpiresAtBefore(now);

        // Only delete refresh tokens that are already revoked — active-but-expired tokens
        // are invalidated on use and revoked on the next login cycle.
        refreshTokenRepository.deleteByExpiresAtBeforeAndRevokedTrue(now);

        logger.info("Token cleanup complete");
    }
}

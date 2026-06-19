package com.medic.auth.infrastructure.persistence;

import com.medic.auth.domain.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface JpaPasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    void deleteByUserId(Long userId);
    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}

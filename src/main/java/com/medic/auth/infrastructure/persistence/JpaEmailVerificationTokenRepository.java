package com.medic.auth.infrastructure.persistence;

import com.medic.auth.domain.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface JpaEmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByToken(String token);
    void deleteByUserId(Long userId);
    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}

package com.tpanh.server.modules.auth.repository;

import com.tpanh.server.modules.auth.entity.VerificationCode;
import com.tpanh.server.modules.auth.enums.TokenType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, UUID> {
    Optional<VerificationCode> findByCode(String code);

    Optional<VerificationCode> findByUserIdAndType(UUID userId, TokenType type);

    void deleteByExpiryDateBefore(Instant now);
}
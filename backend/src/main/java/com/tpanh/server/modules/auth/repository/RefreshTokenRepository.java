package com.tpanh.server.modules.auth.repository;

import com.tpanh.server.modules.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);

    @Query("""
        select t from RefreshToken t inner join User u on t.user.id = u.id
        where u.id = :userId and (t.revoked = false )
    """)
    List<RefreshToken> findAllValidTokenByUser(UUID userId);

    void deleteByExpiryDateBeforeOrRevokedTrue(Instant now);
}

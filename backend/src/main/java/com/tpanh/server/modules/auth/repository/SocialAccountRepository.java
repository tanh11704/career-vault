package com.tpanh.server.modules.auth.repository;

import com.tpanh.server.modules.auth.entity.SocialAccount;
import com.tpanh.server.modules.auth.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, UUID> {
    Optional<SocialAccount> findByProviderAndProviderId(AuthProvider provider, String providerId);
}

package com.landata.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {
  Optional<AuthToken> findByTokenHashAndPurpose(String tokenHash, String purpose);
}

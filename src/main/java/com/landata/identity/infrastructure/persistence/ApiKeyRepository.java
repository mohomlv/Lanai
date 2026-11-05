package com.landata.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
  java.util.List<ApiKey> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
  Optional<ApiKey> findByIdAndCompanyId(UUID id, UUID companyId);
}

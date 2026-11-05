package com.landata.ai.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiProviderConfigRepository extends JpaRepository<AiProviderConfig, UUID> {
  Optional<AiProviderConfig> findByIdAndCompanyId(UUID id, UUID companyId);
  Optional<AiProviderConfig> findFirstByCompanyIdAndActiveTrue(UUID companyId);
  Optional<AiProviderConfig> findFirstByCompanyIdIsNullAndActiveTrue();
  List<AiProviderConfig> findByCompanyIdOrderByCreatedAtAsc(UUID companyId);
}

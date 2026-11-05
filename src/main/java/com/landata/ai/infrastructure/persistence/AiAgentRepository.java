package com.landata.ai.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAgentRepository extends JpaRepository<AiAgent, UUID> {
  Optional<AiAgent> findByIdAndCompanyId(UUID id, UUID companyId);
  Optional<AiAgent> findByProviderAgentId(String providerAgentId);
}

package com.landata.ai.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRequestRepository extends JpaRepository<AiRequest, UUID> {
  Optional<AiRequest> findByIdAndCompanyId(UUID id, UUID companyId);
  Optional<AiRequest> findByCompanyIdAndIdempotencyKey(UUID companyId, String idempotencyKey);
}

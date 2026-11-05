package com.landata.ai.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRequestArtifactRepository extends JpaRepository<AiRequestArtifact, UUID> {
  java.util.List<AiRequestArtifact> findByRequestId(UUID requestId);
}

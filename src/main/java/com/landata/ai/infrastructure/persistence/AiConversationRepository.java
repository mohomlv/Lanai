package com.landata.ai.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {
  Optional<AiConversation> findByIdAndCompanyId(UUID id, UUID companyId);
  List<AiConversation> findByCompanyIdAndUserIdOrderByUpdatedAtDesc(UUID companyId, UUID userId);
}

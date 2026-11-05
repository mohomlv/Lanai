package com.landata.ai.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {
  java.util.List<AiMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}

package com.landata.admin.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmbedTokenRepository extends JpaRepository<EmbedToken, UUID> {
  java.util.List<EmbedToken> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
}

package com.landata.knowledge.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocEmbeddingRepository extends JpaRepository<DocEmbedding, UUID> {
  void deleteByDocId(UUID docId);

  long countByDatasourceIdAndCompanyId(UUID datasourceId, UUID companyId);
}

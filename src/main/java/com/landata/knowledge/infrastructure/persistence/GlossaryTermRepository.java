package com.landata.knowledge.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GlossaryTermRepository extends JpaRepository<GlossaryTerm, UUID> {
  java.util.List<GlossaryTerm> findByCompanyIdAndDatasourceId(UUID companyId, UUID datasourceId);
  Optional<GlossaryTerm> findByIdAndCompanyId(UUID id, UUID companyId);
}

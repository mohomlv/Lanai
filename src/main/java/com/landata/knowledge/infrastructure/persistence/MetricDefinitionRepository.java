package com.landata.knowledge.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricDefinitionRepository extends JpaRepository<MetricDefinition, UUID> {
  java.util.List<MetricDefinition> findByCompanyIdAndDatasourceId(UUID companyId, UUID datasourceId);
  Optional<MetricDefinition> findByIdAndCompanyId(UUID id, UUID companyId);
}

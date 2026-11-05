package com.landata.knowledge.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchemaDocRepository extends JpaRepository<SchemaDoc, UUID> {
  java.util.List<SchemaDoc> findByDatasourceIdAndCompanyIdAndCurrentTrue(UUID datasourceId, UUID companyId);
  Optional<SchemaDoc> findByIdAndCompanyId(UUID id, UUID companyId);
  java.util.List<SchemaDoc> findByDatasourceIdAndTableIdAndKindOrderByVersionDesc(UUID datasourceId, UUID tableId, String kind);
  Optional<SchemaDoc> findFirstByDatasourceIdAndKindAndTableIdIsNullAndCurrentTrue(UUID datasourceId, String kind);
  Optional<SchemaDoc> findFirstByDatasourceIdAndTableIdAndKindAndCurrentTrue(UUID datasourceId, UUID tableId, String kind);
}

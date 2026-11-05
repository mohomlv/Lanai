package com.landata.datasource.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogTableRepository extends JpaRepository<CatalogTable, UUID> {
  java.util.List<CatalogTable> findByDatasourceIdAndCompanyId(UUID datasourceId, UUID companyId);
  Optional<CatalogTable> findByIdAndCompanyId(UUID id, UUID companyId);
  Optional<CatalogTable> findByDatasourceIdAndSchemaNameAndTableName(UUID datasourceId, String schemaName, String tableName);
}

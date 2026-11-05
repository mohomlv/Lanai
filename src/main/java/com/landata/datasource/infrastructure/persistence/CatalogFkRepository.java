package com.landata.datasource.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogFkRepository extends JpaRepository<CatalogFk, UUID> {
  java.util.List<CatalogFk> findByDatasourceIdAndCompanyId(UUID datasourceId, UUID companyId);
  Optional<CatalogFk> findByIdAndCompanyId(UUID id, UUID companyId);
  void deleteByDatasourceIdAndInferredTrue(UUID datasourceId);

  void deleteByDatasourceIdAndCompanyIdAndInferredFalse(UUID datasourceId, UUID companyId);
}

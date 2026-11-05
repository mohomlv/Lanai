package com.landata.datasource.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogColumnRepository extends JpaRepository<CatalogColumn, UUID> {
  java.util.List<CatalogColumn> findByTableIdOrderByOrdinalAsc(UUID tableId);
  Optional<CatalogColumn> findByIdAndCompanyId(UUID id, UUID companyId);
}

package com.landata.datasource.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasourceRepository extends JpaRepository<Datasource, UUID> {
  java.util.List<Datasource> findByCompanyIdAndDeletedAtIsNullOrderByNameAsc(UUID companyId);
  Optional<Datasource> findByIdAndCompanyIdAndDeletedAtIsNull(UUID id, UUID companyId);
  long countByCompanyIdAndDeletedAtIsNull(UUID companyId);
}

package com.landata.datasource.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasourcePermissionRepository extends JpaRepository<DatasourcePermission, UUID> {
  java.util.List<DatasourcePermission> findByDatasourceIdAndCompanyId(UUID datasourceId, UUID companyId);
  Optional<DatasourcePermission> findByDatasourceIdAndRole(UUID datasourceId, String role);
}

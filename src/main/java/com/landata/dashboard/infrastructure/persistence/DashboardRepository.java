package com.landata.dashboard.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardRepository extends JpaRepository<Dashboard, UUID> {
  java.util.List<Dashboard> findByCompanyIdAndArchivedAtIsNullOrderByUpdatedAtDesc(UUID companyId);
  Optional<Dashboard> findByIdAndCompanyId(UUID id, UUID companyId);
}

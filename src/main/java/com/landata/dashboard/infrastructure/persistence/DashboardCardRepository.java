package com.landata.dashboard.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardCardRepository extends JpaRepository<DashboardCard, UUID> {
  java.util.List<DashboardCard> findByDashboardId(UUID dashboardId);
  Optional<DashboardCard> findByIdAndCompanyId(UUID id, UUID companyId);
}

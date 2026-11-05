package com.landata.dashboard.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardTabRepository extends JpaRepository<DashboardTab, UUID> {
  java.util.List<DashboardTab> findByDashboardIdOrderByPositionAsc(UUID dashboardId);
  void deleteByDashboardId(UUID dashboardId);
}

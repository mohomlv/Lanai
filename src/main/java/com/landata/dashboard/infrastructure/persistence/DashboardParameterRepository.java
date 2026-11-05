package com.landata.dashboard.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardParameterRepository extends JpaRepository<DashboardParameter, UUID> {
  java.util.List<DashboardParameter> findByDashboardIdOrderByPositionAsc(UUID dashboardId);
  void deleteByDashboardId(UUID dashboardId);
}

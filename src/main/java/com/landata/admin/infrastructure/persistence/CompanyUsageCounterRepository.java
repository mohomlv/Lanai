package com.landata.admin.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyUsageCounterRepository extends JpaRepository<CompanyUsageCounter, CompanyUsageKey> {
  Optional<CompanyUsageCounter> findByCompanyIdAndPeriod(UUID companyId, java.time.LocalDate period);
}

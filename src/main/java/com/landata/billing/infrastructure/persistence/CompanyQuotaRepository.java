package com.landata.billing.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyQuotaRepository extends JpaRepository<CompanyQuota, UUID> {
}

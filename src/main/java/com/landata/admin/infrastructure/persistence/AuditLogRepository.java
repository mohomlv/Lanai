package com.landata.admin.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
  org.springframework.data.domain.Page<AuditLog> findByCompanyIdOrderByCreatedAtDesc(UUID companyId, org.springframework.data.domain.Pageable pageable);
}

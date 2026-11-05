package com.landata.query.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueryRunRepository extends JpaRepository<QueryRun, UUID> {
  java.util.List<QueryRun> findByCompanyIdOrderByStartedAtDesc(UUID companyId);
  Optional<QueryRun> findByIdAndCompanyId(UUID id, UUID companyId);
}

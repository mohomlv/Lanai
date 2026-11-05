package com.landata.knowledge.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScrapeJobRepository extends JpaRepository<ScrapeJob, UUID> {
  Optional<ScrapeJob> findByIdAndCompanyId(UUID id, UUID companyId);
}

package com.landata.knowledge.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScrapeJobItemRepository extends JpaRepository<ScrapeJobItem, UUID> {
  java.util.List<ScrapeJobItem> findByJobIdOrderByUpdatedAtAsc(UUID jobId);
  Optional<ScrapeJobItem> findByIdAndCompanyId(UUID id, UUID companyId);
}

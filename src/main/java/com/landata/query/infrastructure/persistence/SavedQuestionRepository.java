package com.landata.query.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedQuestionRepository extends JpaRepository<SavedQuestion, UUID> {
  java.util.List<SavedQuestion> findByCompanyIdAndArchivedAtIsNullOrderByUpdatedAtDesc(UUID companyId);
  java.util.List<SavedQuestion> findByCompanyIdAndParameterQuestionTrueAndArchivedAtIsNullOrderByNameAsc(UUID companyId);
  Optional<SavedQuestion> findByIdAndCompanyId(UUID id, UUID companyId);
}

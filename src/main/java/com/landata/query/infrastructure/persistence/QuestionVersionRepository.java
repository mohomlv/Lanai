package com.landata.query.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionVersionRepository extends JpaRepository<QuestionVersion, UUID> {
  java.util.List<QuestionVersion> findByQuestionIdOrderByVersionNoDesc(UUID questionId);
  Optional<QuestionVersion> findByQuestionIdAndVersionNo(UUID questionId, int versionNo);
}

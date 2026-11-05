package com.landata.viz.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChartTemplateRepository extends JpaRepository<ChartTemplate, UUID> {
  java.util.List<ChartTemplate> findByCompanyIdIsNullAndActiveTrueOrderByKeyAsc();
  Optional<ChartTemplate> findFirstByCompanyIdIsNullAndKeyAndActiveTrue(String key);
}

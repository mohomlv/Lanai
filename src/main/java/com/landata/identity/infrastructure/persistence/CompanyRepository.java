package com.landata.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
  Optional<Company> findByIdAndDeletedAtIsNull(UUID id);
  Optional<Company> findBySlugAndDeletedAtIsNull(String slug);
}

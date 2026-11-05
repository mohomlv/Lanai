package com.landata.query.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionFolderRepository extends JpaRepository<CollectionFolder, UUID> {
  java.util.List<CollectionFolder> findByCompanyIdAndArchivedAtIsNullOrderByNameAsc(UUID companyId);
  Optional<CollectionFolder> findByIdAndCompanyId(UUID id, UUID companyId);
}

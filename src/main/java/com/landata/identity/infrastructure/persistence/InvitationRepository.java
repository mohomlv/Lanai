package com.landata.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
  Optional<Invitation> findByTokenHash(String tokenHash);
  Optional<Invitation> findByIdAndCompanyId(UUID id, UUID companyId);
  java.util.List<Invitation> findByCompanyIdAndStatus(UUID companyId, String status);
}

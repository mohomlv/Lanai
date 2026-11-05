package com.landata.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {
  Optional<Membership> findByCompanyIdAndUserId(UUID companyId, UUID userId);
  java.util.List<Membership> findByUserIdAndStatus(UUID userId, String status);
  java.util.List<Membership> findByCompanyIdAndStatus(UUID companyId, String status);
}

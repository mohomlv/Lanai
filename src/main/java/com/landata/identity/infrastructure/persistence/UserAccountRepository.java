package com.landata.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
  Optional<UserAccount> findByEmailIgnoreCase(String email);
  Optional<UserAccount> findByUsernameIgnoreCase(String username);
}

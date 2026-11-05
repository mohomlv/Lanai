package com.landata.shared.security;

import java.util.List;
import java.util.UUID;

public record AuthPrincipal(
    UUID userId,
    UUID companyId,
    String role,
    String email,
    String username,
    List<Integer> branchIds
) {
  public AuthPrincipal {
    branchIds = branchIds == null ? List.of() : List.copyOf(branchIds);
  }

  public AuthPrincipal(UUID userId, UUID companyId, String role, String email) {
    this(userId, companyId, role, email, null, List.of());
  }
}

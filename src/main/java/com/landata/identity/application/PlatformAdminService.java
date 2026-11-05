package com.landata.identity.application;

import com.landata.identity.infrastructure.persistence.Membership;
import com.landata.identity.infrastructure.persistence.MembershipRepository;
import com.landata.identity.infrastructure.persistence.UserAccount;
import com.landata.shared.api.ApiException;
import com.landata.shared.config.LandataProperties;
import com.landata.shared.security.AuthPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PlatformAdminService {
  private final LandataProperties properties;
  private final MembershipRepository memberships;

  public PlatformAdminService(LandataProperties properties, MembershipRepository memberships) {
    this.properties = properties;
    this.memberships = memberships;
  }

  public boolean isAdmin(String email, UUID userId) {
    if (properties.isPlatformAdminEmail(email)) return true;
    if (userId == null || properties.getPlatformAdminBranchCodes().isEmpty()) return false;
    return memberships.findByUserIdAndStatus(userId, "active").stream()
        .anyMatch(membership -> properties.hasPlatformAdminBranch(membership.getBranchIds()));
  }

  public boolean isAdmin(AuthPrincipal principal) {
    if (principal == null) return false;
    if (properties.isPlatformAdminEmail(principal.email())) return true;
    return properties.hasPlatformAdminBranch(principal.branchIds());
  }

  public void require(AuthPrincipal principal) {
    if (!isAdmin(principal)) {
      throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only a platform admin can do this");
    }
  }

  public void elevate(UserAccount user, Membership membership) {
    if (user == null || !isAdmin(user.getEmail(), user.getId())) return;
    if (membership == null) return;
    if ("owner".equals(membership.getRole()) || "admin".equals(membership.getRole())) return;
    membership.setRole("admin");
  }

  public List<Integer> adminBranchCodes() {
    return List.copyOf(properties.getPlatformAdminBranchCodes());
  }
}

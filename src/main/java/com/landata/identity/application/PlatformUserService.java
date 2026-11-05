package com.landata.identity.application;

import com.landata.identity.infrastructure.persistence.Company;
import com.landata.identity.infrastructure.persistence.CompanyRepository;
import com.landata.identity.infrastructure.persistence.Membership;
import com.landata.identity.infrastructure.persistence.MembershipRepository;
import com.landata.identity.infrastructure.persistence.UserAccount;
import com.landata.identity.infrastructure.persistence.UserAccountRepository;
import com.landata.query.infrastructure.sql.UserAttributes;
import com.landata.shared.api.ApiException;
import com.landata.shared.security.AuthPrincipal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformUserService {
  private final PlatformAdminService platformAdmins;
  private final UserAccountRepository users;
  private final MembershipRepository memberships;
  private final CompanyRepository companies;
  private final UsernameService usernames;

  public PlatformUserService(
      PlatformAdminService platformAdmins,
      UserAccountRepository users,
      MembershipRepository memberships,
      CompanyRepository companies,
      UsernameService usernames
  ) {
    this.platformAdmins = platformAdmins;
    this.users = users;
    this.memberships = memberships;
    this.companies = companies;
    this.usernames = usernames;
  }

  public List<Map<String, Object>> list(AuthPrincipal principal) {
    platformAdmins.require(principal);
    return users.findAll().stream().map(this::userView).toList();
  }

  @Transactional
  public Map<String, Object> update(AuthPrincipal principal, UUID userId, Map<String, Object> body) {
    platformAdmins.require(principal);
    UserAccount user = users.findById(userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
    if (body != null && body.get("username") != null) {
      usernames.assign(user, String.valueOf(body.get("username")));
    }
    if (body != null && body.get("display_name") instanceof String name && !name.isBlank()) {
      user.setDisplayName(name.trim());
    }
    if (body != null && body.get("memberships") instanceof List<?> rows) {
      for (Object row : rows) {
        if (row instanceof Map<?, ?> map) updateMembership(userId, map);
      }
    } else if (body != null && body.get("company_id") != null) {
      updateMembership(userId, body);
    }
    user.setUpdatedAt(Instant.now());
    return userView(user);
  }

  private void updateMembership(UUID userId, Map<?, ?> body) {
    Object companyRaw = body.get("company_id");
    if (companyRaw == null) return;
    UUID companyId = UUID.fromString(String.valueOf(companyRaw));
    Membership membership = memberships.findByCompanyIdAndUserId(companyId, userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Membership not found"));
    if (body.containsKey("branch_ids")) {
      membership.setBranchIds(UserAttributes.integers(body.get("branch_ids")));
    }
    if (body.get("role") instanceof String role && !role.isBlank() && !"owner".equals(membership.getRole())) {
      if (!List.of("admin", "editor", "viewer").contains(role)) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_ROLE", "Role must be admin, editor, or viewer");
      }
      membership.setRole(role);
    }
  }

  private Map<String, Object> userView(UserAccount user) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("user_id", user.getId());
    row.put("email", user.getEmail());
    row.put("username", user.getUsername());
    row.put("display_name", user.getDisplayName());
    row.put("status", user.getStatus());
    row.put("platform_admin", platformAdmins.isAdmin(user.getEmail(), user.getId()));
    List<Map<String, Object>> companiesForUser = new ArrayList<>();
    for (Membership membership : memberships.findByUserIdAndStatus(user.getId(), "active")) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("company_id", membership.getCompanyId());
      item.put("company_name", companies.findById(membership.getCompanyId()).map(Company::getName).orElse(""));
      item.put("role", membership.getRole());
      item.put("branch_ids", membership.getBranchIds());
      companiesForUser.add(item);
    }
    row.put("memberships", companiesForUser);
    return row;
  }
}

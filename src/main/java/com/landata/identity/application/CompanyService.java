package com.landata.identity.application;

import com.landata.billing.infrastructure.persistence.CompanyQuota;
import com.landata.billing.infrastructure.persistence.CompanyQuotaRepository;
import com.landata.identity.infrastructure.persistence.Company;
import com.landata.identity.infrastructure.persistence.CompanyRepository;
import com.landata.identity.infrastructure.persistence.Invitation;
import com.landata.identity.infrastructure.persistence.InvitationRepository;
import com.landata.identity.infrastructure.persistence.Membership;
import com.landata.identity.infrastructure.persistence.MembershipRepository;
import com.landata.identity.infrastructure.persistence.UserAccount;
import com.landata.identity.infrastructure.persistence.UserAccountRepository;
import com.landata.query.infrastructure.sql.UserAttributes;
import com.landata.shared.api.ApiException;
import com.landata.shared.config.LandataProperties;
import com.landata.shared.security.AuthPrincipal;
import com.landata.shared.security.Hashes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyService {
  private final CompanyRepository companies;
  private final MembershipRepository memberships;
  private final UserAccountRepository users;
  private final InvitationRepository invitations;
  private final CompanyQuotaRepository quotas;
  private final AuthService authService;
  private final UsernameService usernames;
  private final LandataProperties properties;

  public CompanyService(
      CompanyRepository companies,
      MembershipRepository memberships,
      UserAccountRepository users,
      InvitationRepository invitations,
      CompanyQuotaRepository quotas,
      AuthService authService,
      UsernameService usernames,
      LandataProperties properties
  ) {
    this.companies = companies;
    this.memberships = memberships;
    this.users = users;
    this.invitations = invitations;
    this.quotas = quotas;
    this.authService = authService;
    this.usernames = usernames;
    this.properties = properties;
  }

  public List<Map<String, Object>> mine(UUID userId) {
    return memberships.findByUserIdAndStatus(userId, "active").stream().map(membership -> {
      Company company = companies.findByIdAndDeletedAtIsNull(membership.getCompanyId()).orElse(null);
      if (company == null) return null;
      Map<String, Object> row = companyView(company);
      row.put("role", membership.getRole());
      return row;
    }).filter(java.util.Objects::nonNull).toList();
  }

  @Transactional
  public Created create(AuthPrincipal principal, String name) {
    if (name == null || name.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Company name is required");
    }
    UserAccount user = users.findById(principal.userId())
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Sign in required"));
    Company company = new Company();
    company.setName(name.trim());
    company.setSlug(uniqueSlug(name));
    company.setOwnerId(user.getId());
    companies.save(company);
    Membership membership = new Membership();
    membership.setCompanyId(company.getId());
    membership.setUserId(user.getId());
    membership.setRole("owner");
    memberships.save(membership);
    CompanyQuota quota = new CompanyQuota();
    quota.setCompanyId(company.getId());
    quotas.save(quota);
    return new Created(companyView(company), authService.forMembership(user, membership));
  }

  @Transactional
  public AuthService.AuthResult switchCompany(AuthPrincipal principal, UUID companyId) {
    Membership membership = activeMembership(principal.userId(), companyId);
    UserAccount user = users.findById(principal.userId()).orElseThrow();
    return authService.forMembership(user, membership);
  }

  public Map<String, Object> current(UUID companyId) {
    return companyView(load(companyId));
  }

  @Transactional
  public Map<String, Object> update(UUID companyId, String name) {
    Company company = load(companyId);
    if (name != null && !name.isBlank()) company.setName(name.trim());
    company.setUpdatedAt(Instant.now());
    return companyView(company);
  }

  @Transactional
  public void delete(UUID companyId) {
    Company company = load(companyId);
    company.setDeletedAt(Instant.now());
    company.setStatus("deleted");
  }

  public List<Map<String, Object>> members(UUID companyId) {
    return memberships.findByCompanyIdAndStatus(companyId, "active").stream().map(this::memberView).toList();
  }

  @Transactional
  public Map<String, Object> updateMember(UUID companyId, UUID userId, Map<String, Object> body) {
    Membership membership = activeMembership(userId, companyId);
    if (body != null && body.containsKey("role") && body.get("role") != null) {
      changeRole(membership, String.valueOf(body.get("role")));
    }
    if (body != null && body.containsKey("username") && body.get("username") != null) {
      UserAccount user = users.findById(userId).orElseThrow();
      usernames.assign(user, String.valueOf(body.get("username")));
    }
    if (body != null && body.containsKey("branch_ids")) {
      membership.setBranchIds(UserAttributes.integers(body.get("branch_ids")));
    }
    return memberView(membership);
  }

  @Transactional
  public void changeRole(UUID companyId, UUID userId, String role) {
    changeRole(activeMembership(userId, companyId), role);
  }

  private void changeRole(Membership membership, String role) {
    if ("owner".equals(membership.getRole())) {
      throw new ApiException(HttpStatus.CONFLICT, "OWNER_ROLE", "Transfer ownership before changing the owner");
    }
    if (!List.of("admin", "editor", "viewer").contains(role)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_ROLE", "Role must be admin, editor, or viewer");
    }
    membership.setRole(role);
  }

  private Map<String, Object> memberView(Membership membership) {
    UserAccount user = users.findById(membership.getUserId()).orElseThrow();
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("user_id", user.getId());
    row.put("email", user.getEmail());
    row.put("username", user.getUsername());
    row.put("display_name", user.getDisplayName());
    row.put("role", membership.getRole());
    row.put("branch_ids", membership.getBranchIds());
    return row;
  }

  @Transactional
  public void removeMember(UUID companyId, UUID userId) {
    Membership membership = activeMembership(userId, companyId);
    if ("owner".equals(membership.getRole())) {
      throw new ApiException(HttpStatus.CONFLICT, "OWNER_ROLE", "The owner cannot be removed");
    }
    memberships.delete(membership);
  }

  @Transactional
  public Map<String, Object> invite(AuthPrincipal principal, String email, String role) {
    if (!List.of("admin", "editor", "viewer").contains(role)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_ROLE", "Role must be admin, editor, or viewer");
    }
    String raw = Hashes.randomToken();
    Invitation invitation = new Invitation();
    invitation.setCompanyId(principal.companyId());
    invitation.setEmail(email.trim());
    invitation.setRole(role);
    invitation.setTokenHash(Hashes.sha256(raw));
    invitation.setInvitedBy(principal.userId());
    invitation.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
    invitations.save(invitation);
    Map<String, Object> view = inviteView(invitation);
    if (properties.getAuth().isExposeDevTokens()) view.put("token", raw);
    return view;
  }

  public List<Map<String, Object>> invitations(UUID companyId) {
    return invitations.findByCompanyIdAndStatus(companyId, "pending").stream().map(this::inviteView).toList();
  }

  @Transactional
  public void revokeInvite(UUID companyId, UUID invitationId) {
    Invitation invitation = invitations.findByIdAndCompanyId(invitationId, companyId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Invitation not found"));
    invitation.setStatus("revoked");
  }

  @Transactional
  public void acceptInvite(AuthPrincipal principal, String rawToken) {
    Invitation invitation = invitations.findByTokenHash(Hashes.sha256(rawToken))
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Invitation not found"));
    if (!"pending".equals(invitation.getStatus()) || invitation.getExpiresAt().isBefore(Instant.now())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_TOKEN", "Invitation is no longer valid");
    }
    UserAccount user = users.findById(principal.userId()).orElseThrow();
    if (!user.getEmail().equalsIgnoreCase(invitation.getEmail())) {
      throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_MISMATCH", "This invitation was sent to a different email");
    }
    memberships.findByCompanyIdAndUserId(invitation.getCompanyId(), user.getId()).orElseGet(() -> {
      Membership membership = new Membership();
      membership.setCompanyId(invitation.getCompanyId());
      membership.setUserId(user.getId());
      membership.setRole(invitation.getRole());
      return memberships.save(membership);
    });
    invitation.setStatus("accepted");
    invitation.setAcceptedAt(Instant.now());
  }

  private Membership activeMembership(UUID userId, UUID companyId) {
    return memberships.findByCompanyIdAndUserId(companyId, userId)
        .filter(membership -> "active".equals(membership.getStatus()))
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Company not found"));
  }

  private Company load(UUID companyId) {
    return companies.findByIdAndDeletedAtIsNull(companyId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Company not found"));
  }

  private String uniqueSlug(String name) {
    String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    if (base.isBlank()) base = "company";
    String slug = base;
    int suffix = 2;
    while (companies.findBySlugAndDeletedAtIsNull(slug).isPresent()) {
      slug = base + "-" + suffix++;
    }
    return slug;
  }

  private Map<String, Object> companyView(Company company) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", company.getId());
    row.put("name", company.getName());
    row.put("slug", company.getSlug());
    row.put("plan", company.getPlan());
    row.put("status", company.getStatus());
    return row;
  }

  private Map<String, Object> inviteView(Invitation invitation) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", invitation.getId());
    row.put("email", invitation.getEmail());
    row.put("role", invitation.getRole());
    row.put("status", invitation.getStatus());
    row.put("expires_at", invitation.getExpiresAt());
    return row;
  }

  public record Created(Map<String, Object> company, AuthService.AuthResult tokens) {}
}

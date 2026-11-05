package com.landata.identity.api;

import com.landata.identity.application.AuthService;
import com.landata.identity.application.CompanyService;
import com.landata.shared.api.PageResponse;
import com.landata.shared.security.AccessGuard;
import com.landata.shared.security.AuthPrincipal;
import com.landata.shared.security.RequiresRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Companies")
@RequestMapping("/api/v1")
public class CompanyController {
  private final CompanyService companies;
  private final AccessGuard accessGuard;

  public CompanyController(CompanyService companies, AccessGuard accessGuard) {
    this.companies = companies;
    this.accessGuard = accessGuard;
  }

  @GetMapping("/companies")
  public PageResponse<Map<String, Object>> list() {
    var rows = companies.mine(accessGuard.requireUser().userId());
    return new PageResponse<>(rows, 0, rows.size());
  }

  @PostMapping("/companies")
  public Map<String, Object> create(@Valid @RequestBody NameRequest request) {
    CompanyService.Created created = companies.create(accessGuard.requireUser(), request.name());
    return Map.of("company", created.company(), "access_token", created.tokens().accessToken(),
        "refresh_token", created.tokens().refreshToken(), "role", created.tokens().role());
  }

  @PostMapping("/companies/{id}/switch")
  public Map<String, Object> switchCompany(@PathVariable UUID id) {
    AuthService.AuthResult tokens = companies.switchCompany(accessGuard.requireUser(), id);
    return Map.of("access_token", tokens.accessToken(), "refresh_token", tokens.refreshToken(), "role", tokens.role(),
        "company_id", tokens.companyId());
  }

  @GetMapping("/company")
  @RequiresRole("viewer")
  public Map<String, Object> current() {
    return companies.current(accessGuard.current().companyId());
  }

  @PatchMapping("/company")
  @RequiresRole("admin")
  public Map<String, Object> update(@RequestBody NameRequest request) {
    return companies.update(accessGuard.current().companyId(), request.name());
  }

  @DeleteMapping("/company")
  @RequiresRole("owner")
  public Map<String, Object> delete() {
    companies.delete(accessGuard.current().companyId());
    return Map.of("status", "deleted");
  }

  @GetMapping("/company/members")
  @RequiresRole("admin")
  public PageResponse<Map<String, Object>> members() {
    var rows = companies.members(accessGuard.current().companyId());
    return new PageResponse<>(rows, 0, rows.size());
  }

  @PatchMapping("/company/members/{userId}")
  @RequiresRole("admin")
  public Map<String, Object> updateMember(@PathVariable UUID userId, @RequestBody Map<String, Object> body) {
    return companies.updateMember(accessGuard.current().companyId(), userId, body);
  }

  @DeleteMapping("/company/members/{userId}")
  @RequiresRole("admin")
  public Map<String, Object> remove(@PathVariable UUID userId) {
    companies.removeMember(accessGuard.current().companyId(), userId);
    return Map.of("status", "ok");
  }

  @GetMapping("/company/invitations")
  @RequiresRole("admin")
  public PageResponse<Map<String, Object>> invitations() {
    var rows = companies.invitations(accessGuard.current().companyId());
    return new PageResponse<>(rows, 0, rows.size());
  }

  @PostMapping("/company/invitations")
  @RequiresRole("admin")
  public Map<String, Object> invite(@Valid @RequestBody InviteRequest request) {
    AuthPrincipal principal = accessGuard.current();
    return companies.invite(principal, request.email(), request.role());
  }

  @DeleteMapping("/company/invitations/{id}")
  @RequiresRole("admin")
  public Map<String, Object> revoke(@PathVariable UUID id) {
    companies.revokeInvite(accessGuard.current().companyId(), id);
    return Map.of("status", "ok");
  }

  @PostMapping("/invitations/{token}/accept")
  public Map<String, Object> accept(@PathVariable String token) {
    companies.acceptInvite(accessGuard.requireUser(), token);
    return Map.of("status", "accepted");
  }

  public record NameRequest(String name) {}
  public record RoleRequest(@NotBlank String role) {}
  public record InviteRequest(@Email @NotBlank String email, @NotBlank String role) {}
}

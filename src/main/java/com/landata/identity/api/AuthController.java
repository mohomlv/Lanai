package com.landata.identity.api;

import com.landata.identity.application.AuthService;
import com.landata.identity.application.PlatformAdminService;
import com.landata.identity.infrastructure.persistence.Company;
import com.landata.identity.infrastructure.persistence.CompanyRepository;
import com.landata.identity.infrastructure.persistence.Membership;
import com.landata.identity.infrastructure.persistence.MembershipRepository;
import com.landata.identity.infrastructure.persistence.UserAccount;
import com.landata.identity.infrastructure.persistence.UserAccountRepository;
import com.landata.shared.api.ApiException;
import com.landata.shared.config.LandataProperties;
import com.landata.shared.security.AccessGuard;
import com.landata.shared.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Auth")
@RequestMapping("/api/v1")
public class AuthController {
  private final AuthService authService;
  private final AccessGuard accessGuard;
  private final UserAccountRepository users;
  private final MembershipRepository memberships;
  private final CompanyRepository companies;
  private final PlatformAdminService platformAdmins;
  private final LandataProperties properties;

  public AuthController(
      AuthService authService,
      AccessGuard accessGuard,
      UserAccountRepository users,
      MembershipRepository memberships,
      CompanyRepository companies,
      PlatformAdminService platformAdmins,
      LandataProperties properties
  ) {
    this.authService = authService;
    this.accessGuard = accessGuard;
    this.users = users;
    this.memberships = memberships;
    this.companies = companies;
    this.platformAdmins = platformAdmins;
    this.properties = properties;
  }

  @PostMapping("/auth/signup")
  public Map<String, Object> signup(@Valid @RequestBody SignupRequest request) {
    return auth(authService.signup(request.email(), request.password(), request.displayName()));
  }

  @PostMapping("/auth/login")
  public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
    return auth(authService.login(request.email(), request.password()));
  }

  @PostMapping("/auth/refresh")
  public Map<String, Object> refresh(@RequestBody RefreshRequest request) {
    return auth(authService.refresh(request.refreshToken()));
  }

  @PostMapping("/auth/logout")
  public Map<String, Object> logout(@RequestBody(required = false) RefreshRequest request) {
    authService.logout(request == null ? null : request.refreshToken());
    return Map.of("status", "ok");
  }

  @PostMapping("/auth/verify-email")
  public Map<String, Object> verify(@RequestBody TokenRequest request) {
    authService.verifyEmail(request.token());
    return Map.of("status", "ok");
  }

  @PostMapping("/auth/forgot-password")
  public Map<String, Object> forgot(@RequestBody EmailRequest request) {
    String token = authService.forgotPassword(request.email());
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", "ok");
    if (properties.getAuth().isExposeDevTokens() && token != null) body.put("dev_token", token);
    return body;
  }

  @PostMapping("/auth/reset-password")
  public Map<String, Object> reset(@Valid @RequestBody ResetRequest request) {
    authService.resetPassword(request.token(), request.password());
    return Map.of("status", "ok");
  }

  @GetMapping("/me")
  public Map<String, Object> me() {
    AuthPrincipal principal = accessGuard.requireUser();
    UserAccount user = users.findById(principal.userId())
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Sign in required"));
    return profile(principal, user);
  }

  @PatchMapping("/me")
  public Map<String, Object> updateMe(@RequestBody ProfileRequest request) {
    AuthPrincipal principal = accessGuard.requireUser();
    UserAccount user = authService.updateProfile(principal, request.username());
    return profile(principal, user);
  }

  private Map<String, Object> profile(AuthPrincipal principal, UserAccount user) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("id", user.getId());
    body.put("email", user.getEmail());
    body.put("username", user.getUsername());
    body.put("display_name", user.getDisplayName());
    body.put("email_verified", user.getEmailVerified());
    body.put("company_id", principal.companyId());
    body.put("role", principal.role());
    body.put("platform_admin", platformAdmins.isAdmin(user.getEmail(), user.getId()));
    Membership current = principal.companyId() == null ? null
        : memberships.findByCompanyIdAndUserId(principal.companyId(), user.getId()).orElse(null);
    body.put("branch_ids", current == null ? List.of() : current.getBranchIds());
    body.put("memberships", memberships.findByUserIdAndStatus(user.getId(), "active").stream().map(this::membership).toList());
    return body;
  }

  private Map<String, Object> membership(Membership membership) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("company_id", membership.getCompanyId());
    row.put("company_name", companies.findById(membership.getCompanyId()).map(Company::getName).orElse(""));
    row.put("role", membership.getRole());
    row.put("branch_ids", membership.getBranchIds());
    return row;
  }

  private Map<String, Object> auth(AuthService.AuthResult result) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("access_token", result.accessToken());
    body.put("refresh_token", result.refreshToken());
    body.put("user_id", result.userId());
    body.put("company_id", result.companyId());
    body.put("role", result.role());
    if (result.devToken() != null) body.put("dev_token", result.devToken());
    return body;
  }

  public record SignupRequest(@Email @NotBlank String email, @NotBlank String password, @NotBlank String displayName) {}
  public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
  public record RefreshRequest(String refreshToken) {}
  public record TokenRequest(@NotBlank String token) {}
  public record EmailRequest(@Email @NotBlank String email) {}
  public record ResetRequest(@NotBlank String token, @NotBlank String password) {}
  public record ProfileRequest(String username) {}
}

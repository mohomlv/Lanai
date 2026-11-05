package com.landata.identity.application;

import com.landata.identity.infrastructure.persistence.AuthToken;
import com.landata.identity.infrastructure.persistence.AuthTokenRepository;
import com.landata.identity.infrastructure.persistence.Membership;
import com.landata.identity.infrastructure.persistence.MembershipRepository;
import com.landata.identity.infrastructure.persistence.RefreshToken;
import com.landata.identity.infrastructure.persistence.RefreshTokenRepository;
import com.landata.identity.infrastructure.persistence.UserAccount;
import com.landata.identity.infrastructure.persistence.UserAccountRepository;
import com.landata.shared.api.ApiException;
import com.landata.shared.config.LandataProperties;
import com.landata.shared.security.AuthPrincipal;
import com.landata.shared.security.Hashes;
import com.landata.shared.security.JwtService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final UserAccountRepository users;
  private final MembershipRepository memberships;
  private final AuthTokenRepository authTokens;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final LandataProperties properties;
  private final UsernameService usernames;
  private final PlatformAdminService platformAdmins;

  public AuthService(
      UserAccountRepository users,
      MembershipRepository memberships,
      AuthTokenRepository authTokens,
      RefreshTokenRepository refreshTokens,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      LandataProperties properties,
      UsernameService usernames,
      PlatformAdminService platformAdmins
  ) {
    this.users = users;
    this.memberships = memberships;
    this.authTokens = authTokens;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.properties = properties;
    this.usernames = usernames;
    this.platformAdmins = platformAdmins;
  }

  @Transactional
  public AuthResult signup(String email, String password, String displayName) {
    if (users.findByEmailIgnoreCase(email).isPresent()) {
      throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "An account with that email already exists");
    }
    UserAccount user = new UserAccount();
    user.setEmail(email.trim());
    user.setDisplayName(displayName.trim());
    user.setPasswordHash(passwordEncoder.encode(password));
    usernames.ensure(user);
    users.save(user);
    String devToken = issueAuthToken(user.getId(), "verify_email", 24);
    return tokens(user, null, null, devToken);
  }

  @Transactional
  public AuthResult login(String email, String password) {
    UserAccount user = users.findByEmailIgnoreCase(email)
        .filter(found -> "active".equals(found.getStatus()))
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Email or password is wrong"));
    if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Email or password is wrong");
    }
    user.setLastLoginAt(Instant.now());
    usernames.ensure(user);
    Membership membership = activeMembership(user.getId(), null);
    elevate(user, membership);
    if (membership == null) {
      return tokens(user, null, null, null);
    }
    return tokens(user, membership.getCompanyId(), membership.getRole(), null);
  }

  @Transactional
  public AuthResult refresh(String rawRefresh) {
    RefreshToken current = refreshTokens.findByTokenHash(Hashes.sha256(rawRefresh))
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "BAD_REFRESH", "Refresh token is not valid"));
    if (current.getRevokedAt() != null) {
      refreshTokens.findByFamilyId(current.getFamilyId()).forEach(token -> {
        if (token.getRevokedAt() == null) token.setRevokedAt(Instant.now());
      });
      throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_REUSE", "Refresh token was already used");
    }
    if (current.getExpiresAt().isBefore(Instant.now())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_REFRESH", "Refresh token expired");
    }
    current.setRevokedAt(Instant.now());
    UserAccount user = users.findById(current.getUserId())
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "BAD_REFRESH", "Account no longer exists"));
    Membership membership = activeMembership(user.getId(), current.getCompanyId());
    elevate(user, membership);
    UUID companyId = membership == null ? null : membership.getCompanyId();
    String role = membership == null ? null : membership.getRole();
    return issueRefresh(user, companyId, role, current.getFamilyId());
  }

  @Transactional
  public void logout(String rawRefresh) {
    if (rawRefresh == null || rawRefresh.isBlank()) return;
    refreshTokens.findByTokenHash(Hashes.sha256(rawRefresh)).ifPresent(token -> token.setRevokedAt(Instant.now()));
  }

  @Transactional
  public void verifyEmail(String rawToken) {
    AuthToken token = consume(rawToken, "verify_email");
    UserAccount user = users.findById(token.getUserId()).orElseThrow();
    user.setEmailVerified(true);
  }

  @Transactional
  public String forgotPassword(String email) {
    return users.findByEmailIgnoreCase(email)
        .map(user -> issueAuthToken(user.getId(), "reset_password", 1))
        .orElse(null);
  }

  @Transactional
  public void resetPassword(String rawToken, String password) {
    AuthToken token = consume(rawToken, "reset_password");
    UserAccount user = users.findById(token.getUserId()).orElseThrow();
    user.setPasswordHash(passwordEncoder.encode(password));
  }

  @Transactional
  public UserAccount updateProfile(AuthPrincipal principal, String username) {
    UserAccount user = users.findById(principal.userId())
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Sign in required"));
    if (username != null) usernames.assign(user, username);
    usernames.ensure(user);
    user.setUpdatedAt(Instant.now());
    return user;
  }

  public AuthResult forMembership(UserAccount user, Membership membership) {
    elevate(user, membership);
    return tokens(user, membership.getCompanyId(), membership.getRole(), null);
  }

  private void elevate(UserAccount user, Membership current) {
    memberships.findByUserIdAndStatus(user.getId(), "active").forEach(item -> platformAdmins.elevate(user, item));
    platformAdmins.elevate(user, current);
  }

  public AuthResult tokens(UserAccount user, UUID companyId, String role, String devToken) {
    return issueRefresh(user, companyId, role, UUID.randomUUID()).withDevToken(expose(devToken));
  }

  private AuthResult issueRefresh(UserAccount user, UUID companyId, String role, UUID familyId) {
    String raw = Hashes.randomToken();
    RefreshToken token = new RefreshToken();
    token.setUserId(user.getId());
    token.setTokenHash(Hashes.sha256(raw));
    token.setFamilyId(familyId);
    token.setCompanyId(companyId);
    token.setExpiresAt(Instant.now().plus(properties.getJwt().getRefreshDays(), ChronoUnit.DAYS));
    refreshTokens.save(token);
    List<Integer> branches = List.of();
    if (companyId != null) {
      branches = memberships.findByCompanyIdAndUserId(companyId, user.getId())
          .map(Membership::getBranchIds)
          .orElse(List.of());
    }
    AuthPrincipal principal = new AuthPrincipal(
        user.getId(), companyId, role, user.getEmail(), user.getUsername(), branches);
    return new AuthResult(jwtService.accessToken(principal), raw, user.getId(), companyId, role, null);
  }

  private Membership activeMembership(UUID userId, UUID preferredCompanyId) {
    if (preferredCompanyId != null) {
      Membership current = memberships.findByCompanyIdAndUserId(preferredCompanyId, userId)
          .filter(membership -> "active".equals(membership.getStatus()))
          .orElse(null);
      if (current != null) return current;
    }
    return memberships.findByUserIdAndStatus(userId, "active").stream()
        .max(Comparator.comparing(Membership::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
        .orElse(null);
  }

  private String issueAuthToken(UUID userId, String purpose, int hours) {
    String raw = Hashes.randomToken();
    AuthToken token = new AuthToken();
    token.setUserId(userId);
    token.setPurpose(purpose);
    token.setTokenHash(Hashes.sha256(raw));
    token.setExpiresAt(Instant.now().plus(hours, ChronoUnit.HOURS));
    authTokens.save(token);
    return raw;
  }

  private AuthToken consume(String rawToken, String purpose) {
    AuthToken token = authTokens.findByTokenHashAndPurpose(Hashes.sha256(rawToken), purpose)
        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "BAD_TOKEN", "Token is not valid"));
    if (token.getUsedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_TOKEN", "Token is not valid");
    }
    token.setUsedAt(Instant.now());
    return token;
  }

  private String expose(String devToken) {
    return properties.getAuth().isExposeDevTokens() ? devToken : null;
  }

  public record AuthResult(String accessToken, String refreshToken, UUID userId, UUID companyId, String role, String devToken) {
    AuthResult withDevToken(String token) {
      return new AuthResult(accessToken, refreshToken, userId, companyId, role, token);
    }
  }
}

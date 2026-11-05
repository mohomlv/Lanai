package com.landata.shared.security;

import com.landata.shared.api.ApiException;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AccessGuard {
  private static final Map<String, Integer> RANK = Map.of(
      "viewer", 1, "editor", 2, "admin", 3, "owner", 4
  );

  public AuthPrincipal current() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Sign in required");
    }
    return principal;
  }

  public AuthPrincipal requireUser() {
    return current();
  }

  public AuthPrincipal require(String minimum) {
    AuthPrincipal principal = current();
    if (principal.companyId() == null || principal.role() == null) {
      throw new ApiException(HttpStatus.CONFLICT, "NO_COMPANY", "Choose a company first");
    }
    int have = RANK.getOrDefault(principal.role().toLowerCase(Locale.ROOT), 0);
    int need = RANK.getOrDefault(minimum.toLowerCase(Locale.ROOT), 99);
    if (have < need) {
      throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this action");
    }
    return principal;
  }
}

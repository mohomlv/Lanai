package com.landata.identity.application;

import com.landata.query.infrastructure.sql.UserAttributes;
import com.landata.shared.security.AuthPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class UserAttributeService {
  public Map<String, Object> forPrincipal(AuthPrincipal principal) {
    if (principal == null) return UserAttributes.values(null, List.of());
    return UserAttributes.values(principal.username(), principal.branchIds());
  }

  public List<Map<String, String>> catalog() {
    return List.of(
        Map.of("text", "@username", "kind", "User", "detail", "Signed-in username"),
        Map.of("text", "@branches", "kind", "User", "detail", "Integer branch ids for this user")
    );
  }
}

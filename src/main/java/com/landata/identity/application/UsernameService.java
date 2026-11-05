package com.landata.identity.application;

import com.landata.identity.infrastructure.persistence.UserAccount;
import com.landata.identity.infrastructure.persistence.UserAccountRepository;
import com.landata.shared.api.ApiException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class UsernameService {
  private static final Pattern VALID = Pattern.compile("^[a-z0-9][a-z0-9._-]{1,39}$");
  private final UserAccountRepository users;

  public UsernameService(UserAccountRepository users) {
    this.users = users;
  }

  public String ensure(UserAccount user) {
    if (user.getUsername() != null && !user.getUsername().isBlank()) return user.getUsername();
    String assigned = unique(fromEmail(user.getEmail()), user.getId());
    user.setUsername(assigned);
    return assigned;
  }

  public void assign(UserAccount user, String requested) {
    String username = normalize(requested);
    if (!VALID.matcher(username).matches()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_USERNAME", "Username must be 2-40 characters: letters, digits, . _ -");
    }
    users.findByUsernameIgnoreCase(username).ifPresent(other -> {
      if (!other.getId().equals(user.getId())) {
        throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "That username is already in use");
      }
    });
    user.setUsername(username);
  }

  private String unique(String base, UUID id) {
    String seed = base.isBlank() || base.length() < 2 ? "user" : base;
    String suffix = id == null ? "new" : id.toString().replace("-", "").substring(0, 6);
    String candidate = seed;
    int attempt = 0;
    while (users.findByUsernameIgnoreCase(candidate).isPresent()) {
      candidate = seed + "_" + suffix + (attempt == 0 ? "" : attempt);
      attempt++;
      if (attempt > 20) {
        candidate = "user_" + suffix + System.nanoTime() % 1000;
        break;
      }
    }
    return candidate;
  }

  private static String fromEmail(String email) {
    if (email == null || email.isBlank()) return "user";
    String local = email.trim();
    int at = local.indexOf('@');
    if (at > 0) local = local.substring(0, at);
    return normalize(local);
  }

  private static String normalize(String raw) {
    if (raw == null) return "";
    String value = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
    value = value.replaceAll("^_+|_+$", "");
    if (value.length() > 40) value = value.substring(0, 40);
    return value;
  }
}

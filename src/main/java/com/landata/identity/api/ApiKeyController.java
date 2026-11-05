package com.landata.identity.api;

import com.landata.identity.infrastructure.persistence.ApiKey;
import com.landata.identity.infrastructure.persistence.ApiKeyRepository;
import com.landata.shared.api.ApiException;
import com.landata.shared.api.PageResponse;
import com.landata.shared.security.AccessGuard;
import com.landata.shared.security.Hashes;
import com.landata.shared.security.RequiresRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "API keys")
@RequestMapping("/api/v1/api-keys")
public class ApiKeyController {
  private final ApiKeyRepository apiKeys;
  private final AccessGuard access;

  public ApiKeyController(ApiKeyRepository apiKeys, AccessGuard access) {
    this.apiKeys = apiKeys;
    this.access = access;
  }

  @GetMapping
  @RequiresRole("admin")
  public PageResponse<Map<String, Object>> list() {
    var rows = apiKeys.findByCompanyIdOrderByCreatedAtDesc(access.current().companyId()).stream()
        .map(this::view)
        .toList();
    return new PageResponse<>(rows, 0, rows.size());
  }

  @PostMapping
  @RequiresRole("admin")
  public Map<String, Object> create(@RequestBody Map<String, Object> body) {
    String raw = "ld_" + Hashes.randomToken();
    ApiKey key = new ApiKey();
    key.setCompanyId(access.current().companyId());
    key.setCreatedBy(access.current().userId());
    key.setName(String.valueOf(body.getOrDefault("name", "key")));
    key.setKeyPrefix(raw.substring(0, 8));
    key.setKeyHash(Hashes.sha256(raw));
    apiKeys.save(key);
    return Map.of("id", key.getId(), "key", raw, "prefix", key.getKeyPrefix());
  }

  @DeleteMapping("/{id}")
  @RequiresRole("admin")
  public Map<String, Object> revoke(@PathVariable UUID id) {
    ApiKey key = apiKeys.findByIdAndCompanyId(id, access.current().companyId())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "API key not found"));
    key.setRevokedAt(java.time.Instant.now());
    apiKeys.save(key);
    return Map.of("status", "revoked");
  }

  private Map<String, Object> view(ApiKey key) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", key.getId());
    row.put("name", key.getName());
    row.put("prefix", key.getKeyPrefix());
    row.put("scopes", key.getScopes());
    row.put("created_by", key.getCreatedBy());
    row.put("last_used_at", key.getLastUsedAt());
    row.put("expires_at", key.getExpiresAt());
    row.put("revoked_at", key.getRevokedAt());
    row.put("created_at", key.getCreatedAt());
    return row;
  }
}

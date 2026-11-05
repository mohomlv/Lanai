package com.landata.identity.api;

import com.landata.identity.application.PlatformUserService;
import com.landata.shared.api.PageResponse;
import com.landata.shared.security.AccessGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Platform")
@RequestMapping("/api/v1")
public class PlatformUserController {
  private final PlatformUserService users;
  private final AccessGuard accessGuard;

  public PlatformUserController(PlatformUserService users, AccessGuard accessGuard) {
    this.users = users;
    this.accessGuard = accessGuard;
  }

  @GetMapping("/platform/users")
  public PageResponse<Map<String, Object>> list() {
    var rows = users.list(accessGuard.requireUser());
    return new PageResponse<>(rows, 0, rows.size());
  }

  @PatchMapping("/platform/users/{userId}")
  public Map<String, Object> update(@PathVariable UUID userId, @RequestBody Map<String, Object> body) {
    return users.update(accessGuard.requireUser(), userId, body);
  }
}

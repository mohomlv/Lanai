package com.landata.dashboard.api;

import com.landata.dashboard.application.DashboardService;
import com.landata.shared.api.PageResponse;
import com.landata.shared.security.AccessGuard;
import com.landata.shared.security.RequiresRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Dashboards")
@RequestMapping("/api/v1")
public class DashboardController {
  private final DashboardService dashboards;
  private final AccessGuard access;

  public DashboardController(DashboardService dashboards, AccessGuard access) {
    this.dashboards = dashboards;
    this.access = access;
  }

  @GetMapping("/dashboards")
  @RequiresRole("viewer")
  public PageResponse<Map<String, Object>> list() {
    var rows = dashboards.list(access.current().companyId());
    return new PageResponse<>(rows, 0, rows.size());
  }

  @PostMapping("/dashboards")
  @RequiresRole("editor")
  public Map<String, Object> create(@RequestBody Map<String, Object> body) {
    return dashboards.create(access.current(), body);
  }

  @GetMapping("/dashboards/{id}")
  @RequiresRole("viewer")
  public Map<String, Object> get(@PathVariable UUID id) {
    return dashboards.get(access.current().companyId(), id);
  }

  @DeleteMapping("/dashboards/{id}")
  @RequiresRole("editor")
  public Map<String, Object> archive(@PathVariable UUID id) {
    dashboards.archive(access.current().companyId(), id);
    return Map.of("status", "archived");
  }

  @PutMapping("/dashboards/{id}")
  @RequiresRole("editor")
  public Map<String, Object> replace(@PathVariable UUID id) {
    return dashboards.get(access.current().companyId(), id);
  }

  @PostMapping("/dashboards/{id}/cards")
  @RequiresRole("editor")
  public Map<String, Object> addCard(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    return dashboards.addCard(access.current(), id, body);
  }

  @PatchMapping("/cards/{id}")
  @RequiresRole("editor")
  public Map<String, Object> updateCard(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    return dashboards.updateCard(access.current().companyId(), id, body);
  }

  @DeleteMapping("/cards/{id}")
  @RequiresRole("editor")
  public Map<String, Object> deleteCard(@PathVariable UUID id) {
    dashboards.deleteCard(access.current().companyId(), id);
    return Map.of("status", "deleted");
  }

  @PutMapping("/dashboards/{id}/parameters")
  @RequiresRole("editor")
  public List<Map<String, Object>> parameters(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> rows = (List<Map<String, Object>>) body.getOrDefault("parameters", List.of());
    return dashboards.replaceParameters(access.current().companyId(), id, rows);
  }

  @PostMapping("/dashboards/{id}/data")
  @RequiresRole("viewer")
  public Map<String, Object> data(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
    return dashboards.data(access.current(), id, body == null ? Map.of() : body);
  }
}

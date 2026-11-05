package com.landata.admin.api;

import com.landata.admin.infrastructure.persistence.AuditLog;
import com.landata.admin.infrastructure.persistence.AuditLogRepository;
import com.landata.billing.infrastructure.persistence.CompanyQuota;
import com.landata.billing.infrastructure.persistence.CompanyQuotaRepository;
import com.landata.admin.infrastructure.persistence.EmbedToken;
import com.landata.admin.infrastructure.persistence.EmbedTokenRepository;
import com.landata.query.infrastructure.persistence.QueryRunRepository;
import com.landata.shared.api.ApiException;
import com.landata.shared.api.PageResponse;
import com.landata.shared.config.LandataProperties;
import com.landata.shared.security.AccessGuard;
import com.landata.shared.security.AuthPrincipal;
import com.landata.shared.security.Hashes;
import com.landata.shared.security.RequiresRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Admin")
@RequestMapping("/api/v1")
public class AdminController {
  private final AuditLogRepository audit;
  private final CompanyQuotaRepository quotas;
  private final QueryRunRepository runs;
  private final EmbedTokenRepository embedTokens;
  private final AccessGuard access;
  private final LandataProperties properties;

  public AdminController(
      AuditLogRepository audit,
      CompanyQuotaRepository quotas,
      QueryRunRepository runs,
      EmbedTokenRepository embedTokens,
      AccessGuard access,
      LandataProperties properties
  ) {
    this.audit = audit;
    this.quotas = quotas;
    this.runs = runs;
    this.embedTokens = embedTokens;
    this.access = access;
    this.properties = properties;
  }

  @GetMapping("/audit-log")
  @RequiresRole("admin")
  public PageResponse<AuditLog> auditLog() {
    var page = audit.findByCompanyIdOrderByCreatedAtDesc(access.current().companyId(), PageRequest.of(0, 50));
    return new PageResponse<>(page.getContent(), page.getNumber(), page.getTotalElements());
  }

  @GetMapping("/quotas")
  @RequiresRole("admin")
  public CompanyQuota quota() {
    return quotas.findById(access.current().companyId()).orElseThrow();
  }

  @PutMapping("/quotas")
  @RequiresRole("owner")
  public CompanyQuota updateQuota(@RequestBody Map<String, Object> body) {
    AuthPrincipal principal = access.current();
    if (!properties.isPlatformAdminEmail(principal.email())) {
      throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only a platform admin can change quotas");
    }
    CompanyQuota quota = quotas.findById(principal.companyId()).orElseThrow();
    if (body.get("ai_requests_per_month") instanceof Number number) quota.setAiRequestsPerMonth(number.intValue());
    if (body.get("queries_per_minute") instanceof Number number) quota.setQueriesPerMinute(number.intValue());
    if (body.get("max_datasources") instanceof Number number) quota.setMaxDatasources(number.intValue());
    return quotas.save(quota);
  }

  @GetMapping("/query-runs")
  @RequiresRole("admin")
  public PageResponse<?> queryRuns() {
    var rows = runs.findByCompanyIdOrderByStartedAtDesc(access.current().companyId());
    return new PageResponse<>(rows, 0, rows.size());
  }

  @PostMapping("/embed-tokens")
  @RequiresRole("editor")
  public Map<String, Object> embed(@RequestBody Map<String, Object> body) {
    String raw = Hashes.randomToken();
    EmbedToken token = new EmbedToken();
    token.setCompanyId(access.current().companyId());
    token.setCreatedBy(access.current().userId());
    token.setResourceType(String.valueOf(body.get("resource_type")));
    token.setResourceId(UUID.fromString(String.valueOf(body.get("resource_id"))));
    token.setTokenHash(Hashes.sha256(raw));
    embedTokens.save(token);
    return Map.of("token", raw, "id", token.getId());
  }
}

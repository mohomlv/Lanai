package com.landata.datasource.application;

import com.landata.datasource.infrastructure.jdbc.DriverSupport;

import com.landata.datasource.infrastructure.jdbc.HostValidator;

import com.landata.datasource.infrastructure.jdbc.PoolManager;

import com.landata.billing.infrastructure.persistence.CompanyQuota;
import com.landata.billing.infrastructure.persistence.CompanyQuotaRepository;
import com.landata.datasource.infrastructure.persistence.Datasource;
import com.landata.datasource.infrastructure.persistence.DatasourcePermission;
import com.landata.datasource.infrastructure.persistence.DatasourcePermissionRepository;
import com.landata.datasource.infrastructure.persistence.DatasourceRepository;
import com.landata.datasource.infrastructure.persistence.SchemaSyncSchedule;
import com.landata.datasource.infrastructure.persistence.SchemaSyncScheduleRepository;
import com.landata.shared.api.ApiException;
import com.landata.shared.crypto.SecretCipher;
import com.landata.shared.log.LogScrubber;
import com.landata.shared.security.AuthPrincipal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatasourceService {
  private final DatasourceRepository datasources;
  private final DatasourcePermissionRepository permissions;
  private final SchemaSyncScheduleRepository schedules;
  private final CompanyQuotaRepository quotas;
  private final SecretCipher cipher;
  private final PoolManager pools;
  private final HostValidator hosts;

  public DatasourceService(
      DatasourceRepository datasources,
      DatasourcePermissionRepository permissions,
      SchemaSyncScheduleRepository schedules,
      CompanyQuotaRepository quotas,
      SecretCipher cipher,
      PoolManager pools,
      HostValidator hosts
  ) {
    this.datasources = datasources;
    this.permissions = permissions;
    this.schedules = schedules;
    this.quotas = quotas;
    this.cipher = cipher;
    this.pools = pools;
    this.hosts = hosts;
  }

  public List<Map<String, Object>> list(UUID companyId) {
    return datasources.findByCompanyIdAndDeletedAtIsNullOrderByNameAsc(companyId).stream().map(this::view).toList();
  }

  public Map<String, Object> get(UUID companyId, UUID id) {
    return view(load(companyId, id));
  }

  @Transactional
  public Map<String, Object> create(AuthPrincipal principal, Map<String, Object> body) {
    CompanyQuota quota = quotas.findById(principal.companyId()).orElse(null);
    int max = quota == null ? 5 : quota.getMaxDatasources();
    if (datasources.countByCompanyIdAndDeletedAtIsNull(principal.companyId()) >= max) {
      throw new ApiException(HttpStatus.CONFLICT, "QUOTA", "Datasource limit reached");
    }
    Datasource datasource = new Datasource();
    datasource.setCompanyId(principal.companyId());
    datasource.setCreatedBy(principal.userId());
    apply(datasource, body, true);
    datasources.save(datasource);
    return view(datasource);
  }

  @Transactional
  public Map<String, Object> update(UUID companyId, UUID id, Map<String, Object> body) {
    Datasource datasource = load(companyId, id);
    apply(datasource, body, false);
    datasource.setUpdatedAt(Instant.now());
    pools.evict(id);
    return view(datasource);
  }

  @Transactional
  public void delete(UUID companyId, UUID id) {
    Datasource datasource = load(companyId, id);
    datasource.setDeletedAt(Instant.now());
    pools.evict(id);
  }

  public Map<String, Object> test(Map<String, Object> body) {
    String type = text(body, "type");
    if (!"sqlite".equals(type)) hosts.check(text(body, "host"), number(body, "port"));
    try (Connection connection = pools.test(
        type, text(body, "host"), number(body, "port"), text(body, "database_name"),
        text(body, "username"), text(body, "password"), text(body.getOrDefault("ssl_mode", "require")))) {
      return testView(connection, type);
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception ex) {
      return Map.of("ok", false, "error", LogScrubber.scrub(ex.getMessage()));
    }
  }

  @Transactional
  public Map<String, Object> testSaved(UUID companyId, UUID id) {
    Datasource datasource = load(companyId, id);
    String password = cipher.decrypt(datasource.getSecretCiphertext(), associated(datasource.getCompanyId()));
    try (Connection connection = pools.test(
        datasource.getType(), datasource.getHost(), datasource.getPort(), datasource.getDatabaseName(),
        datasource.getUsername(), password, datasource.getSslMode())) {
      Map<String, Object> result = testView(connection, datasource.getType());
      datasource.setStatus("connected");
      datasource.setLastTestAt(Instant.now());
      datasource.setLastTestError(null);
      datasource.setServerVersion(String.valueOf(result.get("server_version")));
      return result;
    } catch (Exception ex) {
      datasource.setStatus("error");
      datasource.setLastTestAt(Instant.now());
      datasource.setLastTestError(LogScrubber.scrub(ex.getMessage()));
      return Map.of("ok", false, "error", datasource.getLastTestError());
    }
  }

  @Transactional
  public List<Map<String, Object>> replacePermissions(UUID companyId, UUID id, List<Map<String, Object>> rows) {
    Datasource datasource = load(companyId, id);
    permissions.findByDatasourceIdAndCompanyId(datasource.getId(), companyId).forEach(permissions::delete);
    List<Map<String, Object>> saved = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      DatasourcePermission permission = new DatasourcePermission();
      permission.setCompanyId(companyId);
      permission.setDatasourceId(datasource.getId());
      permission.setRole(text(row, "role"));
      permission.setCanQuery(bool(row.get("can_query"), true));
      permission.setCanNativeSql(bool(row.get("can_native_sql"), false));
      permission.setCanManage(bool(row.get("can_manage"), false));
      permissions.save(permission);
      saved.add(row);
    }
    return saved;
  }

  public Map<String, Object> schedule(UUID companyId, UUID id) {
    load(companyId, id);
    SchemaSyncSchedule schedule = schedules.findById(id).orElseGet(() -> {
      SchemaSyncSchedule created = new SchemaSyncSchedule();
      created.setDatasourceId(id);
      created.setCompanyId(companyId);
      return created;
    });
    return Map.of("cron", schedule.getCron(), "enabled", schedule.getEnabled());
  }

  @Transactional
  public Map<String, Object> saveSchedule(UUID companyId, UUID id, String cron, boolean enabled) {
    load(companyId, id);
    SchemaSyncSchedule schedule = schedules.findById(id).orElseGet(SchemaSyncSchedule::new);
    schedule.setDatasourceId(id);
    schedule.setCompanyId(companyId);
    if (cron != null && !cron.isBlank()) schedule.setCron(cron);
    schedule.setEnabled(enabled);
    schedules.save(schedule);
    return Map.of("cron", schedule.getCron(), "enabled", schedule.getEnabled());
  }

  public Datasource load(UUID companyId, UUID id) {
    return datasources.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Datasource not found"));
  }

  public String password(Datasource datasource) {
    return cipher.decrypt(datasource.getSecretCiphertext(), associated(datasource.getCompanyId()));
  }

  private void apply(Datasource datasource, Map<String, Object> body, boolean creating) {
    if (creating || body.containsKey("name")) datasource.setName(text(body, "name"));
    if (creating || body.containsKey("type")) datasource.setType(text(body, "type"));
    if (creating || body.containsKey("host")) datasource.setHost(text(body, "host"));
    if (creating || body.containsKey("port")) datasource.setPort(number(body, "port"));
    if (creating || body.containsKey("database_name")) datasource.setDatabaseName(text(body, "database_name"));
    if (creating || body.containsKey("username")) datasource.setUsername(text(body, "username"));
    if (body.containsKey("ssl_mode")) datasource.setSslMode(text(body, "ssl_mode"));
    if (body.containsKey("description")) datasource.setDescription(text(body, "description"));
    if (body.containsKey("ai_sharing_level")) datasource.setAiSharingLevel(text(body, "ai_sharing_level"));
    if (body.get("options") instanceof Map<?, ?> options) {
      @SuppressWarnings("unchecked")
      Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) options);
      if (copy.get("jdbc") instanceof Map<?, ?> jdbc) {
        @SuppressWarnings("unchecked")
        Map<String, Object> jdbcMap = (Map<String, Object>) jdbc;
        DriverSupport.filterProperties(datasource.getType(), jdbcMap);
      }
      datasource.setOptions(copy);
    }
    if (!"sqlite".equals(datasource.getType())) hosts.check(datasource.getHost(), datasource.getPort());
    if (creating || body.containsKey("password")) {
      String password = text(body, "password");
      datasource.setSecretCiphertext(cipher.encrypt(password, associated(datasource.getCompanyId())));
      datasource.setSecretKeyId(cipher.keyId());
    }
  }

  private Map<String, Object> testView(Connection connection, String type) throws Exception {
    boolean writable = DriverSupport.writable(connection, type);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("ok", true);
    body.put("server_version", connection.getMetaData().getDatabaseProductVersion());
    body.put("writable", writable);
    if (writable) body.put("warning", "This user can change data. Use a read-only account.");
    return body;
  }

  private Map<String, Object> view(Datasource datasource) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", datasource.getId());
    row.put("name", datasource.getName());
    row.put("type", datasource.getType());
    row.put("host", datasource.getHost());
    row.put("port", datasource.getPort());
    row.put("database_name", datasource.getDatabaseName());
    row.put("username", datasource.getUsername());
    row.put("has_password", datasource.getSecretCiphertext() != null);
    row.put("ssl_mode", datasource.getSslMode());
    row.put("description", datasource.getDescription());
    row.put("ai_sharing_level", datasource.getAiSharingLevel());
    row.put("status", datasource.getStatus());
    row.put("query_timeout_seconds", datasource.getQueryTimeoutSeconds());
    row.put("max_rows", datasource.getMaxRows());
    row.put("last_synced_at", datasource.getLastSyncedAt());
    row.put("schema_fingerprint", datasource.getSchemaFingerprint());
    return row;
  }

  private static byte[] associated(UUID companyId) {
    return companyId.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static String text(Map<String, Object> body, String key) {
    Object value = body.get(key);
    if (value == null || String.valueOf(value).isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", key + " is required");
    }
    return String.valueOf(value).trim();
  }

  private static String text(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static int number(Map<String, Object> body, String key) {
    Object value = body.get(key);
    if (value instanceof Number number) return number.intValue();
    return Integer.parseInt(String.valueOf(value));
  }

  private static boolean bool(Object value, boolean fallback) {
    return value instanceof Boolean flag ? flag : fallback;
  }
}

package com.landata.query.application;
import com.landata.datasource.application.DatasourceAuthorizer;
import com.landata.datasource.infrastructure.jdbc.PoolManager;
import com.landata.datasource.application.DatasourceService;
import com.landata.datasource.infrastructure.persistence.CatalogColumn;
import com.landata.datasource.infrastructure.persistence.CatalogColumnRepository;
import com.landata.datasource.infrastructure.persistence.CatalogTable;
import com.landata.datasource.infrastructure.persistence.CatalogTableRepository;
import com.landata.datasource.infrastructure.persistence.Datasource;
import com.landata.query.infrastructure.persistence.QueryRun;
import com.landata.query.infrastructure.persistence.QueryRunRepository;
import com.landata.identity.application.UserAttributeService;
import com.landata.query.infrastructure.sql.NativeSql;
import com.landata.query.infrastructure.sql.QueryValidator;
import com.landata.query.infrastructure.sql.SqlCompiler;
import com.landata.shared.api.ApiException;
import com.landata.shared.log.LogScrubber;
import com.landata.shared.security.AuthPrincipal;
import com.landata.shared.security.Hashes;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class QueryService {
  private final DatasourceService datasources;
  private final DatasourceAuthorizer authorizer;
  private final PoolManager pools;
  private final CatalogTableRepository tables;
  private final CatalogColumnRepository columns;
  private final QueryRunRepository runs;
  private final ParameterAccessService parameterAccess;
  private final UserAttributeService userAttributes;
  private final ConcurrentHashMap<UUID, Statement> active = new ConcurrentHashMap<>();

  public QueryService(
      DatasourceService datasources,
      DatasourceAuthorizer authorizer,
      PoolManager pools,
      CatalogTableRepository tables,
      CatalogColumnRepository columns,
      QueryRunRepository runs,
      ParameterAccessService parameterAccess,
      UserAttributeService userAttributes
  ) {
    this.datasources = datasources;
    this.authorizer = authorizer;
    this.pools = pools;
    this.tables = tables;
    this.columns = columns;
    this.runs = runs;
    this.parameterAccess = parameterAccess;
    this.userAttributes = userAttributes;
  }

  public Map<String, Object> compile(AuthPrincipal principal, Map<String, Object> body) {
    Datasource datasource = datasources.load(principal.companyId(), uuid(body, "datasource_id"));
    authorizer.check(datasource, principal.role(), false);
    parameterAccess.assertSources(principal, parameterList(body), null);
    Prepared prepared = prepare(principal, datasource, body, ZoneId.of("UTC"));
    return Map.of("sql", prepared.sql(), "parameter_count", prepared.binds().size());
  }

  public Map<String, Object> validate(AuthPrincipal principal, Map<String, Object> body) {
    Datasource datasource = datasources.load(principal.companyId(), uuid(body, "datasource_id"));
    parameterAccess.assertSources(principal, parameterList(body), null);
    Prepared prepared = prepare(principal, datasource, body, ZoneId.of("UTC"));
    QueryValidator.assertSafeSelect(prepared.sql());
    checkTables(datasource, prepared.sql());
    return Map.of("valid", true, "sql", prepared.sql());
  }

  public Map<String, Object> run(AuthPrincipal principal, Map<String, Object> body, String source) {
    Datasource datasource = datasources.load(principal.companyId(), uuid(body, "datasource_id"));
    boolean nativeSql = "native".equals(body.get("mode"));
    authorizer.check(datasource, principal.role(), nativeSql);
    parameterAccess.assertSources(principal, parameterList(body), null);
    Prepared prepared = prepare(principal, datasource, body, ZoneId.of("UTC"));
    QueryValidator.assertSafeSelect(prepared.sql());
    return execute(principal, datasource, prepared, source, null, null);
  }

  public Map<String, Object> executeSaved(AuthPrincipal principal, Datasource datasource, Prepared prepared, UUID questionId, String source) {
    authorizer.check(datasource, principal.role(), prepared.nativeSql());
    QueryValidator.assertSafeSelect(prepared.sql());
    return execute(principal, datasource, prepared, source, questionId, null);
  }

  public Map<String, Object> cancel(AuthPrincipal principal, UUID runId) {
    runs.findByIdAndCompanyId(runId, principal.companyId())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Query run not found"));
    Statement statement = active.get(runId);
    if (statement != null) {
      try {
        statement.cancel();
      } catch (Exception ignored) {
        // The driver reports cancellation on the executing thread.
      }
    }
    return Map.of("status", "cancelled");
  }

  public Prepared prepare(Datasource datasource, Map<String, Object> body, ZoneId zone) {
    return prepare(null, datasource, body, zone);
  }

  public Prepared prepare(AuthPrincipal principal, Datasource datasource, Map<String, Object> body, ZoneId zone) {
    String mode = String.valueOf(body.getOrDefault("mode", "visual"));
    if ("native".equals(mode)) {
      NativeSql.Rendered rendered = NativeSql.render(
          String.valueOf(body.get("native_sql")),
          parameterList(body),
          valueMap(body),
          zone,
          maxBinds(datasource.getType()),
          userAttributes.forPrincipal(principal));
      String sql = rendered.sql();
      if ("sqlserver".equals(datasource.getType())) {
        sql = sql.replaceAll("(?is)\\s+limit\\s+\\d+\\s*;?\\s*$", "");
      }
      return new Prepared(sql, rendered.binds(), true);
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> spec = (Map<String, Object>) body.get("query_spec");
    SqlCompiler.Compiled compiled = SqlCompiler.compile(
        spec, parameterList(body), valueMap(body), datasource.getType(), zone, datasource.getMaxRows());
    return new Prepared(compiled.sql(), compiled.binds(), false);
  }

  private Map<String, Object> execute(
      AuthPrincipal principal, Datasource datasource, Prepared prepared, String source, UUID questionId, UUID dashboardId
  ) {
    QueryRun run = new QueryRun();
    run.setCompanyId(principal.companyId());
    run.setUserId(principal.userId());
    run.setDatasourceId(datasource.getId());
    run.setQuestionId(questionId);
    run.setDashboardId(dashboardId);
    run.setSource("ai".equals(source) ? "ai_validation" : source);
    run.setSqlHash(Hashes.sha256(prepared.sql()));
    run.setParamsHash(Hashes.sha256(String.valueOf(prepared.binds().size())));
    run.setCompiledSql(prepared.sql());
    run.setStartedAt(Instant.now());
    long started = System.currentTimeMillis();
    try (Connection connection = pools.connection(datasource, datasources.password(datasource));
         PreparedStatement statement = connection.prepareStatement(prepared.sql())) {
      active.put(run.getId(), statement);
      statement.setQueryTimeout(datasource.getQueryTimeoutSeconds());
      int cap = Math.min(datasource.getMaxRows(), 10000);
      statement.setMaxRows(cap + 1);
      for (int i = 0; i < prepared.binds().size(); i++) statement.setObject(i + 1, prepared.binds().get(i));
      try (ResultSet rows = statement.executeQuery()) {
        ResultSetMetaData meta = rows.getMetaData();
        List<Map<String, Object>> columns = new ArrayList<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
          columns.add(Map.of("name", meta.getColumnLabel(i), "type", "other", "native_type", meta.getColumnTypeName(i)));
        }
        List<List<Object>> data = new ArrayList<>();
        while (rows.next() && data.size() <= cap) {
          List<Object> row = new ArrayList<>();
          for (int i = 1; i <= meta.getColumnCount(); i++) {
            Object value = rows.getObject(i);
            if (value instanceof String text && text.length() > 10000) value = text.substring(0, 10000);
            row.add(value);
          }
          data.add(row);
        }
        boolean truncated = data.size() > cap;
        if (truncated) data.remove(data.size() - 1);
        run.setStatus("success");
        run.setRowCount(data.size());
        run.setDurationMs((int) (System.currentTimeMillis() - started));
        runs.save(run);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("run_id", run.getId());
        body.put("columns", columns);
        body.put("rows", data);
        body.put("truncated", truncated);
        body.put("row_limit", cap);
        body.put("duration_ms", run.getDurationMs());
        body.put("sql", prepared.sql());
        body.put("cache", Map.of("hit", false, "age_s", 0));
        return body;
      }
    } catch (Exception ex) {
      run.setStatus(ex instanceof java.sql.SQLTimeoutException ? "timeout" : "error");
      run.setErrorCode(run.getStatus().toUpperCase());
      String detail = LogScrubber.scrub(ex.getMessage());
      if (detail == null || detail.isBlank()) detail = "Query failed";
      if (detail.length() > 500) detail = detail.substring(0, 500);
      run.setErrorMessage(detail);
      run.setDurationMs((int) (System.currentTimeMillis() - started));
      try {
        runs.save(run);
      } catch (RuntimeException ignored) {
        // The database error is more useful than a failed history insert.
      }
      throw new ApiException(HttpStatus.BAD_GATEWAY, run.getErrorCode(), detail);
    } finally {
      active.remove(run.getId());
    }
  }

  private void checkTables(Datasource datasource, String sql) {
    for (CatalogTable table : tables.findByDatasourceIdAndCompanyId(datasource.getId(), datasource.getCompanyId())) {
      if (!table.getVisible() && sql.toLowerCase().contains(table.getTableName().toLowerCase())) {
        throw new com.landata.query.infrastructure.sql.QueryProblem("HIDDEN_TABLE", table.getTableName());
      }
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> parameterList(Map<String, Object> body) {
    Object raw = body.get("parameter_definitions");
    if (raw instanceof List<?> list) return (List<Map<String, Object>>) list;
    if (body.get("query_spec") instanceof Map<?, ?> spec && spec.get("parameters") instanceof List<?> list) {
      return (List<Map<String, Object>>) list;
    }
    return List.of();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> valueMap(Map<String, Object> body) {
    Object raw = body.get("parameter_values");
    if (raw instanceof Map<?, ?> map) return (Map<String, Object>) map;
    raw = body.get("parameters");
    if (raw instanceof Map<?, ?> map) return (Map<String, Object>) map;
    return Map.of();
  }

  private UUID uuid(Map<String, Object> body, String key) {
    return UUID.fromString(String.valueOf(body.get(key)));
  }

  private int maxBinds(String type) {
    return "sqlserver".equals(type) ? 2000 : 10000;
  }

  public List<String> visibleColumns(Datasource datasource, String schema, String table) {
    return tables.findByDatasourceIdAndSchemaNameAndTableName(datasource.getId(), schema, table)
        .map(found -> columns.findByTableIdOrderByOrdinalAsc(found.getId()).stream().map(CatalogColumn::getName).toList())
        .orElse(List.of());
  }

  public record Prepared(String sql, List<Object> binds, boolean nativeSql) {}
}

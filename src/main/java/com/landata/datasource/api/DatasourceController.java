package com.landata.datasource.api;

import com.landata.datasource.infrastructure.jdbc.DriverSupport;

import com.landata.datasource.infrastructure.jdbc.PoolManager;

import com.landata.datasource.application.CatalogSync;

import com.landata.datasource.application.DatasourceService;

import com.landata.datasource.infrastructure.persistence.CatalogColumn;
import com.landata.datasource.infrastructure.persistence.CatalogColumnRepository;
import com.landata.datasource.infrastructure.persistence.CatalogFk;
import com.landata.datasource.infrastructure.persistence.CatalogFkRepository;
import com.landata.datasource.infrastructure.persistence.CatalogTable;
import com.landata.datasource.infrastructure.persistence.CatalogTableRepository;
import com.landata.datasource.infrastructure.persistence.Datasource;
import com.landata.identity.application.UserAttributeService;
import com.landata.shared.api.ApiException;
import com.landata.shared.api.PageResponse;
import com.landata.shared.security.RequiresRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Tag(name = "Datasources")
@RequestMapping("/api/v1")
public class DatasourceController {
  private final DatasourceService datasources;
  private final CatalogSync catalogSync;
  private final CatalogTableRepository tables;
  private final CatalogColumnRepository columns;
  private final CatalogFkRepository foreignKeys;
  private final PoolManager pools;
  private final com.landata.shared.security.AccessGuard access;
  private final UserAttributeService userAttributes;

  public DatasourceController(
      DatasourceService datasources,
      CatalogSync catalogSync,
      CatalogTableRepository tables,
      CatalogColumnRepository columns,
      CatalogFkRepository foreignKeys,
      PoolManager pools,
      com.landata.shared.security.AccessGuard access,
      UserAttributeService userAttributes
  ) {
    this.datasources = datasources;
    this.catalogSync = catalogSync;
    this.tables = tables;
    this.columns = columns;
    this.foreignKeys = foreignKeys;
    this.pools = pools;
    this.access = access;
    this.userAttributes = userAttributes;
  }

  @GetMapping("/datasources")
  @RequiresRole("viewer")
  public PageResponse<Map<String, Object>> list() {
    var rows = datasources.list(access.current().companyId());
    return new PageResponse<>(rows, 0, rows.size());
  }

  @PostMapping("/datasources")
  @RequiresRole("admin")
  public Map<String, Object> create(@RequestBody Map<String, Object> body) {
    return datasources.create(access.current(), body);
  }

  @GetMapping("/datasources/{id}")
  @RequiresRole("viewer")
  public Map<String, Object> get(@PathVariable UUID id) {
    return datasources.get(access.current().companyId(), id);
  }

  @PatchMapping("/datasources/{id}")
  @RequiresRole("admin")
  public Map<String, Object> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    return datasources.update(access.current().companyId(), id, body);
  }

  @DeleteMapping("/datasources/{id}")
  @RequiresRole("admin")
  public Map<String, Object> delete(@PathVariable UUID id) {
    datasources.delete(access.current().companyId(), id);
    return Map.of("status", "deleted");
  }

  @PostMapping("/datasources/test")
  @RequiresRole("admin")
  public Map<String, Object> test(@RequestBody Map<String, Object> body) {
    return datasources.test(body);
  }

  @PostMapping("/datasources/{id}/test")
  @RequiresRole("admin")
  public Map<String, Object> testSaved(@PathVariable UUID id) {
    return datasources.testSaved(access.current().companyId(), id);
  }

  @PutMapping("/datasources/{id}/permissions")
  @RequiresRole("admin")
  public List<Map<String, Object>> permissions(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> rows = (List<Map<String, Object>>) body.getOrDefault("permissions", List.of());
    return datasources.replacePermissions(access.current().companyId(), id, rows);
  }

  @PostMapping("/datasources/{id}/sync")
  @RequiresRole("admin")
  public ResponseEntity<Map<String, Object>> sync(@PathVariable UUID id) {
    catalogSync.enqueue(access.current().companyId(), id);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("status", "queued"));
  }

  @GetMapping("/datasources/{id}/autocomplete")
  @RequiresRole("editor")
  public Map<String, Object> autocompleteCatalog(@PathVariable UUID id) {
    datasources.load(access.current().companyId(), id);
    List<Map<String, String>> items = new ArrayList<>(userAttributes.catalog());
    int sent = 0;
    for (CatalogTable table : tables.findByDatasourceIdAndCompanyId(id, access.current().companyId())) {
      if (table.getRemovedAt() != null || !Boolean.TRUE.equals(table.getVisible())) continue;
      String schema = table.getSchemaName() == null ? "" : table.getSchemaName();
      String qualified = schema.isBlank() ? table.getTableName() : schema + "." + table.getTableName();
      if (!schema.isBlank()) items.add(catalogItem(schema, "Schema", schema, table.getTableName()));
      items.add(catalogItem(table.getTableName(), "Table", schema, table.getTableName()));
      items.add(catalogItem(qualified, "Table", schema, table.getTableName()));
      if (++sent >= 2000) break;
      for (CatalogColumn column : columns.findByTableIdOrderByOrdinalAsc(table.getId())) {
        String typeName = column.getDataType() == null ? "" : column.getDataType();
        String detail = table.getTableName() + (typeName.isBlank() ? "" : " " + typeName);
        items.add(catalogItem(column.getName(), "Column", schema, table.getTableName(), detail));
        items.add(catalogItem(table.getTableName() + "." + column.getName(), "Column", schema, table.getTableName(), detail));
        if (!schema.isBlank()) {
          items.add(catalogItem(qualified + "." + column.getName(), "Column", schema, table.getTableName(), detail));
        }
        if (++sent >= 2000) break;
      }
      if (sent >= 2000) break;
    }
    return Map.of("items", items);
  }

  @GetMapping(value = "/datasources/{id}/autocomplete/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @RequiresRole("editor")
  public SseEmitter autocomplete(@PathVariable UUID id, @RequestParam(defaultValue = "") String prefix) throws java.io.IOException {
    datasources.load(access.current().companyId(), id);
    boolean userAttr = prefix.trim().startsWith("@");
    String token = prefix.toLowerCase().replaceAll("[^a-z0-9_.@]", "");
    SseEmitter emitter = new SseEmitter(20_000L);
    int sent = 0;
    if (userAttr || token.startsWith("@")) {
      String needle = token.startsWith("@") ? token : "@" + token;
      for (Map<String, String> item : userAttributes.catalog()) {
        if (!item.getOrDefault("text", "").toLowerCase().startsWith(needle)) continue;
        emitter.send(SseEmitter.event().name("suggestion").data(item));
        sent++;
      }
    }
    if (userAttr || token.startsWith("@") || (token.length() < 1 && sent == 0)) {
      emitter.send(SseEmitter.event().name("done").data(Map.of("count", sent)));
      emitter.complete();
      return emitter;
    }
    UUID companyId = access.current().companyId();
    for (CatalogTable table : tables.findByDatasourceIdAndCompanyId(id, companyId)) {
      if (table.getRemovedAt() != null || !table.getVisible()) continue;
      String qualified = table.getSchemaName() + "." + table.getTableName();
      if (qualified.toLowerCase().contains(token) || table.getTableName().toLowerCase().startsWith(token)) {
        emitter.send(SseEmitter.event().name("suggestion").data(Map.of(
            "text", qualified,
            "kind", "Table"
        )));
        if (++sent >= 40) break;
      }
    }
    if (sent < 40) {
      for (CatalogTable table : tables.findByDatasourceIdAndCompanyId(id, companyId)) {
        if (table.getRemovedAt() != null || !table.getVisible()) continue;
        for (CatalogColumn column : columns.findByTableIdOrderByOrdinalAsc(table.getId())) {
          if (!column.getName().toLowerCase().startsWith(token)) continue;
          emitter.send(SseEmitter.event().name("suggestion").data(Map.of(
              "text", column.getName(),
              "kind", table.getTableName() + " " + column.getDataType()
          )));
          if (++sent >= 40) break;
        }
        if (sent >= 40) break;
      }
    }
    emitter.send(SseEmitter.event().name("done").data(Map.of("count", sent)));
    emitter.complete();
    return emitter;
  }

  @GetMapping("/datasources/{id}/tables")
  @RequiresRole("viewer")
  public PageResponse<Map<String, Object>> tables(@PathVariable UUID id, @RequestParam(required = false) Boolean visible, @RequestParam(required = false) String q) {
    datasources.load(access.current().companyId(), id);
    List<Map<String, Object>> rows = tables.findByDatasourceIdAndCompanyId(id, access.current().companyId()).stream()
        .filter(table -> table.getRemovedAt() == null)
        .filter(table -> visible == null || table.getVisible() == visible)
        .filter(table -> q == null || table.getTableName().toLowerCase().contains(q.toLowerCase()))
        .map(this::tableView)
        .toList();
    return new PageResponse<>(rows, 0, rows.size());
  }

  @GetMapping("/tables/{id}")
  @RequiresRole("viewer")
  public Map<String, Object> table(@PathVariable UUID id) {
    return tableView(requireTable(id));
  }

  @PatchMapping("/tables/{id}")
  @RequiresRole("admin")
  public Map<String, Object> patchTable(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    CatalogTable table = requireTable(id);
    if (body.containsKey("visible")) table.setVisible(Boolean.TRUE.equals(body.get("visible")));
    if (body.containsKey("display_name")) table.setDisplayName(string(body.get("display_name")));
    if (body.containsKey("user_note")) table.setUserNote(string(body.get("user_note")));
    return tableView(table);
  }

  @GetMapping("/columns/{id}")
  @RequiresRole("viewer")
  public Map<String, Object> column(@PathVariable UUID id) {
    return columnView(requireColumn(id));
  }

  @PatchMapping("/columns/{id}")
  @RequiresRole("admin")
  public Map<String, Object> patchColumn(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    CatalogColumn column = requireColumn(id);
    if (body.containsKey("visible")) column.setVisible(Boolean.TRUE.equals(body.get("visible")));
    if (body.containsKey("is_pii")) column.setPii(Boolean.TRUE.equals(body.get("is_pii")));
    if (body.containsKey("display_name")) column.setDisplayName(string(body.get("display_name")));
    if (body.containsKey("user_note")) column.setUserNote(string(body.get("user_note")));
    if (body.containsKey("exclude_from_samples")) column.setExcludeFromSamples(Boolean.TRUE.equals(body.get("exclude_from_samples")));
    return columnView(column);
  }

  @GetMapping("/datasources/{id}/fks")
  @RequiresRole("admin")
  public PageResponse<Map<String, Object>> fks(@PathVariable UUID id) {
    datasources.load(access.current().companyId(), id);
    var rows = foreignKeys.findByDatasourceIdAndCompanyId(id, access.current().companyId()).stream().map(this::fkView).toList();
    return new PageResponse<>(rows, 0, rows.size());
  }

  @PostMapping("/datasources/{id}/fks")
  @RequiresRole("admin")
  public Map<String, Object> addFk(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    Datasource datasource = datasources.load(access.current().companyId(), id);
    CatalogFk fk = new CatalogFk();
    fk.setCompanyId(datasource.getCompanyId());
    fk.setDatasourceId(id);
    fk.setConstraintName(String.valueOf(body.getOrDefault("constraint_name", "manual")));
    fk.setFromColumnId(UUID.fromString(String.valueOf(body.get("from_column_id"))));
    fk.setToColumnId(UUID.fromString(String.valueOf(body.get("to_column_id"))));
    fk.setInferred(false);
    foreignKeys.save(fk);
    return fkView(fk);
  }

  @DeleteMapping("/datasources/{id}/fks/{fkId}")
  @RequiresRole("admin")
  public Map<String, Object> deleteFk(@PathVariable UUID id, @PathVariable UUID fkId) {
    datasources.load(access.current().companyId(), id);
    CatalogFk fk = foreignKeys.findByIdAndCompanyId(fkId, access.current().companyId())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Foreign key not found"));
    foreignKeys.delete(fk);
    return Map.of("status", "deleted");
  }

  @GetMapping("/datasources/{id}/sync-schedule")
  @RequiresRole("admin")
  public Map<String, Object> schedule(@PathVariable UUID id) {
    return datasources.schedule(access.current().companyId(), id);
  }

  @PutMapping("/datasources/{id}/sync-schedule")
  @RequiresRole("admin")
  public Map<String, Object> saveSchedule(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    return datasources.saveSchedule(access.current().companyId(), id, string(body.get("cron")), Boolean.TRUE.equals(body.get("enabled")));
  }

  @GetMapping("/datasources/{id}/values")
  @RequiresRole("viewer")
  public Map<String, Object> values(@PathVariable UUID id, @RequestParam String table, @RequestParam String column, @RequestParam(required = false) String q) {
    Datasource datasource = datasources.load(access.current().companyId(), id);
    String[] parts = table.split("\\.");
    if (parts.length != 2 || !column.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "table must be schema.table");
    }
    CatalogTable catalogTable = tables.findByDatasourceIdAndSchemaNameAndTableName(id, parts[0], parts[1])
        .filter(found -> found.getCompanyId().equals(access.current().companyId()) && found.getVisible())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Table not found"));
    boolean known = columns.findByTableIdOrderByOrdinalAsc(catalogTable.getId()).stream().anyMatch(item -> item.getName().equals(column) && item.getVisible());
    if (!known) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Column not found");
    String sql = "SELECT DISTINCT " + DriverSupport.qualify(datasource.getType(), column)
        + " FROM " + DriverSupport.qualify(datasource.getType(), table)
        + " WHERE " + DriverSupport.qualify(datasource.getType(), column) + " IS NOT NULL";
    List<Object> values = new ArrayList<>();
    try (Connection connection = pools.connection(datasource, datasources.password(datasource));
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setMaxRows(500);
      statement.setQueryTimeout(Math.min(10, datasource.getQueryTimeoutSeconds()));
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next() && values.size() < 500) values.add(rows.getObject(1));
      }
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, "DB_ERROR", "Could not load values");
    }
    if (q != null && !q.isBlank()) {
      values.removeIf(value -> value == null || !String.valueOf(value).toLowerCase().contains(q.toLowerCase()));
    }
    return Map.of("values", values);
  }

  private CatalogTable requireTable(UUID id) {
    return tables.findByIdAndCompanyId(id, access.current().companyId())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Table not found"));
  }

  private CatalogColumn requireColumn(UUID id) {
    return columns.findByIdAndCompanyId(id, access.current().companyId())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Column not found"));
  }

  private Map<String, Object> tableView(CatalogTable table) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", table.getId());
    row.put("schema_name", table.getSchemaName());
    row.put("table_name", table.getTableName());
    row.put("table_type", table.getTableType());
    row.put("visible", table.getVisible());
    row.put("display_name", table.getDisplayName());
    row.put("user_note", table.getUserNote());
    row.put("columns", columns.findByTableIdOrderByOrdinalAsc(table.getId()).stream().map(this::columnView).toList());
    return row;
  }

  private Map<String, Object> columnView(CatalogColumn column) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", column.getId());
    row.put("name", column.getName());
    row.put("data_type", column.getDataType());
    row.put("normalized_type", column.getNormalizedType());
    row.put("is_nullable", column.getNullable());
    row.put("is_pk", column.getPk());
    row.put("is_pii", column.getPii());
    row.put("visible", column.getVisible());
    row.put("display_name", column.getDisplayName());
    row.put("user_note", column.getUserNote());
    return row;
  }

  private Map<String, Object> fkView(CatalogFk fk) {
    return Map.of("id", fk.getId(), "constraint_name", fk.getConstraintName(), "from_column_id", fk.getFromColumnId(),
        "to_column_id", fk.getToColumnId(), "is_inferred", fk.getInferred());
  }

  private static Map<String, String> catalogItem(String text, String kind, String schema, String table) {
    return catalogItem(text, kind, schema, table, kind);
  }

  private static Map<String, String> catalogItem(String text, String kind, String schema, String table, String detail) {
    Map<String, String> row = new LinkedHashMap<>();
    row.put("text", text);
    row.put("kind", kind);
    if (detail != null && !detail.isBlank()) row.put("detail", detail);
    if (schema != null && !schema.isBlank()) row.put("schema", schema);
    if (table != null && !table.isBlank()) row.put("table", table);
    return row;
  }

  private static String string(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}

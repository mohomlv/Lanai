package com.landata.datasource.application;

import com.landata.datasource.infrastructure.jdbc.PoolManager;

import com.landata.datasource.infrastructure.persistence.CatalogColumn;
import com.landata.datasource.infrastructure.persistence.CatalogColumnRepository;
import com.landata.datasource.infrastructure.persistence.CatalogFk;
import com.landata.datasource.infrastructure.persistence.CatalogFkRepository;
import com.landata.datasource.infrastructure.persistence.CatalogTable;
import com.landata.datasource.infrastructure.persistence.CatalogTableRepository;
import com.landata.datasource.infrastructure.persistence.Datasource;
import com.landata.datasource.infrastructure.persistence.DatasourceRepository;
import com.landata.shared.log.LogScrubber;
import com.landata.shared.security.Hashes;
import com.landata.datasource.infrastructure.jdbc.DriverSupport;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogSync {
  private static final Set<String> SKIP_SCHEMAS = Set.of(
      "pg_catalog", "information_schema", "pg_toast", "sys", "guest"
  );
  private final DatasourceRepository datasources;
  private final DatasourceService datasourceService;
  private final CatalogTableRepository tables;
  private final CatalogColumnRepository columns;
  private final CatalogFkRepository foreignKeys;
  private final PoolManager pools;
  private final JobScheduler jobs;

  public CatalogSync(
      DatasourceRepository datasources,
      @Lazy DatasourceService datasourceService,
      CatalogTableRepository tables,
      CatalogColumnRepository columns,
      CatalogFkRepository foreignKeys,
      PoolManager pools,
      JobScheduler jobs
  ) {
    this.datasources = datasources;
    this.datasourceService = datasourceService;
    this.tables = tables;
    this.columns = columns;
    this.foreignKeys = foreignKeys;
    this.pools = pools;
    this.jobs = jobs;
  }

  public void enqueue(UUID companyId, UUID datasourceId) {
    datasourceService.load(companyId, datasourceId);
    jobs.enqueue(() -> sync(datasourceId));
  }

  @Transactional
  public void sync(UUID datasourceId) {
    Datasource datasource = datasources.findById(datasourceId).orElseThrow();
    if (datasource.getDeletedAt() != null) return;
    String password = datasourceService.password(datasource);
    List<RawTable> snapshot;
    List<RawFk> relations;
    try (Connection connection = pools.connection(datasource, password)) {
      DatabaseMetaData meta = connection.getMetaData();
      snapshot = read(meta, datasource.getType());
      relations = readForeignKeys(meta, snapshot);
    } catch (Exception ex) {
      datasource.setStatus("error");
      datasource.setLastTestError(LogScrubber.scrub(ex.getMessage()));
      return;
    }
    String fingerprint = Hashes.sha256(fingerprintText(snapshot));
    Map<String, CatalogTable> existing = new HashMap<>();
    for (CatalogTable table : tables.findByDatasourceIdAndCompanyId(datasourceId, datasource.getCompanyId())) {
      existing.put(key(table.getSchemaName(), table.getTableName()), table);
    }
    for (RawTable raw : snapshot) {
      CatalogTable table = existing.remove(key(raw.schema(), raw.name()));
      if (table == null) {
        table = new CatalogTable();
        table.setCompanyId(datasource.getCompanyId());
        table.setDatasourceId(datasourceId);
        table.setSchemaName(raw.schema());
        table.setTableName(raw.name());
      }
      table.setTableType(raw.type());
      table.setRemovedAt(null);
      table.setUpdatedAt(Instant.now());
      tables.save(table);
      writeColumns(datasource, table, raw.columns());
    }
    writeForeignKeys(datasource, relations);
    for (CatalogTable stale : existing.values()) {
      stale.setRemovedAt(Instant.now());
    }
    datasource.setSchemaFingerprint(fingerprint);
    datasource.setLastSyncedAt(Instant.now());
    datasource.setStatus("connected");
  }

  public int sample(UUID datasourceId) {
    Datasource datasource = datasources.findById(datasourceId).orElseThrow();
    if (!"schema_and_samples".equals(datasource.getAiSharingLevel())) return 0;
    String password = datasourceService.password(datasource);
    int stored = 0;
    try (Connection connection = pools.connection(datasource, password)) {
      int tablesLeft = 80;
      for (CatalogTable table : tables.findByDatasourceIdAndCompanyId(datasourceId, datasource.getCompanyId())) {
        if (table.getRemovedAt() != null || !table.getVisible()) continue;
        if (tablesLeft-- == 0) break;
        int columnsLeft = 8;
        for (CatalogColumn column : columns.findByTableIdOrderByOrdinalAsc(table.getId())) {
          if (columnsLeft == 0) break;
          if (column.getPii() || column.getExcludeFromSamples() || !column.getVisible()) continue;
          String normalized = column.getNormalizedType();
          if ("binary".equals(normalized) || "json".equals(normalized)) continue;
          columnsLeft--;
          List<Object> values = readSamples(connection, datasource, table, column);
          if (values.isEmpty()) continue;
          column.setSampleValues(values);
          columns.save(column);
          stored++;
        }
      }
    } catch (Exception ignored) {
      return stored;
    }
    return stored;
  }

  private List<Object> readSamples(Connection connection, Datasource datasource, CatalogTable table, CatalogColumn column) {
    String sql = DriverSupport.sampleDistinct(datasource.getType(), table.getSchemaName(), table.getTableName(), column.getName(), 5);
    List<Object> values = new ArrayList<>();
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setQueryTimeout(Math.min(3, Math.max(1, datasource.getQueryTimeoutSeconds())));
      statement.setMaxRows(5);
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next() && values.size() < 5) {
          Object value = rows.getObject(1);
          if (value == null) continue;
          String text = String.valueOf(value).replaceAll("[\\r\\n]+", " ").trim();
          if (text.isEmpty()) continue;
          if (text.length() > 80) text = text.substring(0, 80);
          values.add(text);
        }
      }
    } catch (SQLException ex) {
      return List.of();
    }
    return values;
  }

  private void writeForeignKeys(Datasource datasource, List<RawFk> relations) {
    foreignKeys.deleteByDatasourceIdAndCompanyIdAndInferredFalse(datasource.getId(), datasource.getCompanyId());
    Map<String, UUID> columnIds = new HashMap<>();
    for (CatalogTable table : tables.findByDatasourceIdAndCompanyId(datasource.getId(), datasource.getCompanyId())) {
      if (table.getRemovedAt() != null) continue;
      for (CatalogColumn column : columns.findByTableIdOrderByOrdinalAsc(table.getId())) {
        columnIds.put(key(table.getSchemaName(), table.getTableName()) + "." + column.getName(), column.getId());
      }
    }
    for (RawFk relation : relations) {
      UUID from = columnIds.get(key(relation.fromSchema(), relation.fromTable()) + "." + relation.fromColumn());
      UUID to = columnIds.get(key(relation.toSchema(), relation.toTable()) + "." + relation.toColumn());
      if (from == null || to == null) continue;
      CatalogFk row = new CatalogFk();
      row.setCompanyId(datasource.getCompanyId());
      row.setDatasourceId(datasource.getId());
      row.setConstraintName(relation.name());
      row.setOrdinal(relation.ordinal());
      row.setFromColumnId(from);
      row.setToColumnId(to);
      row.setInferred(false);
      foreignKeys.save(row);
    }
  }

  private List<RawFk> readForeignKeys(DatabaseMetaData meta, List<RawTable> snapshot) {
    List<RawFk> found = new ArrayList<>();
    for (RawTable table : snapshot) {
      try (ResultSet rows = meta.getImportedKeys(null, table.schema(), table.name())) {
        while (rows.next()) {
          found.add(new RawFk(
              rows.getString("FK_NAME"),
              rows.getInt("KEY_SEQ"),
              schemaOf(rows.getString("FKTABLE_SCHEM"), table.schema()),
              rows.getString("FKTABLE_NAME"),
              rows.getString("FKCOLUMN_NAME"),
              schemaOf(rows.getString("PKTABLE_SCHEM"), table.schema()),
              rows.getString("PKTABLE_NAME"),
              rows.getString("PKCOLUMN_NAME")
          ));
        }
      } catch (SQLException ex) {
        return found;
      }
    }
    return found;
  }

  private static String schemaOf(String schema, String fallback) {
    if (schema == null || schema.isBlank()) return fallback;
    return schema;
  }

  private void writeColumns(Datasource datasource, CatalogTable table, List<RawColumn> rawColumns) {
    Map<String, CatalogColumn> existing = new HashMap<>();
    for (CatalogColumn column : columns.findByTableIdOrderByOrdinalAsc(table.getId())) {
      existing.put(column.getName(), column);
    }
    for (RawColumn raw : rawColumns) {
      CatalogColumn column = existing.get(raw.name());
      if (column == null) {
        column = new CatalogColumn();
        column.setCompanyId(datasource.getCompanyId());
        column.setTableId(table.getId());
        column.setName(raw.name());
      }
      column.setOrdinal(raw.ordinal());
      column.setDataType(raw.type());
      column.setNormalizedType(normalize(raw.type()));
      column.setNullable(raw.nullable());
      column.setPk(raw.primaryKey());
      columns.save(column);
    }
  }

  private List<RawTable> read(DatabaseMetaData meta, String type) throws Exception {
    List<RawTable> found = new ArrayList<>();
    try (ResultSet rows = meta.getTables(null, null, "%", new String[]{"TABLE", "VIEW"})) {
      while (rows.next()) {
        String schema = rows.getString("TABLE_SCHEM");
        if (schema != null && SKIP_SCHEMAS.contains(schema.toLowerCase(Locale.ROOT))) continue;
        String name = rows.getString("TABLE_NAME");
        String kind = "VIEW".equalsIgnoreCase(rows.getString("TABLE_TYPE")) ? "view" : "table";
        List<RawColumn> cols = new ArrayList<>();
        Set<String> primary = primaryKeys(meta, schema, name);
        try (ResultSet columns = meta.getColumns(null, schema, name, "%")) {
          while (columns.next()) {
            cols.add(new RawColumn(
                columns.getString("COLUMN_NAME"),
                columns.getInt("ORDINAL_POSITION"),
                columns.getString("TYPE_NAME"),
                columns.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                primary.contains(columns.getString("COLUMN_NAME"))
            ));
          }
        }
        found.add(new RawTable(schema == null ? "public" : schema, name, kind, cols));
      }
    }
    return found;
  }

  private Set<String> primaryKeys(DatabaseMetaData meta, String schema, String table) throws Exception {
    java.util.HashSet<String> keys = new java.util.HashSet<>();
    try (ResultSet rows = meta.getPrimaryKeys(null, schema, table)) {
      while (rows.next()) keys.add(rows.getString("COLUMN_NAME"));
    }
    return keys;
  }

  private static String normalize(String type) {
    String value = type == null ? "" : type.toLowerCase(Locale.ROOT);
    if (value.contains("bool")) return "boolean";
    if (value.contains("int") || value.contains("serial")) return "integer";
    if (value.contains("numeric") || value.contains("decimal") || value.contains("double") || value.contains("float") || value.contains("real")) return "decimal";
    if (value.contains("timestamp") || value.contains("datetime")) return "datetime";
    if (value.equals("date")) return "date";
    if (value.contains("time")) return "time";
    if (value.contains("json")) return "json";
    if (value.contains("bytea") || value.contains("blob") || value.contains("binary")) return "binary";
    if (value.contains("char") || value.contains("text") || value.contains("clob")) return "text";
    return "other";
  }

  private static String fingerprintText(List<RawTable> tables) {
    StringBuilder builder = new StringBuilder();
    for (RawTable table : tables) {
      builder.append(table.schema()).append('.').append(table.name());
      for (RawColumn column : table.columns()) {
        builder.append('|').append(column.name()).append(':').append(column.type());
      }
      builder.append('\n');
    }
    return builder.toString();
  }

  private static String key(String schema, String table) {
    return schema + "." + table;
  }

  private record RawTable(String schema, String name, String type, List<RawColumn> columns) {}
  private record RawColumn(String name, int ordinal, String type, boolean nullable, boolean primaryKey) {}
  private record RawFk(
      String name,
      int ordinal,
      String fromSchema,
      String fromTable,
      String fromColumn,
      String toSchema,
      String toTable,
      String toColumn
  ) {}
}

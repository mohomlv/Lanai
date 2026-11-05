package com.landata.datasource.infrastructure.jdbc.dialect;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * One engine, same idea as a Metabase driver folder: URL, quoting, and the
 * small SQL shapes that differ between databases live here instead of a switch.
 */
public interface SqlDialect {
  boolean supports(String type);

  String jdbcUrl(String type, String host, int port, String database, String sslMode);

  default String quote(String ident) {
    return "\"" + ident.replace("\"", "\"\"") + "\"";
  }

  default String sampleDistinct(String schema, String table, String column, int limit) {
    int rows = Math.max(1, Math.min(limit, 20));
    return "SELECT DISTINCT " + quote(column) + " FROM " + qualify(schema, table)
        + " WHERE " + quote(column) + " IS NOT NULL LIMIT " + rows;
  }

  default void applyReadOnly(Connection connection, String type) throws SQLException {
    connection.setReadOnly(true);
  }

  default boolean writable(Connection connection, String type) {
    return false;
  }

  default String qualify(String schema, String table) {
    if (schema == null || schema.isBlank()) return quote(table);
    return quote(schema) + "." + quote(table);
  }
}

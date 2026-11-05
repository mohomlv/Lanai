package com.landata.datasource.infrastructure.jdbc.dialect;

import java.sql.Connection;
import java.sql.SQLException;

final class PostgresDialect implements SqlDialect {
  @Override
  public boolean supports(String type) {
    return "postgres".equals(type) || "redshift".equals(type);
  }

  @Override
  public String jdbcUrl(String type, String host, int port, String database, String sslMode) {
    String db = database == null ? "" : database;
    return "jdbc:postgresql://" + host + ":" + port + "/" + db + "?sslmode=" + ssl(sslMode);
  }

  @Override
  public void applyReadOnly(Connection connection, String type) throws SQLException {
    connection.setReadOnly(true);
    if ("postgres".equals(type)) {
      try (var statement = connection.createStatement()) {
        statement.execute("SET default_transaction_read_only = on");
      }
    }
  }

  @Override
  public boolean writable(Connection connection, String type) {
    if (!"postgres".equals(type)) return false;
    try (var statement = connection.createStatement();
         var rows = statement.executeQuery("SELECT usesuper OR usecreatedb FROM pg_user WHERE usename = current_user")) {
      return rows.next() && rows.getBoolean(1);
    } catch (SQLException ex) {
      return false;
    }
  }

  private static String ssl(String ssl) {
    return switch (ssl == null ? "require" : ssl) {
      case "disable", "require", "verify-ca", "verify-full" -> ssl == null ? "require" : ssl;
      default -> "require";
    };
  }
}

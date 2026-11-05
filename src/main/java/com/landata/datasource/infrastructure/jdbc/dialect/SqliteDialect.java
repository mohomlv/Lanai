package com.landata.datasource.infrastructure.jdbc.dialect;

final class SqliteDialect implements SqlDialect {
  @Override
  public boolean supports(String type) {
    return "sqlite".equals(type);
  }

  @Override
  public String jdbcUrl(String type, String host, int port, String database, String sslMode) {
    return "jdbc:sqlite:" + (database == null ? "" : database);
  }
}

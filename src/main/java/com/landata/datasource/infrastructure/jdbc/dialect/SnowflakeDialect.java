package com.landata.datasource.infrastructure.jdbc.dialect;

final class SnowflakeDialect implements SqlDialect {
  @Override
  public boolean supports(String type) {
    return "snowflake".equals(type);
  }

  @Override
  public String jdbcUrl(String type, String host, int port, String database, String sslMode) {
    String db = database == null ? "" : database;
    return "jdbc:snowflake://" + host + "/?db=" + db;
  }
}

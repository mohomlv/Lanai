package com.landata.datasource.infrastructure.jdbc.dialect;

final class TrinoDialect implements SqlDialect {
  @Override
  public boolean supports(String type) {
    return "trino".equals(type) || "starburst".equals(type) || "presto".equals(type);
  }

  @Override
  public String jdbcUrl(String type, String host, int port, String database, String sslMode) {
    String db = database == null ? "" : database;
    if ("presto".equals(type)) return "jdbc:presto://" + host + ":" + port + "/" + db;
    String ssl = sslMode == null ? "require" : sslMode;
    return "jdbc:trino://" + host + ":" + port + "/" + db + "?SSL=" + (!"disable".equals(ssl));
  }
}

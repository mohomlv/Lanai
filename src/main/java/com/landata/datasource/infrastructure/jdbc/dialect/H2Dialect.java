package com.landata.datasource.infrastructure.jdbc.dialect;

final class H2Dialect implements SqlDialect {
  @Override
  public boolean supports(String type) {
    return "h2".equals(type);
  }

  @Override
  public String jdbcUrl(String type, String host, int port, String database, String sslMode) {
    String db = database == null ? "" : database;
    return "jdbc:h2:tcp://" + host + ":" + port + "/" + db;
  }
}

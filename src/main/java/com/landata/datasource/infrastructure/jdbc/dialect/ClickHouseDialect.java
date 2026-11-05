package com.landata.datasource.infrastructure.jdbc.dialect;

final class ClickHouseDialect implements SqlDialect {
  @Override
  public boolean supports(String type) {
    return "clickhouse".equals(type);
  }

  @Override
  public String jdbcUrl(String type, String host, int port, String database, String sslMode) {
    String db = database == null ? "" : database;
    String ssl = sslMode == null ? "require" : sslMode;
    return "jdbc:clickhouse://" + host + ":" + port + "/" + db + "?ssl=" + (!"disable".equals(ssl));
  }

  @Override
  public String quote(String ident) {
    return "`" + ident.replace("`", "``") + "`";
  }
}

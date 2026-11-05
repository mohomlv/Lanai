package com.landata.datasource.infrastructure.jdbc.dialect;

final class OracleDialect implements SqlDialect {
  @Override
  public boolean supports(String type) {
    return "oracle".equals(type);
  }

  @Override
  public String jdbcUrl(String type, String host, int port, String database, String sslMode) {
    String db = database == null ? "" : database;
    return "jdbc:oracle:thin:@//" + host + ":" + port + "/" + db;
  }

  @Override
  public String sampleDistinct(String schema, String table, String column, int limit) {
    int rows = Math.max(1, Math.min(limit, 20));
    return "SELECT DISTINCT " + quote(column) + " FROM " + qualify(schema, table)
        + " WHERE " + quote(column) + " IS NOT NULL FETCH FIRST " + rows + " ROWS ONLY";
  }
}

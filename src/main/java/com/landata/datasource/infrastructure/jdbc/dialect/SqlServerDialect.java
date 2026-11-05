package com.landata.datasource.infrastructure.jdbc.dialect;

final class SqlServerDialect implements SqlDialect {
  @Override
  public boolean supports(String type) {
    return "sqlserver".equals(type);
  }

  @Override
  public String jdbcUrl(String type, String host, int port, String database, String sslMode) {
    String db = database == null ? "" : database;
    String ssl = sslMode == null ? "require" : sslMode;
    return "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" + db
        + ";encrypt=" + (!"disable".equals(ssl)) + ";trustServerCertificate=" + "disable".equals(ssl);
  }

  @Override
  public String quote(String ident) {
    return "[" + ident.replace("]", "]]") + "]";
  }

  @Override
  public String sampleDistinct(String schema, String table, String column, int limit) {
    int rows = Math.max(1, Math.min(limit, 20));
    return "SELECT DISTINCT TOP (" + rows + ") " + quote(column) + " FROM " + qualify(schema, table)
        + " WHERE " + quote(column) + " IS NOT NULL";
  }
}

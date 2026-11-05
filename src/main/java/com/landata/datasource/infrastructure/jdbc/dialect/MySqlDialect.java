package com.landata.datasource.infrastructure.jdbc.dialect;

import java.sql.Connection;
import java.sql.SQLException;

final class MySqlDialect implements SqlDialect {
  @Override
  public boolean supports(String type) {
    return "mysql".equals(type) || "mariadb".equals(type);
  }

  @Override
  public String jdbcUrl(String type, String host, int port, String database, String sslMode) {
    String db = database == null ? "" : database;
    return "jdbc:mysql://" + host + ":" + port + "/" + db + "?allowMultiQueries=false&sslMode=" + ssl(sslMode);
  }

  @Override
  public String quote(String ident) {
    return "`" + ident.replace("`", "``") + "`";
  }

  @Override
  public boolean writable(Connection connection, String type) {
    try (var statement = connection.createStatement();
         var rows = statement.executeQuery(
             "SELECT COUNT(*) FROM information_schema.user_privileges "
                 + "WHERE grantee LIKE CONCAT('%', CURRENT_USER(), '%') "
                 + "AND privilege_type IN ('INSERT','UPDATE','DELETE','CREATE','DROP','ALL PRIVILEGES')")) {
      return rows.next() && rows.getInt(1) > 0;
    } catch (SQLException ex) {
      return false;
    }
  }

  private static String ssl(String ssl) {
    return switch (ssl == null ? "" : ssl) {
      case "disable" -> "DISABLED";
      case "verify-full" -> "VERIFY_IDENTITY";
      default -> "REQUIRED";
    };
  }
}

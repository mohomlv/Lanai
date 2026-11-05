package com.landata.datasource.infrastructure.jdbc;

import com.landata.datasource.infrastructure.jdbc.dialect.DialectRegistry;
import com.landata.datasource.infrastructure.jdbc.dialect.SqlDialect;
import com.landata.shared.api.ApiException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

public final class DriverSupport {
  private static final Set<String> BLOCKED_PROPERTIES = Set.of(
      "socketfactory", "sslfactory", "autodeserialize", "allowloadlocalinfile", "allowurlinlocalinfile",
      "allowmultiqueries", "queryinterceptors", "connectionattributes"
  );
  private static final Map<String, Set<String>> ALLOWED = Map.of(
      "postgres", Set.of("logintimeout", "connecttimeout", "sockettimeout", "applicationname", "sslmode"),
      "mysql", Set.of("logintimeout", "connecttimeout", "sockettimeout"),
      "mariadb", Set.of("logintimeout", "connecttimeout", "sockettimeout"),
      "sqlserver", Set.of("logintimeout", "connecttimeout", "sockettimeout", "applicationname")
  );

  private DriverSupport() {}

  public static String jdbcUrl(String type, String host, int port, String database, String sslMode) {
    return dialect(type).jdbcUrl(type, host, port, database, sslMode);
  }

  public static void applyReadOnly(Connection connection, String type) throws SQLException {
    dialect(type).applyReadOnly(connection, type);
  }

  public static boolean writable(Connection connection, String type) {
    return dialect(type).writable(connection, type);
  }

  public static String qualify(String type, String ref) {
    SqlDialect dialect = dialect(type);
    StringBuilder out = new StringBuilder();
    for (String part : ref.split("\\.")) {
      if (!out.isEmpty()) out.append('.');
      out.append(dialect.quote(part));
    }
    return out.toString();
  }

  public static String quote(String type, String ident) {
    return dialect(type).quote(ident);
  }

  public static String sampleDistinct(String type, String schema, String table, String column, int limit) {
    return dialect(type).sampleDistinct(schema, table, column, limit);
  }

  private static SqlDialect dialect(String type) {
    return DialectRegistry.require(type);
  }

  public static Map<String, String> filterProperties(String type, Map<String, Object> jdbc) {
    Set<String> allowed = ALLOWED.getOrDefault(type, Set.of());
    java.util.LinkedHashMap<String, String> out = new java.util.LinkedHashMap<>();
    if (jdbc == null) return out;
    for (var entry : jdbc.entrySet()) {
      String key = entry.getKey().toLowerCase(Locale.ROOT).replace("_", "");
      if (BLOCKED_PROPERTIES.contains(key) || key.contains("factory") || key.contains("interceptor")) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_JDBC_PROPERTY", "JDBC property is not allowed");
      }
      if (!allowed.contains(key)) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_JDBC_PROPERTY", "JDBC property is not allowed: " + entry.getKey());
      }
      out.put(entry.getKey(), String.valueOf(entry.getValue()));
    }
    return out;
  }

}

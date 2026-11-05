package com.landata.datasource.infrastructure.jdbc.dialect;

import com.landata.shared.api.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;

public final class DialectRegistry {
  private static final List<SqlDialect> DIALECTS = List.of(
      new PostgresDialect(),
      new MySqlDialect(),
      new SqlServerDialect(),
      new OracleDialect(),
      new ClickHouseDialect(),
      new SnowflakeDialect(),
      new TrinoDialect(),
      new H2Dialect(),
      new SqliteDialect()
  );

  private DialectRegistry() {}

  public static SqlDialect require(String type) {
    for (SqlDialect dialect : DIALECTS) {
      if (dialect.supports(type)) return dialect;
    }
    throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_TYPE", "Database type is not supported");
  }
}

package com.landata.datasource.infrastructure.jdbc;

import com.landata.datasource.application.JdbcUrlBuilder;
import org.springframework.stereotype.Component;

@Component
public class DefaultJdbcUrlBuilder implements JdbcUrlBuilder {
  @Override
  public String build(String type, String host, int port, String database, String sslMode) {
    return DriverSupport.jdbcUrl(type, host, port, database, sslMode);
  }
}

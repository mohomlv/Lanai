package com.landata.datasource.application;

/**
 * Port for building customer JDBC URLs from typed connection fields.
 * Implementations must never accept a raw user-supplied JDBC URL.
 */
public interface JdbcUrlBuilder {
  String build(String type, String host, int port, String database, String sslMode);
}

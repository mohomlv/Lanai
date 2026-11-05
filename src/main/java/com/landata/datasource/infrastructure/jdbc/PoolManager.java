package com.landata.datasource.infrastructure.jdbc;

import com.landata.datasource.application.JdbcUrlBuilder;
import com.landata.datasource.infrastructure.persistence.Datasource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class PoolManager implements AutoCloseable {
  private final ConcurrentHashMap<UUID, HikariDataSource> pools = new ConcurrentHashMap<>();
  private final HostValidator hosts;
  private final JdbcUrlBuilder jdbcUrls;

  public PoolManager(HostValidator hosts, JdbcUrlBuilder jdbcUrls) {
    this.hosts = hosts;
    this.jdbcUrls = jdbcUrls;
  }

  public Connection connection(Datasource datasource, String password) throws SQLException {
    HostValidator.Resolved resolved = resolve(datasource.getType(), datasource.getHost(), datasource.getPort());
    HikariDataSource pool = pools.compute(datasource.getId(), (id, existing) -> {
      if (existing != null && !existing.isClosed()) return existing;
      return open(datasource, password, resolved);
    });
    Connection connection = pool.getConnection();
    DriverSupport.applyReadOnly(connection, datasource.getType());
    return connection;
  }

  public Connection test(String type, String host, int port, String database, String username, String password, String sslMode)
      throws SQLException {
    HostValidator.Resolved resolved = resolve(type, host, port);
    String url = jdbcUrls.build(type, connectHost(type, host, resolved), resolved.port(), database, sslMode);
    Connection connection = java.sql.DriverManager.getConnection(url, username, password);
    DriverSupport.applyReadOnly(connection, type);
    return connection;
  }

  private HostValidator.Resolved resolve(String type, String host, int port) {
    if ("sqlite".equals(type)) return new HostValidator.Resolved("127.0.0.1", port);
    return hosts.check(host, port);
  }

  private static String connectHost(String type, String host, HostValidator.Resolved resolved) {
    return "snowflake".equals(type) ? host.trim() : resolved.address();
  }

  public void evict(UUID datasourceId) {
    HikariDataSource pool = pools.remove(datasourceId);
    if (pool != null) pool.close();
  }

  private HikariDataSource open(Datasource datasource, String password, HostValidator.Resolved resolved) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(jdbcUrls.build(
        datasource.getType(), connectHost(datasource.getType(), datasource.getHost(), resolved), resolved.port(),
        datasource.getDatabaseName(), datasource.getSslMode()));
    config.setUsername(datasource.getUsername());
    config.setPassword(password);
    config.setMaximumPoolSize(poolSize(datasource.getOptions()));
    config.setConnectionTimeout(10_000);
    config.setIdleTimeout(300_000);
    config.setPoolName("landata-" + datasource.getId());
    config.setReadOnly(true);
    return new HikariDataSource(config);
  }

  @SuppressWarnings("unchecked")
  private int poolSize(Map<String, Object> options) {
    if (options == null) return 3;
    Object pool = options.get("pool");
    if (pool instanceof Map<?, ?> map && map.get("max") instanceof Number number) {
      return Math.min(10, Math.max(1, number.intValue()));
    }
    return 3;
  }

  @Override
  public void close() {
    pools.values().forEach(HikariDataSource::close);
    pools.clear();
  }
}

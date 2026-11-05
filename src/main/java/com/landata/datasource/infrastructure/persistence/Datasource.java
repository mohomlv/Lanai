package com.landata.datasource.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


@Entity
@Table(name = "datasource")
public class Datasource  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "name")
  private String name;

  @Column(name = "type")
  private String type;

  @Column(name = "host")
  private String host;

  @Column(name = "port")
  private int port;

  @Column(name = "database_name")
  private String databaseName;

  @Column(name = "username")
  private String username;

  @Column(name = "secret_ciphertext")
  private byte[] secretCiphertext;

  @Column(name = "secret_key_id")
  private String secretKeyId;

  @Column(name = "ssl_mode")
  private String sslMode = "require";

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "options")
  private Map<String, Object> options = new LinkedHashMap<>();

  @Column(name = "description")
  private String description;

  @Column(name = "ai_sharing_level")
  private String aiSharingLevel = "schema_only";

  @Column(name = "query_timeout_seconds")
  private int queryTimeoutSeconds = 30;

  @Column(name = "max_rows")
  private int maxRows = 10000;

  @Column(name = "status")
  private String status = "new";

  @Column(name = "last_test_at")
  private Instant lastTestAt;

  @Column(name = "last_test_error")
  private String lastTestError;

  @Column(name = "last_synced_at")
  private Instant lastSyncedAt;

  @Column(name = "schema_fingerprint")
  private String schemaFingerprint;

  @Column(name = "server_version")
  private String serverVersion;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
    if (updatedAt == null) updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getCompanyId() { return companyId; }
  public void setCompanyId(UUID companyId) { this.companyId = companyId; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public String getType() { return type; }
  public void setType(String type) { this.type = type; }

  public String getHost() { return host; }
  public void setHost(String host) { this.host = host; }

  public int getPort() { return port; }
  public void setPort(int port) { this.port = port; }

  public String getDatabaseName() { return databaseName; }
  public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }

  public String getUsername() { return username; }
  public void setUsername(String username) { this.username = username; }

  public byte[] getSecretCiphertext() { return secretCiphertext; }
  public void setSecretCiphertext(byte[] secretCiphertext) { this.secretCiphertext = secretCiphertext; }

  public String getSecretKeyId() { return secretKeyId; }
  public void setSecretKeyId(String secretKeyId) { this.secretKeyId = secretKeyId; }

  public String getSslMode() { return sslMode; }
  public void setSslMode(String sslMode) { this.sslMode = sslMode; }

  public Map<String, Object> getOptions() { return options; }
  public void setOptions(Map<String, Object> options) { this.options = options; }

  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }

  public String getAiSharingLevel() { return aiSharingLevel; }
  public void setAiSharingLevel(String aiSharingLevel) { this.aiSharingLevel = aiSharingLevel; }

  public int getQueryTimeoutSeconds() { return queryTimeoutSeconds; }
  public void setQueryTimeoutSeconds(int queryTimeoutSeconds) { this.queryTimeoutSeconds = queryTimeoutSeconds; }

  public int getMaxRows() { return maxRows; }
  public void setMaxRows(int maxRows) { this.maxRows = maxRows; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public Instant getLastTestAt() { return lastTestAt; }
  public void setLastTestAt(Instant lastTestAt) { this.lastTestAt = lastTestAt; }

  public String getLastTestError() { return lastTestError; }
  public void setLastTestError(String lastTestError) { this.lastTestError = lastTestError; }

  public Instant getLastSyncedAt() { return lastSyncedAt; }
  public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }

  public String getSchemaFingerprint() { return schemaFingerprint; }
  public void setSchemaFingerprint(String schemaFingerprint) { this.schemaFingerprint = schemaFingerprint; }

  public String getServerVersion() { return serverVersion; }
  public void setServerVersion(String serverVersion) { this.serverVersion = serverVersion; }

  public UUID getCreatedBy() { return createdBy; }
  public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

  public Instant getDeletedAt() { return deletedAt; }
  public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

}

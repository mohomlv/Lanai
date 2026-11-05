package com.landata.ai.infrastructure.persistence;

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
@Table(name = "ai_provider_config")
public class AiProviderConfig  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "provider")
  private String provider;

  @Column(name = "api_key_ciphertext")
  private byte[] apiKeyCiphertext;

  @Column(name = "api_key_key_id")
  private String apiKeyKeyId;

  @Column(name = "default_model_id")
  private String defaultModelId;

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "model_params")
  private List<Object> modelParams = new ArrayList<>();

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "settings")
  private Map<String, Object> settings = new LinkedHashMap<>();

  @Column(name = "is_active")
  private boolean active = true;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

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

  public String getProvider() { return provider; }
  public void setProvider(String provider) { this.provider = provider; }

  public byte[] getApiKeyCiphertext() { return apiKeyCiphertext; }
  public void setApiKeyCiphertext(byte[] apiKeyCiphertext) { this.apiKeyCiphertext = apiKeyCiphertext; }

  public String getApiKeyKeyId() { return apiKeyKeyId; }
  public void setApiKeyKeyId(String apiKeyKeyId) { this.apiKeyKeyId = apiKeyKeyId; }

  public String getDefaultModelId() { return defaultModelId; }
  public void setDefaultModelId(String defaultModelId) { this.defaultModelId = defaultModelId; }

  public List<Object> getModelParams() { return modelParams; }
  public void setModelParams(List<Object> modelParams) { this.modelParams = modelParams; }

  public Map<String, Object> getSettings() { return settings; }
  public void setSettings(Map<String, Object> settings) { this.settings = settings; }

  public boolean getActive() { return active; }
  public void setActive(boolean active) { this.active = active; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}

package com.landata.query.infrastructure.persistence;

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
@Table(name = "question_version")
public class QuestionVersion  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "question_id")
  private UUID questionId;

  @Column(name = "version_no")
  private int versionNo;

  @Column(name = "mode")
  private String mode;

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "query_spec")
  private Map<String, Object> querySpec;

  @Column(name = "native_sql")
  private String nativeSql;

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "parameters")
  private List<Map<String, Object>> parameters = new ArrayList<>();

  @Column(name = "viz_template_key")
  private String vizTemplateKey = "table";

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "viz_config")
  private Map<String, Object> vizConfig = new LinkedHashMap<>();

@JdbcTypeCode(SqlTypes.JSON)

  @Column(name = "result_metadata")
  private Map<String, Object> resultMetadata;

  @Column(name = "change_note")
  private String changeNote;

  @Column(name = "source")
  private String source = "user";

  @Column(name = "ai_request_id")
  private UUID aiRequestId;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "created_at")
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getCompanyId() { return companyId; }
  public void setCompanyId(UUID companyId) { this.companyId = companyId; }

  public UUID getQuestionId() { return questionId; }
  public void setQuestionId(UUID questionId) { this.questionId = questionId; }

  public int getVersionNo() { return versionNo; }
  public void setVersionNo(int versionNo) { this.versionNo = versionNo; }

  public String getMode() { return mode; }
  public void setMode(String mode) { this.mode = mode; }

  public Map<String, Object> getQuerySpec() { return querySpec; }
  public void setQuerySpec(Map<String, Object> querySpec) { this.querySpec = querySpec; }

  public String getNativeSql() { return nativeSql; }
  public void setNativeSql(String nativeSql) { this.nativeSql = nativeSql; }

  public List<Map<String, Object>> getParameters() { return parameters; }
  public void setParameters(List<Map<String, Object>> parameters) { this.parameters = parameters; }

  public String getVizTemplateKey() { return vizTemplateKey; }
  public void setVizTemplateKey(String vizTemplateKey) { this.vizTemplateKey = vizTemplateKey; }

  public Map<String, Object> getVizConfig() { return vizConfig; }
  public void setVizConfig(Map<String, Object> vizConfig) { this.vizConfig = vizConfig; }

  public Map<String, Object> getResultMetadata() { return resultMetadata; }
  public void setResultMetadata(Map<String, Object> resultMetadata) { this.resultMetadata = resultMetadata; }

  public String getChangeNote() { return changeNote; }
  public void setChangeNote(String changeNote) { this.changeNote = changeNote; }

  public String getSource() { return source; }
  public void setSource(String source) { this.source = source; }

  public UUID getAiRequestId() { return aiRequestId; }
  public void setAiRequestId(UUID aiRequestId) { this.aiRequestId = aiRequestId; }

  public UUID getCreatedBy() { return createdBy; }
  public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}

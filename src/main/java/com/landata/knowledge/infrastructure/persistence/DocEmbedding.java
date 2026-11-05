package com.landata.knowledge.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "doc_embedding")
public class DocEmbedding {
  @Id
  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "datasource_id")
  private UUID datasourceId;

  @Column(name = "doc_id")
  private UUID docId;

  @Column(name = "chunk_index")
  private int chunkIndex;

  @Column(name = "chunk_kind")
  private String chunkKind;

  @Column(name = "chunk_text")
  private String chunkText;

  @Column(name = "token_count")
  private Integer tokenCount;

  @Column(name = "table_name")
  private String tableName;

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
  public UUID getDatasourceId() { return datasourceId; }
  public void setDatasourceId(UUID datasourceId) { this.datasourceId = datasourceId; }
  public UUID getDocId() { return docId; }
  public void setDocId(UUID docId) { this.docId = docId; }
  public int getChunkIndex() { return chunkIndex; }
  public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
  public String getChunkKind() { return chunkKind; }
  public void setChunkKind(String chunkKind) { this.chunkKind = chunkKind; }
  public String getChunkText() { return chunkText; }
  public void setChunkText(String chunkText) { this.chunkText = chunkText; }
  public Integer getTokenCount() { return tokenCount; }
  public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }
  public String getTableName() { return tableName; }
  public void setTableName(String tableName) { this.tableName = tableName; }
}

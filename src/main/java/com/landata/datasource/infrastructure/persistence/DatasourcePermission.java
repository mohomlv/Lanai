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
@Table(name = "datasource_permission")
public class DatasourcePermission  {
@Id

  @Column(name = "id")
  private UUID id = UUID.randomUUID();

  @Column(name = "company_id")
  private UUID companyId;

  @Column(name = "datasource_id")
  private UUID datasourceId;

  @Column(name = "role")
  private String role;

  @Column(name = "can_query")
  private boolean canQuery = true;

  @Column(name = "can_native_sql")
  private boolean canNativeSql = false;

  @Column(name = "can_manage")
  private boolean canManage = false;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getCompanyId() { return companyId; }
  public void setCompanyId(UUID companyId) { this.companyId = companyId; }

  public UUID getDatasourceId() { return datasourceId; }
  public void setDatasourceId(UUID datasourceId) { this.datasourceId = datasourceId; }

  public String getRole() { return role; }
  public void setRole(String role) { this.role = role; }

  public boolean getCanQuery() { return canQuery; }
  public void setCanQuery(boolean canQuery) { this.canQuery = canQuery; }

  public boolean getCanNativeSql() { return canNativeSql; }
  public void setCanNativeSql(boolean canNativeSql) { this.canNativeSql = canNativeSql; }

  public boolean getCanManage() { return canManage; }
  public void setCanManage(boolean canManage) { this.canManage = canManage; }

}

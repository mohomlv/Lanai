package com.landata.datasource.application;

import com.landata.datasource.infrastructure.persistence.Datasource;
import com.landata.datasource.infrastructure.persistence.DatasourcePermission;
import com.landata.datasource.infrastructure.persistence.DatasourcePermissionRepository;
import com.landata.shared.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DatasourceAuthorizer {
  private final DatasourcePermissionRepository permissions;

  public DatasourceAuthorizer(DatasourcePermissionRepository permissions) {
    this.permissions = permissions;
  }

  public void check(Datasource datasource, String role, boolean nativeSql) {
    String storedRole = "owner".equals(role) ? "admin" : role;
    DatasourcePermission permission = permissions.findByDatasourceIdAndRole(datasource.getId(), storedRole).orElse(null);
    boolean canQuery = permission == null || permission.getCanQuery();
    boolean elevated = "owner".equals(role) || "admin".equals(role) || "editor".equals(role);
    boolean canNative = elevated || permission == null || permission.getCanNativeSql();
    if (!canQuery) {
      throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You cannot query this database");
    }
    if (nativeSql && !canNative) {
      throw new ApiException(HttpStatus.FORBIDDEN, "NATIVE_SQL_DENIED", "Native SQL is not enabled for your role");
    }
  }
}

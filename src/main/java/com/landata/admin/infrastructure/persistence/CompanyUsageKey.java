package com.landata.admin.infrastructure.persistence;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

public class CompanyUsageKey implements Serializable {
  private UUID companyId;
  private LocalDate period;

  public CompanyUsageKey() {}

  public CompanyUsageKey(UUID companyId, LocalDate period) {
    this.companyId = companyId;
    this.period = period;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof CompanyUsageKey key)) return false;
    return companyId.equals(key.companyId) && period.equals(key.period);
  }

  @Override
  public int hashCode() {
    return companyId.hashCode() * 31 + period.hashCode();
  }
}

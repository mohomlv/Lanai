package com.landata.query.infrastructure.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SqlServerGuardsTest {
  @Test
  void rewritesIntAggregatesOnce() {
    String sql = "SELECT COUNT(*) AS n, SUM(amount) AS total, DATEDIFF(second, a, b) AS age FROM orders";
    String widened = SqlServerGuards.widen(sql);
    assertTrue(widened.contains("COUNT_BIG(*)"));
    assertTrue(widened.contains("SUM(CAST(amount AS bigint))"));
    assertTrue(widened.contains("DATEDIFF_BIG(second, a, b)"));
    assertEquals(widened, SqlServerGuards.widen(widened));
    QueryValidator.assertSafeSelect(widened);
  }
}

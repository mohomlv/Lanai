package com.landata.query.infrastructure.sql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DialectTokensTest {
  @Test
  void lineTemplateBecomesRunnableSqlServerSelect() {
    String template = """
        SELECT
            <<DAY(o.created_at)>> AS "date",
            COUNT(*) AS p1,
            'All' AS p1_title
        FROM orders o
        WHERE 1 = 1
            [[AND o.term = {{term}}]]
            [[AND b.id = {{branch}}]]
        GROUP BY <<DAY(o.created_at)>>
        ORDER BY "date"
        """;
    String sql = NativeSql.forAskDraft(DialectTokens.expand(template, "sqlserver"));
    assertTrue(sql.contains("CAST(o.created_at AS DATE)"));
    assertTrue(sql.contains("[date]"));
    assertFalse(sql.contains("{{"));
    assertFalse(sql.contains("<<"));
    assertFalse(NativeSql.hasPlaceholders(sql));
    QueryValidator.assertSafeSelect(sql);
  }

  @Test
  void nestedAddDaysAndLimitBecomeTop() {
    String sql = DialectTokens.expand(
        "SELECT <<DAY(created_at)>> AS \"date\", 1 AS p1 FROM events WHERE created_at >= <<ADD_DAYS(<<TODAY>>, -30)>> <<LIMIT 10>>",
        "sqlserver"
    );
    assertTrue(sql.toUpperCase().contains("TOP 10"));
    assertTrue(sql.contains("DATEADD(day, -30, CAST(GETDATE() AS DATE))"));
    assertFalse(DialectTokens.leftoverTokens(sql));
  }
}

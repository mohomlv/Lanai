package com.landata.query.infrastructure.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NativeSqlTest {
  @Test
  void removesEmptyOptionalBlockAndBindsRange() {
    String sql = """
        SELECT SUM(total) AS revenue FROM orders
        WHERE created_at >= {{period.start}} AND created_at < {{period.end_exclusive}}
        [[ AND status IN ({{status}}) ]]
        """;
    List<Map<String, Object>> parameters = List.of(
        Map.of("name", "period", "type", "date_range", "required", true, "default", Map.of("start", "2026-01-01", "end", "2026-01-31")),
        Map.of("name", "status", "type", "select_multi", "required", false)
    );
    NativeSql.Rendered rendered = NativeSql.render(sql, parameters, Map.of(), ZoneId.of("UTC"), 100);
    assertFalse(rendered.sql().toLowerCase().contains("status"));
    assertTrue(rendered.sql().contains(">= ?"));
    assertEquals(2, rendered.binds().size());

    NativeSql.Rendered withStatus = NativeSql.render(
        sql,
        parameters,
        Map.of("status", "paid", "period", "2026-02-01~2026-02-28"),
        ZoneId.of("UTC"),
        100
    );
    assertTrue(withStatus.sql().toLowerCase().contains("status"));
    assertEquals(3, withStatus.binds().size());
  }

  @Test
  void bindsEntityMultiSelectAsInList() {
    String sql = "SELECT id FROM orders WHERE city_id IN ({{city}})";
    List<Map<String, Object>> parameters = List.of(Map.of(
        "name", "city",
        "type", "entity",
        "selection_mode", "multi",
        "default", List.of("10", "20")
    ));
    NativeSql.Rendered rendered = NativeSql.render(sql, parameters, Map.of(), ZoneId.of("UTC"), 100);
    assertTrue(rendered.sql().contains("IN (?, ?)"));
    assertEquals(List.of("10", "20"), rendered.binds());
  }

  @Test
  void removesEmptyMultiSelectBlock() {
    String sql = "SELECT id FROM orders WHERE 1 = 1 [[ AND city_id IN ({{city}}) ]]";
    List<Map<String, Object>> parameters = List.of(Map.of(
        "name", "city", "type", "entity", "selection_mode", "multi", "required", false
    ));
    NativeSql.Rendered rendered = NativeSql.render(sql, parameters, Map.of(), ZoneId.of("UTC"), 100);
    assertFalse(rendered.sql().toLowerCase().contains("city_id"));
    assertEquals(0, rendered.binds().size());
  }

  @Test
  void rejectsUnsafeSql() {
    QueryProblem problem = assertThrows(QueryProblem.class, () -> QueryValidator.assertSafeSelect("DROP TABLE orders"));
    assertEquals("NOT_SELECT", problem.code());
    assertThrows(QueryProblem.class, () -> QueryValidator.assertSafeSelect("SELECT pg_read_file('a')"));
    assertThrows(QueryProblem.class, () -> QueryValidator.assertSafeSelect("SELECT 1; SELECT 2"));
  }

  @Test
  void askDraftStripsOptionalFilters() {
    String sql = NativeSql.forAskDraft("SELECT 1 AS p1 WHERE 1 = 1 [[AND term = {{term}}]]");
    assertFalse(sql.contains("{{"));
    assertFalse(NativeSql.hasPlaceholders(sql));
  }

  @Test
  void bindsUserAttributesAndDropsEmptyOptionalBlock() {
    String sql = """
        SELECT id FROM orders
        WHERE clerk = @username
        [[ AND branch_id IN (@branches) ]]
        """;
    NativeSql.Rendered withBranches = NativeSql.render(
        sql,
        List.of(),
        Map.of(),
        ZoneId.of("UTC"),
        100,
        UserAttributes.values("ada", List.of(10, 20))
    );
    assertTrue(withBranches.sql().contains("clerk = ?"));
    assertTrue(withBranches.sql().toLowerCase().contains("branch_id in (?, ?)"));
    assertEquals(List.of("ada", 10, 20), withBranches.binds());

    NativeSql.Rendered withoutBranches = NativeSql.render(
        sql,
        List.of(),
        Map.of(),
        ZoneId.of("UTC"),
        100,
        UserAttributes.values("ada", List.of())
    );
    assertFalse(withoutBranches.sql().toLowerCase().contains("branch_id"));
    assertEquals(List.of("ada"), withoutBranches.binds());
  }

  @Test
  void wrapsBareInListForBranches() {
    String sql = "SELECT * FROM Tbl_Branch WHERE 1 = 1 [[AND id IN @branches]]";
    NativeSql.Rendered rendered = NativeSql.render(
        sql,
        List.of(),
        Map.of(),
        ZoneId.of("UTC"),
        100,
        UserAttributes.values("ada", List.of(3, 7))
    );
    assertTrue(rendered.sql().toLowerCase().contains("id in (?, ?)"));
    assertEquals(List.of(3, 7), rendered.binds());
    QueryValidator.assertSafeSelect(rendered.sql());
  }

  @Test
  void dropsWholeOptionalBlockWhenUsernameMissing() {
    String sql = "SELECT id FROM orders WHERE 1 = 1 [[ AND clerk = @username ]]";
    NativeSql.Rendered rendered = NativeSql.render(
        sql,
        List.of(),
        Map.of(),
        ZoneId.of("UTC"),
        100,
        UserAttributes.values(null, List.of())
    );
    assertFalse(rendered.sql().toLowerCase().contains("clerk"));
    assertEquals(0, rendered.binds().size());
  }
}

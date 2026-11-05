package com.landata.query.infrastructure.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SqlCompilerTest {
  @Test
  void compilesPostgresDateRangeAndMultiSelect() {
    Map<String, Object> spec = Map.of(
        "version", 1,
        "source", Map.of("table", "public.orders", "alias", "o"),
        "joins", List.of(Map.of(
            "type", "left",
            "table", "public.customers",
            "alias", "c",
            "on", List.of(Map.of("left", "o.customer_id", "op", "=", "right", "c.id"))
        )),
        "select", List.of(
            Map.of("alias", "month", "expr", Map.of("date_bucket", Map.of("column", "o.created_at", "grain", "month"))),
            Map.of("alias", "region", "expr", Map.of("column", "c.region")),
            Map.of("alias", "revenue", "expr", Map.of("agg", "sum", "column", "o.total"))
        ),
        "filters", Map.of("and", List.of(
            Map.of("column", "o.created_at", "op", "between", "value", Map.of("param", "period")),
            Map.of("column", "o.status", "op", "in", "value", Map.of("param", "status"))
        )),
        "group_by", List.of("month", "region"),
        "order_by", List.of(Map.of("ref", "month", "dir", "asc")),
        "limit", 1000
    );
    List<Map<String, Object>> parameters = List.of(
        Map.of("name", "period", "type", "date_range", "default", Map.of("start", "2026-01-01", "end", "2026-12-31")),
        Map.of("name", "status", "type", "select_multi", "default", List.of("paid", "open"))
    );
    SqlCompiler.Compiled compiled = SqlCompiler.compile(spec, parameters, Map.of(), "postgres", ZoneId.of("UTC"), 10000);
    assertTrue(compiled.sql().contains("date_trunc('month', \"o\".\"created_at\")"));
    assertTrue(compiled.sql().contains("LEFT JOIN \"public\".\"customers\""));
    assertTrue(compiled.sql().contains("GROUP BY date_trunc('month', \"o\".\"created_at\")"), compiled.sql());
    assertTrue(compiled.sql().endsWith("LIMIT 1000"));
    assertEquals(4, compiled.binds().size());
  }

  @Test
  void usesTopOnSqlServer() {
    Map<String, Object> spec = Map.of(
        "source", Map.of("table", "dbo.orders", "alias", "o"),
        "select", List.of(Map.of("alias", "n", "expr", Map.of("agg", "count"))),
        "limit", 10
    );
    SqlCompiler.Compiled compiled = SqlCompiler.compile(spec, List.of(), Map.of(), "sqlserver", ZoneId.of("UTC"), 100);
    assertTrue(compiled.sql().startsWith("SELECT TOP (10)"));
    assertTrue(compiled.sql().contains("[dbo].[orders]"));
  }

  @Test
  void rejectsMissingGroupBy() {
    Map<String, Object> spec = Map.of(
        "source", Map.of("table", "public.orders", "alias", "o"),
        "select", List.of(
            Map.of("alias", "region", "expr", Map.of("column", "o.region")),
            Map.of("alias", "n", "expr", Map.of("agg", "count"))
        )
    );
    QueryProblem problem = assertThrows(QueryProblem.class, () ->
        SqlCompiler.compile(spec, List.of(), Map.of(), "postgres", ZoneId.of("UTC"), 100));
    assertEquals("MISSING_GROUP_BY", problem.code());
  }
}

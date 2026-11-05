package com.landata.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PromptDocsTest {
  private final PromptDocs docs = new PromptDocs();

  @BeforeEach
  void load() throws Exception {
    docs.load();
  }

  @Test
  void sqlServerSliceHasTokensAndSafetyWithoutOtherEngines() {
    String slice = docs.dialectSlice("sqlserver");
    assertTrue(slice.contains("DATEADD(day, n, x)"));
    assertTrue(slice.contains("COUNT_BIG"));
    assertTrue(slice.contains("N prefix"));
    assertFalse(slice.contains("SAFE_DIVIDE"));
    assertFalse(slice.contains("DATE_TRUNC('day'"));
  }

  @Test
  void postgresSliceUsesDateTrunc() {
    String slice = docs.dialectSlice("postgres");
    assertTrue(slice.contains("DATE_TRUNC('day', x)::date"));
    assertFalse(slice.contains("COUNT_BIG"));
  }

  @Test
  void resolvePrefersUserChartOverKeywords() {
    assertEquals("pie", docs.resolveChart("pie", "monthly revenue trend"));
    assertEquals("line", docs.resolveChart("table", "monthly revenue trend over time"));
    assertEquals("table", docs.resolveChart("table", "show the rows"));
  }

  @Test
  void systemPromptTellsAskNotToEmitFilters() {
    String prompt = docs.systemPrompt("sqlserver");
    assertTrue(prompt.contains("Ask has no dashboard filters"));
    assertTrue(prompt.contains("[date]"));
    assertTrue(prompt.contains("query_guide"));
  }

  @Test
  void chartMarkdownKeepsContractColumns() {
    String line = docs.chartMarkdown("line");
    assertTrue(line.contains("`date`"));
    assertTrue(line.contains("p1_title"));
    String pie = docs.chartMarkdown("donut");
    assertTrue(pie.contains("`amount`"));
  }
}

package com.landata.knowledge.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QueryGuideDocsTest {
  @Test
  void titleFallsBackToHeading() {
    assertEquals("Monthly occupancy", KnowledgeService.guideTitle(null, "# Monthly occupancy\n\nA line chart."));
    assertEquals("Given title", KnowledgeService.guideTitle("Given title", "# Other"));
    assertEquals("Query guide", KnowledgeService.guideTitle("  ", "no heading"));
  }

  @Test
  void queryGuideChunksUseGuideKind() {
    var chunks = DocIndexer.chunks("# Occupancy\n\n## Example SQL\nSELECT 1\n", "query_guide");
    assertEquals(2, chunks.size());
    assertTrue(chunks.stream().allMatch(chunk -> "query_guide".equals(chunk.kind())));
  }
}

package com.landata.knowledge.application;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextEmbeddingTest {
  @Test
  void similarTableDocsRankAboveUnrelatedText() {
    float[] question = TextEmbedding.embed("monthly revenue from paid orders");
    float[] orders = TextEmbedding.embed("# orders.md\nTable: dbo.orders\nFields: total status customer_id\nRelated: customers");
    float[] weather = TextEmbedding.embed("# weather.md\nTable: dbo.weather\nFields: temp humidity");
    assertTrue(TextEmbedding.cosine(question, orders) > TextEmbedding.cosine(question, weather));
  }
}

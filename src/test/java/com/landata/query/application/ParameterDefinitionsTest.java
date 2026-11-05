package com.landata.query.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.landata.query.infrastructure.sql.QueryProblem;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParameterDefinitionsTest {
  @Test
  void normalizesEntitySourceAndMultiDefault() {
    UUID questionId = UUID.randomUUID();
    List<Map<String, Object>> rows = ParameterDefinitions.normalize(List.of(Map.of(
        "name", "city",
        "type", "entity",
        "label", "City",
        "selection_mode", "multi",
        "default", "12",
        "options_source", Map.of(
            "kind", "query",
            "question_id", questionId.toString(),
            "display_field", "city_name",
            "value_field", "city_id"
        )
    )));
    assertEquals("multi", rows.get(0).get("selection_mode"));
    assertEquals(List.of("12"), rows.get(0).get("default"));
    @SuppressWarnings("unchecked")
    Map<String, Object> source = (Map<String, Object>) rows.get(0).get("options_source");
    assertEquals("city_id", source.get("source_field"));
    assertEquals("city_name", source.get("display_field"));
  }

  @Test
  void rejectsEntityWithoutSourceQuestion() {
    QueryProblem problem = assertThrows(QueryProblem.class, () -> ParameterDefinitions.normalize(List.of(
        Map.of("name", "city", "type", "entity")
    )));
    assertEquals("BAD_PARAM_TYPE", problem.code());
  }

  @Test
  void rejectsValueFieldOutsideConfiguredFields() {
    QueryProblem problem = assertThrows(QueryProblem.class, () -> ParameterDefinitions.fieldsConfig(Map.of(
        "fields", List.of("city_name"),
        "value_field", "city_id",
        "display_field", "city_name"
    )));
    assertEquals("BAD_PARAM_TYPE", problem.code());
  }
}

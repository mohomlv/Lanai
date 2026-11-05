package com.landata.viz.api;

import com.landata.shared.security.RequiresRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Charts")
@RequestMapping("/api/v1")
public class VizController {
  private static final List<String> KEYS = List.of(
      "table", "scalar", "line", "area", "bar", "stacked_bar", "row", "pie", "scatter", "combo", "pivot", "gauge", "funnel"
  );

  @GetMapping("/chart-templates")
  @RequiresRole("viewer")
  public Map<String, Object> list() {
    return Map.of("items", KEYS.stream().map(key -> Map.of("key", key, "name", key)).toList());
  }

  @GetMapping("/chart-templates/{key}")
  @RequiresRole("viewer")
  public Map<String, Object> one(@PathVariable String key) {
    return Map.of("key", key, "viz_schema", Map.of("type", "object"), "data_requirements", Map.of("measures", 1));
  }

  @PostMapping("/viz/auto")
  @RequiresRole("viewer")
  public Map<String, Object> auto(@RequestBody Map<String, Object> body) {
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> columns = (List<Map<String, Object>>) body.getOrDefault("columns", List.of());
    boolean date = columns.stream().anyMatch(column -> "date".equals(column.get("type")) || "datetime".equals(column.get("type")));
    long numbers = columns.stream().filter(column -> "integer".equals(column.get("type")) || "decimal".equals(column.get("type"))).count();
    String template = columns.size() == 1 && numbers == 1 ? "scalar" : date && numbers >= 1 ? "line" : numbers >= 1 ? "bar" : "table";
    return Map.of("template", template, "config", Map.of());
  }

  @PostMapping("/viz/validate")
  @RequiresRole("editor")
  public Map<String, Object> validate(@RequestBody Map<String, Object> body) {
    String template = String.valueOf(body.get("template"));
    return Map.of("valid", KEYS.contains(template), "issues", KEYS.contains(template) ? List.of() : List.of("Unknown template"));
  }

  @PostMapping("/viz/pivot")
  @RequiresRole("viewer")
  public Map<String, Object> pivot(@RequestBody Map<String, Object> body) {
    return Map.of("columns", body.getOrDefault("columns", List.of()), "rows", body.getOrDefault("rows", List.of()));
  }
}

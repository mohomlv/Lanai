package com.landata.knowledge.api;

import com.landata.knowledge.application.KnowledgeService;
import com.landata.shared.api.PageResponse;
import com.landata.shared.security.AccessGuard;
import com.landata.shared.security.RequiresRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Knowledge")
@RequestMapping("/api/v1")
public class KnowledgeController {
  private final KnowledgeService knowledge;
  private final AccessGuard access;

  public KnowledgeController(KnowledgeService knowledge, AccessGuard access) {
    this.knowledge = knowledge;
    this.access = access;
  }

  @PostMapping("/datasources/{id}/scrape")
  @RequiresRole("admin")
  public ResponseEntity<Map<String, Object>> scrape(
      @PathVariable UUID id,
      @RequestBody(required = false) Map<String, Object> body
  ) {
    boolean useAi = body != null && Boolean.TRUE.equals(body.get("use_ai"));
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(knowledge.scrape(access.current(), id, useAi));
  }

  @GetMapping("/scrape-jobs/{id}")
  @RequiresRole("admin")
  public Map<String, Object> job(@PathVariable UUID id) {
    return knowledge.job(access.current().companyId(), id);
  }

  @GetMapping("/scrape-jobs/{id}/items")
  @RequiresRole("admin")
  public PageResponse<Map<String, Object>> items(@PathVariable UUID id) {
    var rows = knowledge.jobItems(access.current().companyId(), id);
    return new PageResponse<>(rows, 0, rows.size());
  }

  @GetMapping("/datasources/{id}/docs")
  @RequiresRole("viewer")
  public PageResponse<Map<String, Object>> docs(@PathVariable UUID id) {
    var rows = knowledge.docs(access.current().companyId(), id);
    return new PageResponse<>(rows, 0, rows.size());
  }

  @PostMapping("/datasources/{id}/docs")
  @RequiresRole("editor")
  public ResponseEntity<Map<String, Object>> addDoc(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    return ResponseEntity.status(HttpStatus.CREATED).body(knowledge.addQueryGuide(access.current(), id, body));
  }

  @GetMapping("/docs/{id}")
  @RequiresRole("viewer")
  public Map<String, Object> doc(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean versions) {
    return knowledge.doc(access.current().companyId(), id, versions);
  }

  @PutMapping("/docs/{id}")
  @RequiresRole("editor")
  public Map<String, Object> edit(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    String title = body.get("title") == null ? null : String.valueOf(body.get("title"));
    return knowledge.editDoc(access.current(), id, String.valueOf(body.get("markdown")), title);
  }

  @DeleteMapping("/docs/{id}")
  @RequiresRole("editor")
  public Map<String, Object> deleteDoc(@PathVariable UUID id) {
    return knowledge.deleteDoc(access.current(), id);
  }

  @PostMapping("/docs/{id}/approve")
  @RequiresRole("admin")
  public Map<String, Object> approve(@PathVariable UUID id) {
    return knowledge.doc(access.current().companyId(), id, false);
  }

  @PostMapping("/docs/{id}/reject")
  @RequiresRole("admin")
  public Map<String, Object> reject(@PathVariable UUID id) {
    return knowledge.doc(access.current().companyId(), id, false);
  }

  @GetMapping("/datasources/{id}/glossary")
  @RequiresRole("admin")
  public PageResponse<?> glossary(@PathVariable UUID id) {
    var rows = knowledge.glossary(access.current().companyId(), id);
    return new PageResponse<>(rows, 0, rows.size());
  }

  @PostMapping("/datasources/{id}/glossary")
  @RequiresRole("admin")
  public Object addTerm(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    return knowledge.addTerm(access.current(), id, body);
  }

  @DeleteMapping("/datasources/{datasourceId}/glossary/{id}")
  @RequiresRole("admin")
  public Map<String, Object> deleteTerm(@PathVariable UUID id) {
    knowledge.deleteTerm(access.current().companyId(), id);
    return Map.of("status", "deleted");
  }

  @PatchMapping("/datasources/{datasourceId}/glossary/{id}")
  @RequiresRole("admin")
  public Map<String, Object> patchTerm() {
    return Map.of("status", "ok");
  }

  @GetMapping("/datasources/{id}/metrics")
  @RequiresRole("admin")
  public PageResponse<?> metrics(@PathVariable UUID id) {
    var rows = knowledge.metricList(access.current().companyId(), id);
    return new PageResponse<>(rows, 0, rows.size());
  }

  @PostMapping("/datasources/{id}/metrics")
  @RequiresRole("admin")
  public Object addMetric(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    return knowledge.addMetric(access.current(), id, body);
  }

  @DeleteMapping("/datasources/{datasourceId}/metrics/{id}")
  @RequiresRole("admin")
  public Map<String, Object> deleteMetric(@PathVariable UUID id) {
    knowledge.deleteMetric(access.current().companyId(), id);
    return Map.of("status", "deleted");
  }

  @PatchMapping("/datasources/{datasourceId}/metrics/{id}")
  @RequiresRole("admin")
  public Map<String, Object> patchMetric() {
    return Map.of("status", "ok");
  }
}

package com.landata.query.api;

import com.landata.query.application.ParameterAccessService;
import com.landata.query.application.QueryService;
import com.landata.query.application.QuestionService;
import com.landata.shared.api.PageResponse;
import com.landata.shared.security.AccessGuard;
import com.landata.shared.security.RequiresRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
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
@Tag(name = "Queries")
@RequestMapping("/api/v1")
public class QueryController {
  private final QueryService queries;
  private final QuestionService questions;
  private final ParameterAccessService parameterAccess;
  private final AccessGuard access;

  public QueryController(QueryService queries, QuestionService questions, ParameterAccessService parameterAccess, AccessGuard access) {
    this.queries = queries;
    this.questions = questions;
    this.parameterAccess = parameterAccess;
    this.access = access;
  }

  @PostMapping("/queries/run")
  @RequiresRole("editor")
  public Map<String, Object> run(@RequestBody Map<String, Object> body) {
    return queries.run(access.current(), body, "editor");
  }

  @PostMapping("/queries/compile")
  @RequiresRole("editor")
  public Map<String, Object> compile(@RequestBody Map<String, Object> body) {
    return queries.compile(access.current(), body);
  }

  @PostMapping("/queries/validate")
  @RequiresRole("editor")
  public Map<String, Object> validate(@RequestBody Map<String, Object> body) {
    return queries.validate(access.current(), body);
  }

  @PostMapping("/queries/{runId}/cancel")
  @RequiresRole("editor")
  public Map<String, Object> cancel(@PathVariable UUID runId) {
    return queries.cancel(access.current(), runId);
  }

  @GetMapping("/questions")
  @RequiresRole("viewer")
  public PageResponse<Map<String, Object>> questions() {
    var rows = questions.list(access.current().companyId());
    return new PageResponse<>(rows, 0, rows.size());
  }

  @PostMapping("/questions")
  @RequiresRole("editor")
  public Map<String, Object> createQuestion(@RequestBody Map<String, Object> body) {
    return questions.create(access.current(), body);
  }

  @GetMapping("/questions/{id}")
  @RequiresRole("viewer")
  public Map<String, Object> question(@PathVariable UUID id) {
    return questions.get(access.current().companyId(), id);
  }

  @PatchMapping("/questions/{id}")
  @RequiresRole("editor")
  public Map<String, Object> updateQuestion(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    return questions.update(access.current().companyId(), id, body);
  }

  @DeleteMapping("/questions/{id}")
  @RequiresRole("editor")
  public Map<String, Object> archiveQuestion(@PathVariable UUID id) {
    questions.archive(access.current().companyId(), id);
    return Map.of("status", "archived");
  }

  @PostMapping("/questions/{id}/versions")
  @RequiresRole("editor")
  public Map<String, Object> addVersion(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    return questions.addVersion(access.current(), id, body);
  }

  @GetMapping("/questions/{id}/versions")
  @RequiresRole("viewer")
  public PageResponse<Map<String, Object>> versions(@PathVariable UUID id) {
    var rows = questions.history(access.current().companyId(), id);
    return new PageResponse<>(rows, 0, rows.size());
  }

  @GetMapping("/questions/{id}/versions/{number}")
  @RequiresRole("viewer")
  public Map<String, Object> version(@PathVariable UUID id, @PathVariable int number) {
    return questions.version(access.current().companyId(), id, number);
  }

  @PostMapping("/questions/{id}/run")
  @RequiresRole("viewer")
  public Map<String, Object> runQuestion(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
    return questions.run(access.current(), id, body == null ? Map.of() : body);
  }

  @GetMapping("/parameter-questions")
  @RequiresRole("viewer")
  public PageResponse<Map<String, Object>> parameterQuestions() {
    var rows = parameterAccess.listAllowed(access.current());
    return new PageResponse<>(rows, 0, rows.size());
  }

  @GetMapping("/parameter-questions/{id}/options")
  @RequiresRole("viewer")
  public Map<String, Object> parameterOptions(
      @PathVariable UUID id,
      @RequestParam(name = "value_field", required = false) String valueField,
      @RequestParam(name = "display_field", required = false) String displayField
  ) {
    return questions.options(access.current(), id, valueField, displayField);
  }

  @GetMapping("/parameter-access")
  @RequiresRole("admin")
  public Map<String, Object> parameterAccess() {
    return parameterAccess.read(access.current());
  }

  @PutMapping("/parameter-access")
  @RequiresRole("admin")
  public Map<String, Object> replaceParameterAccess(@RequestBody Map<String, Object> body) {
    return parameterAccess.replace(access.current(), body);
  }

  @GetMapping("/collections")
  @RequiresRole("viewer")
  public PageResponse<Map<String, Object>> collections() {
    var rows = questions.collections(access.current().companyId());
    return new PageResponse<>(rows, 0, rows.size());
  }

  @PostMapping("/collections")
  @RequiresRole("editor")
  public Map<String, Object> createCollection(@RequestBody Map<String, Object> body) {
    return questions.createCollection(access.current(), body);
  }

  @PatchMapping("/collections/{id}")
  @RequiresRole("editor")
  public Map<String, Object> updateCollection(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    return questions.updateCollection(access.current().companyId(), id, body);
  }

  @DeleteMapping("/collections/{id}")
  @RequiresRole("editor")
  public Map<String, Object> archiveCollection(@PathVariable UUID id) {
    questions.archiveCollection(access.current().companyId(), id);
    return Map.of("status", "archived");
  }
}

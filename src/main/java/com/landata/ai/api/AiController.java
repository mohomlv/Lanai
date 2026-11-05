package com.landata.ai.api;

import com.landata.ai.application.AiService;

import com.landata.shared.security.AccessGuard;
import com.landata.shared.security.RequiresRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Tag(name = "AI")
@RequestMapping("/api/v1/ai")
public class AiController {
  private final AiService ai;
  private final AccessGuard access;
  private final ScheduledExecutorService ssePool = Executors.newScheduledThreadPool(2);

  public AiController(AiService ai, AccessGuard access) {
    this.ai = ai;
    this.access = access;
  }

  @GetMapping("/conversations")
  @RequiresRole("editor")
  public Map<String, Object> conversations() {
    return Map.of("items", ai.conversations(access.current()));
  }

  @PostMapping("/conversations")
  @RequiresRole("editor")
  public Map<String, Object> open(@RequestBody Map<String, Object> body) {
    UUID datasourceId = body.get("datasource_id") == null ? null : UUID.fromString(String.valueOf(body.get("datasource_id")));
    return ai.open(access.current(), datasourceId, body.get("target_type") == null ? "question" : String.valueOf(body.get("target_type")));
  }

  @GetMapping("/conversations/{id}")
  @RequiresRole("editor")
  public Map<String, Object> conversation(@PathVariable UUID id) {
    return ai.conversation(access.current().companyId(), id);
  }

  @PostMapping("/conversations/{id}/messages")
  @RequiresRole("editor")
  public ResponseEntity<Map<String, Object>> message(
      @PathVariable UUID id,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
  ) {
    UUID providerConfigId = null;
    if (body.get("provider_config_id") != null && !String.valueOf(body.get("provider_config_id")).isBlank()) {
      providerConfigId = UUID.fromString(String.valueOf(body.get("provider_config_id")));
    }
    String chart = body.get("viz_template_key") == null ? null : String.valueOf(body.get("viz_template_key"));
    Map<String, Object> request = ai.message(
        access.current(), id, String.valueOf(body.get("content")), idempotencyKey, providerConfigId, chart);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(request);
  }

  @GetMapping("/requests/{id}")
  @RequiresRole("editor")
  public Map<String, Object> request(@PathVariable UUID id) {
    return ai.request(access.current().companyId(), id);
  }

  @GetMapping("/requests/{id}/events")
  @RequiresRole("editor")
  public SseEmitter events(@PathVariable UUID id) {
    UUID companyId = access.current().companyId();
    SseEmitter emitter = new SseEmitter(120_000L);
    AtomicInteger ticks = new AtomicInteger();
    var future = ssePool.scheduleAtFixedRate(() -> {
      try {
        Map<String, Object> view = ai.request(companyId, id);
        String status = String.valueOf(view.get("status"));
        emitter.send(SseEmitter.event().name("status").data(view));
        if (view.get("result") != null && !"queued".equals(status) && !"generating".equals(status)) {
          emitter.send(SseEmitter.event().name("draft").data(view));
          emitter.send(SseEmitter.event().name("done").data(view));
          emitter.complete();
        } else if ("failed".equals(status) || "discarded".equals(status)) {
          emitter.send(SseEmitter.event().name("error").data(view));
          emitter.complete();
        } else if (ticks.incrementAndGet() > 110) {
          emitter.send(SseEmitter.event().name("error").data(Map.of("status", "timeout")));
          emitter.complete();
        }
      } catch (IOException ex) {
        emitter.complete();
      } catch (RuntimeException ex) {
        emitter.completeWithError(ex);
      }
    }, 0, 1, TimeUnit.SECONDS);
    emitter.onCompletion(() -> future.cancel(false));
    emitter.onTimeout(() -> {
      future.cancel(false);
      emitter.complete();
    });
    return emitter;
  }

  @PostMapping("/requests/{id}/accept")
  @RequiresRole("editor")
  public Map<String, Object> accept(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
    String name = body == null || body.get("name") == null ? "AI question" : String.valueOf(body.get("name"));
    String chart = body == null || body.get("viz_template_key") == null ? null : String.valueOf(body.get("viz_template_key"));
    String sql = body == null || body.get("native_sql") == null ? null : String.valueOf(body.get("native_sql"));
    return ai.accept(access.current(), id, name, chart, sql);
  }

  @PostMapping("/requests/{id}/discard")
  @RequiresRole("editor")
  public Map<String, Object> discard(@PathVariable UUID id) {
    return ai.discard(access.current().companyId(), id);
  }

  @PostMapping("/requests/{id}/feedback")
  @RequiresRole("editor")
  public Map<String, Object> feedback(@PathVariable UUID id) {
    ai.request(access.current().companyId(), id);
    return Map.of("status", "ok");
  }

  @GetMapping("/usage")
  @RequiresRole("admin")
  public Map<String, Object> usage() {
    return Map.of("ai_requests", 0, "ai_tokens", 0);
  }

  @GetMapping("/agents")
  @RequiresRole("editor")
  public Map<String, Object> agents() {
    return Map.of("items", ai.savedAgents(access.current().companyId()));
  }

  @GetMapping("/providers")
  @RequiresRole("admin")
  public Map<String, Object> providers() {
    return Map.of("items", ai.providerCatalog());
  }

  @PostMapping("/providers/models")
  @RequiresRole("admin")
  public Map<String, Object> discoverModels(@RequestBody Map<String, Object> body) {
    return ai.discoverModels(access.current(), body);
  }

  @GetMapping("/provider-config")
  @RequiresRole("admin")
  public Map<String, Object> provider() {
    return ai.provider(access.current().companyId());
  }

  @PutMapping("/provider-config")
  @RequiresRole("admin")
  public Map<String, Object> saveProvider(@RequestBody Map<String, Object> body) {
    return ai.saveProvider(access.current(), body);
  }

  @PostMapping("/provider-config/test")
  @RequiresRole("admin")
  public Map<String, Object> testProvider(@RequestBody Map<String, Object> body) {
    return ai.testProvider(access.current(), body);
  }

  @GetMapping("/endpoints")
  @RequiresRole("admin")
  public Map<String, Object> endpoints() {
    return Map.of("items", ai.endpoints(access.current().companyId()));
  }

  @PostMapping("/endpoints")
  @RequiresRole("admin")
  public Map<String, Object> createEndpoint(@RequestBody Map<String, Object> body) {
    return ai.createEndpoint(access.current(), body);
  }

  @PostMapping("/endpoints/{id}/use")
  @RequiresRole("admin")
  public Map<String, Object> useEndpoint(@PathVariable UUID id) {
    return ai.useEndpoint(access.current(), id);
  }

  @DeleteMapping("/endpoints/{id}")
  @RequiresRole("admin")
  public Map<String, Object> deleteEndpoint(@PathVariable UUID id) {
    return ai.deleteEndpoint(access.current(), id);
  }

  @GetMapping("/models")
  @RequiresRole("admin")
  public Map<String, Object> models() {
    return Map.of("items", ai.providerCatalog());
  }
}

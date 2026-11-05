package com.landata.shared.api;

import com.landata.shared.log.LogScrubber;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

@RestControllerAdvice
public class ProblemHandler {
  private static final Logger log = LoggerFactory.getLogger(ProblemHandler.class);
  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ProblemDetail> api(ApiException ex, HttpServletRequest request) {
    return problem(ex.status(), ex.code(), ex.getMessage(), request);
  }

  @ExceptionHandler(AsyncRequestNotUsableException.class)
  public void clientGone() {
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> unexpected(Exception ex, HttpServletRequest request) {
    log.error("Unhandled request failure", ex);
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "Something went wrong", request);
  }

  private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(status, LogScrubber.scrub(detail));
    body.setTitle(status.getReasonPhrase());
    body.setType(URI.create("about:blank"));
    body.setProperty("code", code);
    body.setProperty("traceId", traceId(request));
    return ResponseEntity.status(status).body(body);
  }

  private String traceId(HttpServletRequest request) {
    String header = request.getHeader("X-Request-Id");
    return header == null || header.isBlank() ? UUID.randomUUID().toString() : header;
  }
}

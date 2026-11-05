package com.landata.query.api;

import com.landata.query.infrastructure.sql.QueryProblem;
import com.landata.shared.log.LogScrubber;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class QueryProblemHandler {
  @ExceptionHandler(QueryProblem.class)
  public ResponseEntity<ProblemDetail> query(QueryProblem ex, HttpServletRequest request) {
    HttpStatus status = "NEEDS_INPUT".equals(ex.code()) ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.BAD_REQUEST;
    ProblemDetail body = ProblemDetail.forStatusAndDetail(status, LogScrubber.scrub(ex.getMessage()));
    body.setTitle(status.getReasonPhrase());
    body.setType(URI.create("about:blank"));
    body.setProperty("code", ex.code());
    String header = request.getHeader("X-Request-Id");
    body.setProperty("traceId", header == null || header.isBlank() ? UUID.randomUUID().toString() : header);
    return ResponseEntity.status(status).body(body);
  }
}

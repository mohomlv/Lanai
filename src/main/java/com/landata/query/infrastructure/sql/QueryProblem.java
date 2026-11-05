package com.landata.query.infrastructure.sql;

public class QueryProblem extends RuntimeException {
  private final String code;

  public QueryProblem(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}

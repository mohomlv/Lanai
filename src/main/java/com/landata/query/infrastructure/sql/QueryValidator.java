package com.landata.query.infrastructure.sql;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.parser.feature.Feature;
import net.sf.jsqlparser.parser.feature.FeatureConfiguration;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;

public final class QueryValidator {
  private static final Pattern FUNCTION = Pattern.compile("\\b([a-z_][a-z0-9_]*)\\s*\\(", Pattern.CASE_INSENSITIVE);
  private static final Set<String> DENIED = Set.of(
      "pg_read_file", "pg_read_binary_file", "pg_ls_dir", "lo_import", "lo_export", "pg_sleep",
      "dblink", "dblink_exec", "dblink_connect",
      "xp_cmdshell", "xp_regread", "xp_fileexist",
      "openrowset", "openquery", "opendatasource",
      "load_file", "sleep", "benchmark",
      "utl_file", "utl_http", "utl_tcp", "utl_smtp", "dbms_scheduler", "dbms_java", "dbms_lob"
  );
  private static final Set<String> DENIED_PREFIX = Set.of("xp_", "sp_", "utl_", "dbms_");

  private QueryValidator() {}

  public static void assertSafeSelect(String sql) {
    Statements statements;
    try {
      statements = CCJSqlParserUtil.parseStatements(sql, parser -> parser.withConfiguration(
          new FeatureConfiguration().setValue(Feature.allowSquareBracketQuotation, true)));
    } catch (Exception ex) {
      throw new QueryProblem("PARSE_ERROR", "SQL could not be parsed");
    }
    if (statements == null || statements.size() != 1) {
      throw new QueryProblem("MULTI_STATEMENT", "Exactly one statement is allowed");
    }
    Statement statement = statements.get(0);
    if (!(statement instanceof Select)) {
      throw new QueryProblem("NOT_SELECT", "Only SELECT statements are allowed");
    }
    String normalized = statement.toString().toLowerCase(Locale.ROOT);
    if (normalized.contains(" into ") || normalized.contains(" for update")) {
      throw new QueryProblem("NOT_SELECT", "INTO and FOR UPDATE are not allowed");
    }
    Matcher matcher = FUNCTION.matcher(normalized);
    while (matcher.find()) {
      String name = matcher.group(1);
      if (DENIED.contains(name) || deniedPrefix(name)) {
        throw new QueryProblem("FORBIDDEN_FUNCTION", "Function is not allowed: " + name);
      }
    }
  }

  private static boolean deniedPrefix(String name) {
    if (name.startsWith("dblink")) return true;
    for (String prefix : DENIED_PREFIX) {
      if (name.startsWith(prefix)) return true;
    }
    return false;
  }
}

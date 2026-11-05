package com.landata.query.infrastructure.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles chart-doc {@code <<TOKEN>>} placeholders into real SQL for a dialect.
 * Ask drafts must not leave tokens or unused dashboard filters in the statement.
 */
public final class DialectTokens {
  private static final Pattern LIMIT = Pattern.compile("<<\\s*LIMIT\\s+(\\d+)\\s*>>", Pattern.CASE_INSENSITIVE);
  private static final Pattern TOKEN = Pattern.compile(
      "<<\\s*([A-Za-z_]+)(?:\\s+([^<>]+?)|\\(([^<>]*?)\\))?\\s*>>");
  private static final Pattern DATE_ALIAS = Pattern.compile(
      "(?i)\\b(AS|BY)\\s+(\\[date\\]|\"date\"|`date`)");

  private DialectTokens() {}

  public static String expand(String sql, String dialect) {
    if (sql == null || sql.isBlank()) return sql;
    String type = normalize(dialect);
    Integer limit = null;
    Matcher limitMatcher = LIMIT.matcher(sql);
    if (limitMatcher.find()) {
      limit = Integer.parseInt(limitMatcher.group(1));
      sql = limitMatcher.reset().replaceAll("");
    }
    String current = sql;
    for (int pass = 0; pass < 8; pass++) {
      Matcher matcher = TOKEN.matcher(current);
      if (!matcher.find()) break;
      StringBuilder out = new StringBuilder();
      matcher.reset();
      while (matcher.find()) {
        String name = matcher.group(1).toUpperCase(Locale.ROOT);
        String rawArgs = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
        matcher.appendReplacement(out, Matcher.quoteReplacement(rewrite(type, name, rawArgs == null ? "" : rawArgs.trim())));
      }
      matcher.appendTail(out);
      current = out.toString();
    }
    return quoteDateColumn(type, applyLimit(type, current, limit));
  }

  public static boolean leftoverTokens(String sql) {
    return sql != null && sql.contains("<<") && sql.contains(">>");
  }

  public static String quoteIdent(String dialect, String ident) {
    String name = ident == null ? "" : ident;
    return switch (normalize(dialect)) {
      case "sqlserver" -> "[" + name.replace("]", "]]") + "]";
      case "mysql", "mariadb" -> "`" + name.replace("`", "``") + "`";
      default -> "\"" + name.replace("\"", "\"\"") + "\"";
    };
  }

  static String normalize(String dialect) {
    if (dialect == null) return "postgresql";
    String type = dialect.toLowerCase(Locale.ROOT);
    return switch (type) {
      case "postgres" -> "postgresql";
      case "mssql" -> "sqlserver";
      default -> type;
    };
  }

  private static String rewrite(String type, String name, String rawArgs) {
    List<String> args = splitArgs(rawArgs);
    String x = args.isEmpty() ? "" : args.get(0);
    String y = args.size() > 1 ? args.get(1) : "";
    return switch (name) {
      case "DAY" -> day(type, x);
      case "WEEK" -> week(type, x);
      case "MONTH" -> month(type, x);
      case "YEAR" -> year(type, x);
      case "HOUR" -> hour(type, x);
      case "DOW" -> dow(type, x);
      case "TODAY" -> today(type);
      case "DAYS_BETWEEN" -> daysBetween(type, x, y);
      case "MONTHS_BETWEEN" -> monthsBetween(type, x, y);
      case "ADD_DAYS" -> addDays(type, x, y);
      case "CONCAT" -> concat(type, x, y);
      case "STR" -> str(type, x);
      case "PCT" -> "ROUND(100.0 * " + x + " / NULLIF(" + y + ", 0), 1)";
      case "MEDIAN" -> median(type, x);
      default -> x.isBlank() ? name : x;
    };
  }

  private static String day(String type, String x) {
    return switch (type) {
      case "sqlserver" -> "CAST(" + x + " AS DATE)";
      case "postgresql" -> "DATE_TRUNC('day', " + x + ")::date";
      case "oracle" -> "TRUNC(" + x + ")";
      default -> "DATE(" + x + ")";
    };
  }

  private static String week(String type, String x) {
    return switch (type) {
      case "sqlserver" -> "DATEADD(week, DATEDIFF(week, 0, " + x + "), 0)";
      case "postgresql" -> "DATE_TRUNC('week', " + x + ")::date";
      case "oracle" -> "TRUNC(" + x + ", 'IW')";
      case "sqlite" -> "DATE(" + x + ", 'weekday 0', '-6 days')";
      case "bigquery" -> "DATE_TRUNC(" + x + ", WEEK)";
      default -> "DATE_SUB(DATE(" + x + "), INTERVAL WEEKDAY(" + x + ") DAY)";
    };
  }

  private static String month(String type, String x) {
    return switch (type) {
      case "sqlserver" -> "DATEFROMPARTS(YEAR(" + x + "), MONTH(" + x + "), 1)";
      case "postgresql" -> "DATE_TRUNC('month', " + x + ")::date";
      case "oracle" -> "TRUNC(" + x + ", 'MM')";
      case "sqlite" -> "DATE(" + x + ", 'start of month')";
      case "bigquery" -> "DATE_TRUNC(" + x + ", MONTH)";
      default -> "DATE_FORMAT(" + x + ", '%Y-%m-01')";
    };
  }

  private static String year(String type, String x) {
    return switch (type) {
      case "postgresql", "oracle", "bigquery" -> "EXTRACT(YEAR FROM " + x + ")";
      case "sqlite" -> "CAST(STRFTIME('%Y', " + x + ") AS INTEGER)";
      default -> "YEAR(" + x + ")";
    };
  }

  private static String hour(String type, String x) {
    return switch (type) {
      case "sqlserver" -> "DATEPART(hour, " + x + ")";
      case "postgresql", "bigquery" -> "EXTRACT(HOUR FROM " + x + ")";
      case "oracle" -> "EXTRACT(HOUR FROM CAST(" + x + " AS TIMESTAMP))";
      case "sqlite" -> "CAST(STRFTIME('%H', " + x + ") AS INTEGER)";
      default -> "HOUR(" + x + ")";
    };
  }

  private static String dow(String type, String x) {
    return switch (type) {
      case "sqlserver" -> "DATEPART(weekday, " + x + ")";
      case "postgresql" -> "EXTRACT(DOW FROM " + x + ")";
      case "oracle" -> "TO_NUMBER(TO_CHAR(" + x + ", 'D'))";
      case "sqlite" -> "CAST(STRFTIME('%w', " + x + ") AS INTEGER)";
      case "bigquery" -> "EXTRACT(DAYOFWEEK FROM " + x + ")";
      default -> "DAYOFWEEK(" + x + ")";
    };
  }

  private static String today(String type) {
    return switch (type) {
      case "sqlserver" -> "CAST(GETDATE() AS DATE)";
      case "mysql", "mariadb" -> "CURDATE()";
      case "oracle" -> "TRUNC(SYSDATE)";
      case "sqlite" -> "DATE('now')";
      case "bigquery" -> "CURRENT_DATE()";
      default -> "CURRENT_DATE";
    };
  }

  private static String daysBetween(String type, String a, String b) {
    return switch (type) {
      case "sqlserver", "snowflake" -> "DATEDIFF(day, " + a + ", " + b + ")";
      case "mysql", "mariadb" -> "DATEDIFF(" + b + ", " + a + ")";
      case "oracle" -> "(TRUNC(" + b + ") - TRUNC(" + a + "))";
      case "sqlite" -> "(JULIANDAY(" + b + ") - JULIANDAY(" + a + "))";
      case "bigquery" -> "DATE_DIFF(" + b + ", " + a + ", DAY)";
      default -> "(CAST(" + b + " AS date) - CAST(" + a + " AS date))";
    };
  }

  private static String monthsBetween(String type, String a, String b) {
    return switch (type) {
      case "sqlserver", "snowflake" -> "DATEDIFF(month, " + a + ", " + b + ")";
      case "mysql", "mariadb" -> "TIMESTAMPDIFF(MONTH, " + a + ", " + b + ")";
      case "oracle" -> "MONTHS_BETWEEN(" + b + ", " + a + ")";
      case "bigquery" -> "DATE_DIFF(" + b + ", " + a + ", MONTH)";
      default -> "((EXTRACT(YEAR FROM " + b + ")-EXTRACT(YEAR FROM " + a + "))*12 + (EXTRACT(MONTH FROM " + b + ")-EXTRACT(MONTH FROM " + a + ")))";
    };
  }

  private static String addDays(String type, String x, String n) {
    String days = n.isBlank() ? "0" : n;
    return switch (type) {
      case "sqlserver", "snowflake" -> "DATEADD(day, " + days + ", " + x + ")";
      case "postgresql" -> "(" + x + " + INTERVAL '" + days + " days')";
      case "mysql", "mariadb", "bigquery" -> "DATE_ADD(" + x + ", INTERVAL " + days + " DAY)";
      case "sqlite" -> "DATE(" + x + ", '" + (days.startsWith("-") ? days : "+" + days) + " days')";
      default -> "(" + x + " + " + days + ")";
    };
  }

  private static String concat(String type, String a, String b) {
    return switch (type) {
      case "sqlserver", "mysql", "mariadb", "bigquery" -> "CONCAT(" + a + ", " + b + ")";
      default -> "(" + a + " || " + b + ")";
    };
  }

  private static String str(String type, String x) {
    return switch (type) {
      case "sqlserver" -> "CAST(" + x + " AS NVARCHAR(100))";
      case "postgresql" -> "CAST(" + x + " AS text)";
      case "oracle" -> "TO_CHAR(" + x + ")";
      case "bigquery" -> "CAST(" + x + " AS STRING)";
      default -> "CAST(" + x + " AS CHAR)";
    };
  }

  private static String median(String type, String x) {
    return switch (type) {
      case "sqlserver" -> "PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY " + x + ") OVER ()";
      case "oracle", "snowflake" -> "MEDIAN(" + x + ")";
      default -> "PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY " + x + ")";
    };
  }

  private static String applyLimit(String type, String sql, Integer limit) {
    if (limit == null) return sql;
    String body = sql.replaceFirst("(?i);\\s*$", "").stripTrailing();
    if ("sqlserver".equals(type)) {
      if (body.matches("(?is).*\\bSELECT\\s+TOP\\s+\\d+.*")) return body;
      return insertTop(body, limit);
    }
    if ("oracle".equals(type)) {
      if (body.toLowerCase(Locale.ROOT).contains("fetch first")) return body;
      return body + " FETCH FIRST " + limit + " ROWS ONLY";
    }
    if (body.toLowerCase(Locale.ROOT).matches("(?s).*\\blimit\\s+\\d+.*")) return body;
    return body + " LIMIT " + limit;
  }

  private static String insertTop(String sql, int limit) {
    String lower = sql.toLowerCase(Locale.ROOT);
    int depth = 0;
    int lastSelect = -1;
    for (int i = 0; i < sql.length(); i++) {
      char c = sql.charAt(i);
      if (c == '(') depth++;
      else if (c == ')') depth--;
      else if (depth == 0 && lower.startsWith("select", i) && (i == 0 || !ident(sql.charAt(i - 1)))) {
        int after = i + 6;
        if (after >= sql.length() || !ident(sql.charAt(after))) lastSelect = i;
      }
    }
    if (lastSelect < 0) return sql;
    int insertAt = lastSelect + 6;
    while (insertAt < sql.length() && Character.isWhitespace(sql.charAt(insertAt))) insertAt++;
    if (lower.startsWith("distinct", insertAt) && (insertAt + 8 >= sql.length() || !ident(sql.charAt(insertAt + 8)))) {
      insertAt += 8;
    }
    return sql.substring(0, insertAt) + " TOP " + limit + " " + sql.substring(insertAt);
  }

  private static String quoteDateColumn(String dialect, String sql) {
    String ident = quoteIdent(dialect, "date");
    Matcher matcher = DATE_ALIAS.matcher(sql);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(1) + " " + ident));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  private static List<String> splitArgs(String raw) {
    List<String> parts = new ArrayList<>();
    if (raw == null || raw.isBlank()) return parts;
    int depth = 0;
    int start = 0;
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c == '(') depth++;
      else if (c == ')') depth--;
      else if (c == ',' && depth == 0) {
        parts.add(raw.substring(start, i).trim());
        start = i + 1;
      }
    }
    parts.add(raw.substring(start).trim());
    return parts;
  }

  private static boolean ident(char c) {
    return Character.isLetterOrDigit(c) || c == '_';
  }
}

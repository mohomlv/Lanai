package com.landata.query.infrastructure.sql;

import java.util.Locale;

/** Rewrites SQL Server aggregates that overflow the 32-bit int type. */
public final class SqlServerGuards {
  private SqlServerGuards() {}

  public static String countBig(String sql) {
    return renameCall(sql, "COUNT", "COUNT_BIG");
  }

  public static String widen(String sql) {
    return wrapSum(renameCall(countBig(sql), "DATEDIFF", "DATEDIFF_BIG"));
  }

  private static String renameCall(String sql, String from, String to) {
    String lower = sql.toLowerCase(Locale.ROOT);
    String needle = from.toLowerCase(Locale.ROOT);
    StringBuilder out = new StringBuilder();
    int index = 0;
    while (index < sql.length()) {
      int at = lower.indexOf(needle, index);
      if (at < 0) {
        out.append(sql, index, sql.length());
        break;
      }
      boolean boundary = at == 0 || !isIdent(sql.charAt(at - 1));
      int after = at + needle.length();
      int paren = skipSpace(sql, after);
      if (boundary && paren < sql.length() && sql.charAt(paren) == '(' && (after >= sql.length() || sql.charAt(after) != '_')) {
        out.append(sql, index, at).append(to);
        index = after;
      } else {
        out.append(sql, index, at + 1);
        index = at + 1;
      }
    }
    return out.toString();
  }

  private static String wrapSum(String sql) {
    String lower = sql.toLowerCase(Locale.ROOT);
    StringBuilder out = new StringBuilder();
    int index = 0;
    while (index < sql.length()) {
      int at = lower.indexOf("sum", index);
      if (at < 0) {
        out.append(sql, index, sql.length());
        break;
      }
      boolean boundary = at == 0 || !isIdent(sql.charAt(at - 1));
      int paren = skipSpace(sql, at + 3);
      if (!boundary || paren >= sql.length() || sql.charAt(paren) != '(') {
        out.append(sql, index, at + 1);
        index = at + 1;
        continue;
      }
      int close = matchingParen(sql, paren);
      if (close < 0) {
        out.append(sql, index, sql.length());
        break;
      }
      String inner = sql.substring(paren + 1, close);
      String folded = inner.toLowerCase(Locale.ROOT);
      out.append(sql, index, at);
      if (folded.stripLeading().startsWith("cast(") && folded.contains("bigint")) {
        out.append(sql, at, close + 1);
      } else {
        out.append("SUM(CAST(").append(inner).append(" AS bigint))");
      }
      index = close + 1;
    }
    return out.toString();
  }

  private static int matchingParen(String sql, int open) {
    int depth = 0;
    boolean quote = false;
    for (int i = open; i < sql.length(); i++) {
      char ch = sql.charAt(i);
      if (ch == '\'') {
        if (quote && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
          i++;
          continue;
        }
        quote = !quote;
        continue;
      }
      if (quote) continue;
      if (ch == '(') depth++;
      else if (ch == ')' && --depth == 0) return i;
    }
    return -1;
  }

  private static int skipSpace(String sql, int index) {
    while (index < sql.length() && Character.isWhitespace(sql.charAt(index))) index++;
    return index;
  }

  private static boolean isIdent(char ch) {
    return Character.isLetterOrDigit(ch) || ch == '_';
  }
}

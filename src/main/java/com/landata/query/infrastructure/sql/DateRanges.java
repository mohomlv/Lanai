package com.landata.query.infrastructure.sql;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DateRanges {
  private DateRanges() {}

  public record Range(LocalDate start, LocalDate endInclusive) {
    public LocalDate endExclusive() {
      return endInclusive.plusDays(1);
    }
  }

  public static Range resolve(Object value, ZoneId zone) {
    if (value instanceof String text) {
      String trimmed = text.trim();
      if (trimmed.regionMatches(true, 0, "preset:", 0, 7)) {
        return preset(trimmed.substring(7).trim(), LocalDate.now(zone));
      }
      int tilde = trimmed.indexOf('~');
      if (tilde > 0) {
        return new Range(LocalDate.parse(trimmed.substring(0, tilde).trim()), LocalDate.parse(trimmed.substring(tilde + 1).trim()));
      }
      throw new QueryProblem("BAD_PARAM_TYPE", "A date range looks like 2026-01-01~2026-01-31 or preset:last_30_days");
    }
    if (value instanceof Map<?, ?> raw) {
      Object preset = raw.get("preset");
      if (preset != null) {
        return preset(String.valueOf(preset), LocalDate.now(zone));
      }
      Object start = raw.get("start");
      Object end = raw.get("end");
      if (start == null || end == null) {
        throw new QueryProblem("BAD_PARAM_TYPE", "A date range needs start and end, or a preset");
      }
      return new Range(LocalDate.parse(String.valueOf(start)), LocalDate.parse(String.valueOf(end)));
    }
    throw new QueryProblem("BAD_PARAM_TYPE", "A date range must be an object");
  }

  public static Range preset(String name, LocalDate today) {
    String key = name.toLowerCase(Locale.ROOT);
    return switch (key) {
      case "today" -> new Range(today, today);
      case "yesterday" -> new Range(today.minusDays(1), today.minusDays(1));
      case "last_7_days" -> new Range(today.minusDays(6), today);
      case "last_30_days" -> new Range(today.minusDays(29), today);
      case "last_90_days" -> new Range(today.minusDays(89), today);
      case "this_week" -> {
        LocalDate start = today.with(DayOfWeek.MONDAY);
        yield new Range(start, start.plusDays(6));
      }
      case "this_month" -> new Range(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()));
      case "last_month" -> {
        LocalDate first = today.withDayOfMonth(1).minusMonths(1);
        yield new Range(first, first.withDayOfMonth(first.lengthOfMonth()));
      }
      case "this_quarter" -> quarter(today, 0);
      case "this_year" -> new Range(LocalDate.of(today.getYear(), 1, 1), LocalDate.of(today.getYear(), 12, 31));
      case "last_year" -> new Range(LocalDate.of(today.getYear() - 1, 1, 1), LocalDate.of(today.getYear() - 1, 12, 31));
      default -> throw new QueryProblem("BAD_PARAM_TYPE", "Unknown date preset " + name);
    };
  }

  private static Range quarter(LocalDate today, int offset) {
    int q = ((today.getMonthValue() - 1) / 3) + 1 + offset;
    int year = today.getYear();
    while (q < 1) {
      q += 4;
      year -= 1;
    }
    int startMonth = ((q - 1) * 3) + 1;
    LocalDate start = LocalDate.of(year, startMonth, 1);
    LocalDate end = start.plusMonths(3).minusDays(1);
    return new Range(start, end);
  }

  public static boolean isEmpty(Object value) {
    if (value == null) return true;
    if (value instanceof String s) return s.isBlank();
    if (value instanceof List<?> list) return list.isEmpty();
    if (value instanceof Map<?, ?> map) return map.isEmpty();
    return false;
  }
}

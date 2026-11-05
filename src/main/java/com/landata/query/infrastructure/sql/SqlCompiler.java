package com.landata.query.infrastructure.sql;

import java.sql.Date;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SqlCompiler {
  private static final Set<String> AGGS = Set.of("count", "count_distinct", "sum", "avg", "min", "max");
  private static final Set<String> GRAINS = Set.of("hour", "day", "week", "month", "quarter", "year");
  private static final Set<String> JOIN_TYPES = Set.of("inner", "left", "right", "full");

  private SqlCompiler() {}

  public record Compiled(String sql, List<Object> binds) {}

  public static Compiled compile(
      Map<String, Object> spec,
      List<Map<String, Object>> parameters,
      Map<String, Object> supplied,
      String dialectName,
      java.time.ZoneId zone,
      int maxRows
  ) {
    if (spec == null) {
      throw new QueryProblem("SCHEMA_VIOLATION", "query_spec is required");
    }
    Object version = spec.get("version");
    if (version != null && ((Number) version).intValue() != 1) {
      throw new QueryProblem("SCHEMA_VIOLATION", "Unsupported query_spec version");
    }
    Dialect dialect = Dialect.of(dialectName);
    Map<String, Object> resolved = NativeSql.resolve(parameters, supplied, zone);
    Map<String, String> types = NativeSql.types(parameters);

    Map<String, Object> source = Maps.map(spec.get("source"));
    String table = Maps.str(source, "table");
    String alias = Maps.str(source, "alias");
    if (table == null || alias == null) {
      throw new QueryProblem("SCHEMA_VIOLATION", "source.table and source.alias are required");
    }

    List<SelectPiece> selects = new ArrayList<>();
    for (Object item : Maps.list(spec.get("select"))) {
      selects.add(selectPiece(Maps.map(item), dialect));
    }
    if (selects.isEmpty()) {
      throw new QueryProblem("SCHEMA_VIOLATION", "select needs at least one column");
    }
    boolean aggregated = selects.stream().anyMatch(SelectPiece::aggregated);
    if (aggregated) {
      List<String> group = Maps.strings(spec.get("group_by"));
      for (SelectPiece piece : selects) {
        if (!piece.aggregated && !group.contains(piece.alias)) {
          throw new QueryProblem("MISSING_GROUP_BY", piece.alias);
        }
      }
    }

    List<Object> binds = new ArrayList<>();
    StringBuilder sql = new StringBuilder();
    Integer limit = limit(spec.get("limit"), maxRows);
    sql.append("SELECT ");
    if (dialect == Dialect.SQLSERVER && limit != null) {
      sql.append("TOP (").append(limit).append(") ");
    }
    for (int i = 0; i < selects.size(); i++) {
      if (i > 0) sql.append(", ");
      SelectPiece piece = selects.get(i);
      sql.append(piece.expression).append(" AS ").append(dialect.quote(piece.alias));
    }
    sql.append(" FROM ").append(dialect.qualify(table)).append(" AS ").append(dialect.quote(alias));

    for (Object rawJoin : Maps.list(spec.get("joins"))) {
      Map<String, Object> join = Maps.map(rawJoin);
      String type = Maps.str(join, "type");
      if (type == null || !JOIN_TYPES.contains(type)) {
        throw new QueryProblem("SCHEMA_VIOLATION", "join type is invalid");
      }
      if ("full".equals(type) && (dialect == Dialect.MYSQL || dialect == Dialect.MARIADB)) {
        throw new QueryProblem("SCHEMA_VIOLATION", "FULL JOIN is not supported on this database");
      }
      sql.append(" ").append(type.toUpperCase(Locale.ROOT)).append(" JOIN ");
      sql.append(dialect.qualify(Maps.str(join, "table"))).append(" AS ").append(dialect.quote(Maps.str(join, "alias")));
      sql.append(" ON ");
      List<Object> ons = Maps.list(join.get("on"));
      if (ons.isEmpty()) {
        throw new QueryProblem("SCHEMA_VIOLATION", "join needs on");
      }
      for (int i = 0; i < ons.size(); i++) {
        if (i > 0) sql.append(" AND ");
        Map<String, Object> on = Maps.map(ons.get(i));
        sql.append(dialect.qualify(Maps.str(on, "left")))
            .append(" ")
            .append(compareOp(Maps.str(on, "op")))
            .append(" ")
            .append(dialect.qualify(Maps.str(on, "right")));
      }
    }

    Object filters = spec.get("filters");
    if (filters != null) {
      Piece where = filter(filters, dialect, resolved, types, binds);
      if (where != null && !where.sql.isBlank()) {
        sql.append(" WHERE ").append(where.sql);
      }
    }

    if (aggregated) {
      List<String> group = Maps.strings(spec.get("group_by"));
      if (!group.isEmpty()) {
        sql.append(" GROUP BY ");
        Map<String, String> byAlias = new LinkedHashMap<>();
        for (SelectPiece piece : selects) {
          byAlias.put(piece.alias, piece.expression);
        }
        for (int i = 0; i < group.size(); i++) {
          if (i > 0) sql.append(", ");
          String ref = group.get(i);
          sql.append(byAlias.getOrDefault(ref, dialect.qualify(ref)));
        }
      }
    }

    List<Object> order = Maps.list(spec.get("order_by"));
    if (!order.isEmpty()) {
      sql.append(" ORDER BY ");
      Set<String> aliases = selects.stream().map(s -> s.alias).collect(java.util.stream.Collectors.toSet());
      for (int i = 0; i < order.size(); i++) {
        if (i > 0) sql.append(", ");
        Map<String, Object> item = Maps.map(order.get(i));
        String ref = Maps.str(item, "ref");
        String dir = "desc".equalsIgnoreCase(Maps.str(item, "dir")) ? "DESC" : "ASC";
        sql.append(aliases.contains(ref) ? dialect.quote(ref) : dialect.qualify(ref)).append(" ").append(dir);
      }
    }

    if (limit != null && dialect == Dialect.ORACLE) {
      sql.append(" FETCH FIRST ").append(limit).append(" ROWS ONLY");
    } else if (limit != null && dialect != Dialect.SQLSERVER) {
      sql.append(" LIMIT ").append(limit);
    }
    return new Compiled(sql.toString(), binds);
  }

  private static Integer limit(Object raw, int maxRows) {
    if (raw == null) return Math.min(1000, maxRows);
    int value = raw instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(raw));
    if (value < 1) throw new QueryProblem("SCHEMA_VIOLATION", "limit must be positive");
    return Math.min(value, maxRows);
  }

  private static SelectPiece selectPiece(Map<String, Object> item, Dialect dialect) {
    String alias = Maps.str(item, "alias");
    if (alias == null || !alias.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      throw new QueryProblem("SCHEMA_VIOLATION", "select alias is invalid");
    }
    Map<String, Object> expr = Maps.map(item.get("expr"));
    if (expr.containsKey("agg")) {
      String agg = Maps.str(expr, "agg");
      if (!AGGS.contains(agg)) throw new QueryProblem("SCHEMA_VIOLATION", "Unknown aggregation " + agg);
      String column = Maps.str(expr, "column");
      boolean distinct = Boolean.TRUE.equals(expr.get("distinct")) || "count_distinct".equals(agg);
      String function = switch (agg) {
        case "count_distinct" -> "COUNT";
        default -> agg.toUpperCase(Locale.ROOT);
      };
      String inner = column == null ? "*" : dialect.qualify(column);
      if (distinct && column != null) inner = "DISTINCT " + inner;
      return new SelectPiece(alias, function + "(" + inner + ")", true);
    }
    if (expr.containsKey("date_bucket")) {
      Map<String, Object> bucket = Maps.map(expr.get("date_bucket"));
      String grain = Maps.str(bucket, "grain");
      if (!GRAINS.contains(grain)) throw new QueryProblem("SCHEMA_VIOLATION", "Unknown date grain");
      return new SelectPiece(alias, dialect.dateBucket(Maps.str(bucket, "column"), grain), false);
    }
    if (expr.containsKey("column")) {
      return new SelectPiece(alias, dialect.qualify(Maps.str(expr, "column")), false);
    }
    if (expr.containsKey("literal")) {
      return new SelectPiece(alias, "?", false);
    }
    throw new QueryProblem("SCHEMA_VIOLATION", "Unsupported select expression");
  }

  private static Piece filter(
      Object node,
      Dialect dialect,
      Map<String, Object> resolved,
      Map<String, String> types,
      List<Object> binds
  ) {
    Map<String, Object> map = Maps.map(node);
    if (map.containsKey("and") || map.containsKey("or")) {
      String key = map.containsKey("and") ? "and" : "or";
      List<String> parts = new ArrayList<>();
      for (Object child : Maps.list(map.get(key))) {
        Piece piece = filter(child, dialect, resolved, types, binds);
        if (piece != null && !piece.sql.isBlank()) parts.add("(" + piece.sql + ")");
      }
      return new Piece(String.join(" " + key.toUpperCase(Locale.ROOT) + " ", parts));
    }
    if (map.containsKey("not")) {
      Piece inner = filter(map.get("not"), dialect, resolved, types, binds);
      return new Piece("NOT (" + inner.sql + ")");
    }
    String column = dialect.qualify(Maps.str(map, "column"));
    String op = Maps.str(map, "op");
    Object value = unwrap(map.get("value"), resolved, types);
    String type = valueType(map.get("value"), types);
    return switch (op) {
      case "is_null" -> new Piece(column + " IS NULL");
      case "not_null" -> new Piece(column + " IS NOT NULL");
      case "is_empty" -> new Piece("(" + column + " IS NULL OR " + column + " = '')");
      case "not_empty" -> new Piece("(" + column + " IS NOT NULL AND " + column + " <> '')");
      case "between" -> between(column, value, type, binds);
      case "in", "not_in" -> inList(column, op, value, binds);
      case "contains", "not_contains", "starts_with", "ends_with" -> like(column, op, value, binds);
      case "=", "!=", "<", "<=", ">", ">=" -> compare(column, op, value, binds);
      default -> throw new QueryProblem("SCHEMA_VIOLATION", "Unsupported operator " + op);
    };
  }

  private static Piece between(String column, Object value, String type, List<Object> binds) {
    if ("date_range".equals(type) && value instanceof DateRanges.Range range) {
      binds.add(Date.valueOf(range.start()));
      binds.add(Date.valueOf(range.endExclusive()));
      return new Piece(column + " >= ? AND " + column + " < ?");
    }
    if (value instanceof List<?> list && list.size() == 2) {
      binds.add(list.get(0));
      binds.add(list.get(1));
      return new Piece(column + " >= ? AND " + column + " <= ?");
    }
    throw new QueryProblem("BAD_PARAM_TYPE", "between needs two values");
  }

  private static Piece inList(String column, String op, Object value, List<Object> binds) {
    List<?> list = value instanceof List<?> values ? values : List.of(value);
    if (list.isEmpty()) throw new QueryProblem("NEEDS_INPUT", "IN list is empty");
    String marks = String.join(", ", list.stream().map(v -> "?").toList());
    binds.addAll(list);
    String keyword = "not_in".equals(op) ? "NOT IN" : "IN";
    return new Piece(column + " " + keyword + " (" + marks + ")");
  }

  private static Piece like(String column, String op, Object value, List<Object> binds) {
    String text = escapeLike(String.valueOf(value));
    String pattern = switch (op) {
      case "starts_with" -> text + "%";
      case "ends_with" -> "%" + text;
      default -> "%" + text + "%";
    };
    binds.add(pattern);
    String keyword = op.startsWith("not") ? "NOT LIKE" : "LIKE";
    return new Piece("LOWER(" + column + ") " + keyword + " LOWER(?) ESCAPE '\\'");
  }

  private static Piece compare(String column, String op, Object value, List<Object> binds) {
    binds.add(value);
    return new Piece(column + " " + compareOp(op) + " ?");
  }

  private static String compareOp(String op) {
    return switch (op) {
      case "=", "!=", "<", "<=", ">", ">=" -> op;
      case null -> "=";
      default -> throw new QueryProblem("SCHEMA_VIOLATION", "Unsupported comparison");
    };
  }

  private static Object unwrap(Object value, Map<String, Object> resolved, Map<String, String> types) {
    if (value instanceof Map<?, ?> map && map.containsKey("param")) {
      String name = String.valueOf(map.get("param"));
      if (!resolved.containsKey(name) || DateRanges.isEmpty(resolved.get(name))) {
        throw new QueryProblem("NEEDS_INPUT", name);
      }
      return resolved.get(name);
    }
    return value;
  }

  private static String valueType(Object value, Map<String, String> types) {
    if (value instanceof Map<?, ?> map && map.containsKey("param")) {
      return types.get(String.valueOf(map.get("param")));
    }
    return null;
  }

  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private record SelectPiece(String alias, String expression, boolean aggregated) {}

  private record Piece(String sql) {}

  public enum Dialect {
    POSTGRES, MYSQL, MARIADB, SQLSERVER, ORACLE;

    static Dialect of(String name) {
      return switch (name == null ? "" : name.toLowerCase(Locale.ROOT)) {
        case "mysql", "clickhouse" -> MYSQL;
        case "mariadb" -> MARIADB;
        case "sqlserver" -> SQLSERVER;
        case "oracle" -> ORACLE;
        default -> POSTGRES;
      };
    }

    String quote(String ident) {
      if (ident == null) throw new QueryProblem("SCHEMA_VIOLATION", "Missing identifier");
      return switch (this) {
        case MYSQL, MARIADB -> "`" + ident.replace("`", "``") + "`";
        case SQLSERVER -> "[" + ident.replace("]", "]]") + "]";
        case POSTGRES, ORACLE -> "\"" + ident.replace("\"", "\"\"") + "\"";
      };
    }

    String qualify(String ref) {
      String[] parts = ref.split("\\.");
      StringBuilder out = new StringBuilder();
      for (int i = 0; i < parts.length; i++) {
        if (i > 0) out.append(".");
        out.append(quote(parts[i]));
      }
      return out.toString();
    }

    String dateBucket(String column, String grain) {
      String quoted = qualify(column);
      return switch (this) {
        case POSTGRES -> "date_trunc('" + grain + "', " + quoted + ")";
        case SQLSERVER -> switch (grain) {
          case "hour" -> "DATEADD(hour, DATEDIFF(hour, 0, " + quoted + "), 0)";
          case "day" -> "DATEADD(day, DATEDIFF(day, 0, " + quoted + "), 0)";
          case "week" -> "DATEADD(week, DATEDIFF(week, 0, " + quoted + "), 0)";
          case "quarter" -> "DATEADD(quarter, DATEDIFF(quarter, 0, " + quoted + "), 0)";
          case "year" -> "DATEADD(year, DATEDIFF(year, 0, " + quoted + "), 0)";
          default -> "DATEADD(month, DATEDIFF(month, 0, " + quoted + "), 0)";
        };
        case MYSQL, MARIADB -> switch (grain) {
          case "day" -> "DATE(" + quoted + ")";
          case "year" -> "DATE_FORMAT(" + quoted + ", '%Y-01-01')";
          default -> "DATE_FORMAT(" + quoted + ", '%Y-%m-01')";
        };
        case ORACLE -> switch (grain) {
          case "year" -> "TRUNC(" + quoted + ", 'YYYY')";
          case "quarter" -> "TRUNC(" + quoted + ", 'Q')";
          case "week" -> "TRUNC(" + quoted + ", 'IW')";
          case "day" -> "TRUNC(" + quoted + ")";
          default -> "TRUNC(" + quoted + ", 'MM')";
        };
      };
    }
  }

  static final class Maps {
    private Maps() {}

    static Map<String, Object> map(Object value) {
      if (!(value instanceof Map<?, ?> raw)) return new LinkedHashMap<>();
      Map<String, Object> out = new LinkedHashMap<>();
      raw.forEach((key, item) -> out.put(String.valueOf(key), item));
      return out;
    }

    static List<Object> list(Object value) {
      if (value instanceof List<?> raw) return new ArrayList<>(raw);
      return List.of();
    }

    static List<String> strings(Object value) {
      List<String> out = new ArrayList<>();
      for (Object item : list(value)) out.add(String.valueOf(item));
      return out;
    }

    static String str(Map<String, Object> map, String key) {
      Object value = map.get(key);
      return value == null ? null : String.valueOf(value);
    }
  }
}

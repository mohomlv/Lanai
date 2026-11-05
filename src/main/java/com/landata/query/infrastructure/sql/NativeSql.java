package com.landata.query.infrastructure.sql;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NativeSql {
  private static final Pattern BLOCK = Pattern.compile("\\[\\[(.+?)]]", Pattern.DOTALL);
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*(@?[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)?)\\s*}}");
  private static final Pattern USER_TOKEN = Pattern.compile("(?<![A-Za-z0-9_@{])@([A-Za-z_][A-Za-z0-9_]*)");

  private NativeSql() {}

  public record Rendered(String sql, List<Object> binds) {}

  public static Map<String, String> types(List<Map<String, Object>> declarations) {
    Map<String, String> types = new LinkedHashMap<>();
    if (declarations == null) return types;
    for (Map<String, Object> declaration : declarations) {
      types.put(SqlCompiler.Maps.str(declaration, "name"), SqlCompiler.Maps.str(declaration, "type"));
    }
    return types;
  }

  public static Map<String, Object> resolve(List<Map<String, Object>> declarations, Map<String, Object> supplied, ZoneId zone) {
    Map<String, Object> resolved = new LinkedHashMap<>();
    if (declarations == null) return resolved;
    Map<String, Object> values = supplied == null ? Map.of() : supplied;
    for (Map<String, Object> declaration : declarations) {
      String name = SqlCompiler.Maps.str(declaration, "name");
      Object raw = values.containsKey(name) ? values.get(name) : declaration.get("default");
      if (DateRanges.isEmpty(raw)) {
        resolved.put(name, null);
        continue;
      }
      resolved.put(name, coerce(declaration, raw, zone));
    }
    return resolved;
  }

  public static String expandUserTokens(String sql) {
    if (sql == null || sql.isBlank()) return sql;
    Matcher matcher = USER_TOKEN.matcher(sql);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String name = matcher.group(1);
      String replacement = UserAttributes.isAttribute(name) ? "{{@" + name + "}}" : matcher.group();
      matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  public static String forAskDraft(String sql) {
    if (sql == null || sql.isBlank()) return sql;
    return cleanup(BLOCK.matcher(expandUserTokens(sql)).replaceAll(""));
  }

  public static boolean hasPlaceholders(String sql) {
    if (sql == null) return false;
    String expanded = expandUserTokens(sql);
    return PLACEHOLDER.matcher(expanded).find();
  }

  public static Rendered render(
      String template,
      List<Map<String, Object>> declarations,
      Map<String, Object> supplied,
      ZoneId zone,
      int maxBinds
  ) {
    return render(template, declarations, supplied, zone, maxBinds, Map.of());
  }

  public static Rendered render(
      String template,
      List<Map<String, Object>> declarations,
      Map<String, Object> supplied,
      ZoneId zone,
      int maxBinds,
      Map<String, Object> userAttributes
  ) {
    if (template == null || template.isBlank()) {
      throw new QueryProblem("SCHEMA_VIOLATION", "native_sql is required");
    }
    template = expandUserTokens(template);
    if (declarations == null) declarations = List.of();
    Map<String, Object> resolved = resolve(declarations, supplied, zone);
    if (userAttributes != null) {
      userAttributes.forEach((key, value) -> {
        if (UserAttributes.isAttribute(key)) resolved.put(UserAttributes.canonical(key), value);
      });
    }
    Map<String, String> types = types(declarations);
    if (userAttributes != null) {
      userAttributes.forEach((key, value) -> {
        if (UserAttributes.isAttribute(key)) types.put(UserAttributes.canonical(key), UserAttributes.type(key));
      });
    }
    List<String> declared = new ArrayList<>(types.keySet());
    List<String> referenced = placeholders(template);
    for (String name : referenced) {
      if (UserAttributes.isAttribute(name)) continue;
      if (!types.containsKey(name)) {
        throw new QueryProblem("UNDECLARED_PARAM", "{{" + name + "}} is in the SQL but was not declared");
      }
    }
    for (String name : declared) {
      if (!referenced.contains(name) && required(declarations, name)) {
        throw new QueryProblem("UNUSED_PARAM", name + " is required but {{" + name + "}} is not in the SQL");
      }
    }
    for (String name : referenced) {
      if (required(declarations, name) && DateRanges.isEmpty(resolved.get(name)) && !insideRemovedBlock(template, name, resolved)) {
        throw new QueryProblem("NEEDS_INPUT", name);
      }
    }

    String withBlocks = removeEmptyBlocks(template, resolved);
    List<Object> binds = new ArrayList<>();
    Matcher matcher = PLACEHOLDER.matcher(withBlocks);
    StringBuilder sql = new StringBuilder();
    while (matcher.find()) {
      String token = matcher.group(1);
      String rawName = token.contains(".") ? token.substring(0, token.indexOf('.')) : token;
      String name = UserAttributes.isAttribute(rawName) ? UserAttributes.canonical(rawName) : rawName;
      String part = token.contains(".") ? token.substring(token.indexOf('.') + 1) : null;
      Object value = resolved.get(name);
      String type = types.get(name);
      Map<String, Object> declaration = declaration(declarations, name);
      if (UserAttributes.isAttribute(name) && declaration.isEmpty()) {
        declaration = Map.of(
            "name", name,
            "type", UserAttributes.type(name),
            "selection_mode", UserAttributes.isBranches(name) ? "multi" : "single"
        );
        type = UserAttributes.type(name);
      }
      if (DateRanges.isEmpty(value)) {
        if (UserAttributes.isAttribute(name)) {
          throw new QueryProblem("NEEDS_INPUT", "@" + name + " has no value. Wrap that filter in [[ ]].");
        }
        if (required(declarations, name) || "date_range".equals(type) || multi(declaration)) {
          throw new QueryProblem("NEEDS_INPUT", name);
        }
        binds.add(null);
        matcher.appendReplacement(sql, "?");
        continue;
      }
      String replacement = bind(declaration, part, value, binds);
      if (multi(declaration)) {
        replacement = wrapInList(withBlocks, matcher.start(), replacement);
      }
      matcher.appendReplacement(sql, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(sql);
    if (binds.size() > maxBinds) {
      throw new QueryProblem("BAD_PARAM_TYPE", "Too many bound parameters");
    }
    return new Rendered(cleanup(sql.toString()), binds);
  }

  private static boolean required(List<Map<String, Object>> declarations, String name) {
    return declarations != null && declarations.stream().anyMatch(item ->
        name.equals(SqlCompiler.Maps.str(item, "name")) && Boolean.TRUE.equals(item.get("required")));
  }

  private static boolean insideRemovedBlock(String template, String name, Map<String, Object> resolved) {
    Matcher matcher = BLOCK.matcher(template);
    while (matcher.find()) {
      String body = matcher.group(1);
      List<String> names = placeholders(body);
      if (names.contains(name) && names.stream().allMatch(item -> DateRanges.isEmpty(resolved.get(item)))) {
        return true;
      }
    }
    return false;
  }

  private static String removeEmptyBlocks(String template, Map<String, Object> resolved) {
    Matcher matcher = BLOCK.matcher(template);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      List<String> names = placeholders(matcher.group(1));
      boolean empty = names.stream().allMatch(name -> DateRanges.isEmpty(resolved.get(name)));
      matcher.appendReplacement(out, empty ? "" : Matcher.quoteReplacement(matcher.group(1)));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  private static List<String> placeholders(String sql) {
    List<String> names = new ArrayList<>();
    Matcher matcher = PLACEHOLDER.matcher(sql);
    while (matcher.find()) {
      String token = matcher.group(1);
      String rawName = token.contains(".") ? token.substring(0, token.indexOf('.')) : token;
      String name = UserAttributes.isAttribute(rawName) ? UserAttributes.canonical(rawName) : rawName;
      if (!names.contains(name)) names.add(name);
    }
    return names;
  }

  private static Map<String, Object> declaration(List<Map<String, Object>> declarations, String name) {
    if (declarations == null) return Map.of();
    for (Map<String, Object> item : declarations) {
      if (name.equals(SqlCompiler.Maps.str(item, "name"))) return item;
    }
    return Map.of();
  }

  private static String wrapInList(String sql, int start, String replacement) {
    int i = start - 1;
    while (i >= 0 && Character.isWhitespace(sql.charAt(i))) i--;
    if (i >= 0 && sql.charAt(i) == '(') return replacement;
    return "(" + replacement + ")";
  }

  static boolean multi(Map<String, Object> declaration) {
    String type = SqlCompiler.Maps.str(declaration, "type");
    if ("date_range".equals(type)) return false;
    if ("select_multi".equals(type)) return true;
    return "multi".equals(SqlCompiler.Maps.str(declaration, "selection_mode"));
  }

  private static String bind(Map<String, Object> declaration, String part, Object value, List<Object> binds) {
    String type = SqlCompiler.Maps.str(declaration, "type");
    if ("date_range".equals(type)) {
      if (!(value instanceof DateRanges.Range range)) {
        throw new QueryProblem("BAD_PARAM_TYPE", "date_range parameter is invalid");
      }
      if (part == null) {
        throw new QueryProblem("BAD_PARAM_TYPE", "Use {{name.start}} and {{name.end_exclusive}} for a date range");
      }
      LocalDate date = switch (part) {
        case "start" -> range.start();
        case "end" -> range.endInclusive();
        case "end_exclusive" -> range.endExclusive();
        default -> throw new QueryProblem("BAD_PARAM_TYPE", "Unknown date range part " + part);
      };
      binds.add(Date.valueOf(date));
      return "?";
    }
    if (part != null) {
      throw new QueryProblem("BAD_PARAM_TYPE", "Only date ranges have parts");
    }
    if (multi(declaration)) {
      if (!(value instanceof List<?> list) || list.isEmpty()) {
        throw new QueryProblem("NEEDS_INPUT", "Multi-select has no values");
      }
      list.forEach(binds::add);
      return String.join(", ", list.stream().map(item -> "?").toList());
    }
    binds.add(value);
    return "?";
  }

  static String cleanup(String sql) {
    String cleaned = sql;
    cleaned = cleaned.replaceAll("(?i)\\bWHERE\\s+(AND|OR)\\b", "WHERE");
    cleaned = cleaned.replaceAll("(?i)\\b(AND|OR)\\s+(GROUP\\s+BY|ORDER\\s+BY|LIMIT|OFFSET|FETCH|UNION)\\b", "$2");
    cleaned = cleaned.replaceAll("(?i)\\bWHERE\\s+(GROUP\\s+BY|ORDER\\s+BY|LIMIT|OFFSET|FETCH|UNION)\\b", "$1");
    cleaned = cleaned.replaceAll("(?i)\\s+\\bWHERE\\s*(?=\\)|$)", "");
    cleaned = cleaned.replaceAll("(?i)\\s+\\b(AND|OR)\\s*(?=\\)|$)", "");
    return cleaned.replaceAll("[ \\t]{2,}", " ").trim();
  }

  private static Object coerce(Map<String, Object> declaration, Object raw, ZoneId zone) {
    String type = SqlCompiler.Maps.str(declaration, "type");
    if (multi(declaration)) {
      List<?> list = raw instanceof List<?> values ? values : List.of(raw);
      String element = "select_multi".equals(type) ? "text" : type;
      return list.stream().map(item -> coerceScalar(element, item, zone)).toList();
    }
    return coerceScalar(type, raw, zone);
  }

  private static Object coerceScalar(String type, Object raw, ZoneId zone) {
    if (type == null || raw == null) return raw;
    return switch (type) {
      case "date_range" -> DateRanges.resolve(raw, zone);
      case "date" -> {
        if (raw instanceof Map<?, ?> map && map.get("preset") != null) {
          yield Date.valueOf(DateRanges.preset(String.valueOf(map.get("preset")), java.time.LocalDate.now(zone)).start());
        }
        yield Date.valueOf(LocalDate.parse(String.valueOf(raw)));
      }
      case "number" -> raw instanceof Number ? raw : new java.math.BigDecimal(String.valueOf(raw));
      case "boolean" -> raw instanceof Boolean ? raw : Boolean.parseBoolean(String.valueOf(raw));
      case "entity" -> raw instanceof Number || raw instanceof Boolean ? raw : String.valueOf(raw);
      default -> raw;
    };
  }
}

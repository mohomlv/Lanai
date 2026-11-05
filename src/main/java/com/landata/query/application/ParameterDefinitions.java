package com.landata.query.application;

import com.landata.query.infrastructure.sql.QueryProblem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ParameterDefinitions {
  public static final Set<String> TYPES = Set.of(
      "text", "number", "boolean", "date", "date_range", "entity", "select", "select_multi"
  );
  public static final Set<String> ROLES = Set.of("owner", "admin", "editor", "viewer");
  private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final int MAX_FIELDS = 40;
  private static final int MAX_STATIC = 500;

  private ParameterDefinitions() {}

  public static List<Map<String, Object>> normalize(Object raw) {
    if (!(raw instanceof List<?> list)) {
      throw new QueryProblem("BAD_PARAM_TYPE", "parameters must be a list");
    }
    List<Map<String, Object>> out = new ArrayList<>();
    List<String> names = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> map)) {
        throw new QueryProblem("BAD_PARAM_TYPE", "A parameter must be an object");
      }
      Map<String, Object> row = one(map);
      String name = String.valueOf(row.get("name"));
      if (names.contains(name)) {
        throw new QueryProblem("BAD_PARAM_TYPE", "Parameter name is duplicated: " + name);
      }
      names.add(name);
      out.add(row);
    }
    return out;
  }

  public static Map<String, Object> fieldsConfig(Object raw) {
    if (raw == null) return null;
    if (!(raw instanceof Map<?, ?> map)) {
      throw new QueryProblem("BAD_PARAM_TYPE", "parameter_fields must be an object");
    }
    List<String> fields = new ArrayList<>();
    Object list = map.get("fields");
    if (list instanceof List<?> items) {
      if (items.size() > MAX_FIELDS) {
        throw new QueryProblem("BAD_PARAM_TYPE", "Too many parameter fields");
      }
      for (Object item : items) {
        String name = identifier(item, "field");
        if (!fields.contains(name)) fields.add(name);
      }
    }
    String valueField = optionalIdentifier(map.get("value_field"), "value_field");
    String displayField = optionalIdentifier(map.get("display_field"), "display_field");
    if (valueField != null && !fields.contains(valueField)) {
      throw new QueryProblem("BAD_PARAM_TYPE", "value_field is not one of the parameter fields");
    }
    if (displayField != null && !fields.contains(displayField)) {
      throw new QueryProblem("BAD_PARAM_TYPE", "display_field is not one of the parameter fields");
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("fields", fields);
    if (valueField != null) out.put("value_field", valueField);
    if (displayField != null) out.put("display_field", displayField);
    return out;
  }

  public static void requireReady(Map<String, Object> config) {
    if (config == null || fieldNames(config).isEmpty()) {
      throw new QueryProblem("BAD_PARAM_TYPE", "A parameter question needs at least one field");
    }
    if (text(config, "value_field") == null || text(config, "display_field") == null) {
      throw new QueryProblem("BAD_PARAM_TYPE", "A parameter question needs a value field and a display field");
    }
  }

  public static void requireConfiguredField(Map<String, Object> config, String field) {
    if (field == null || field.isBlank() || !fieldNames(config).contains(field)) {
      throw new QueryProblem("BAD_PARAM_TYPE", "Field is not available on the parameter question");
    }
  }

  public static List<String> fieldNames(Map<String, Object> config) {
    if (config == null || !(config.get("fields") instanceof List<?> list)) return List.of();
    List<String> names = new ArrayList<>();
    for (Object item : list) {
      if (item != null) names.add(String.valueOf(item));
    }
    return names;
  }

  public static String text(Map<String, Object> map, String key) {
    if (map == null) return null;
    Object value = map.get(key);
    if (value == null) return null;
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
  }

  private static Map<String, Object> one(Map<?, ?> raw) {
    String name = identifier(raw.get("name"), "name");
    String type = textOf(raw.get("type"));
    if (type == null || !TYPES.contains(type)) {
      throw new QueryProblem("BAD_PARAM_TYPE", "Unknown parameter type");
    }
    boolean multi = multi(type, textOf(raw.get("selection_mode")));
    if ("date_range".equals(type) && multi) {
      throw new QueryProblem("BAD_PARAM_TYPE", "A date range cannot be multi-select");
    }
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("name", name);
    row.put("type", type);
    row.put("selection_mode", multi ? "multi" : "single");
    String label = textOf(raw.get("label"));
    if (label != null) {
      if (label.length() > 200) {
        throw new QueryProblem("BAD_PARAM_TYPE", "Parameter label is too long");
      }
      row.put("label", label);
    }
    if (Boolean.TRUE.equals(raw.get("required")) || "true".equals(textOf(raw.get("required")))) {
      row.put("required", true);
    } else {
      row.put("required", false);
    }
    Object fallback = raw.get("default");
    if (fallback instanceof String text && text.isBlank()) fallback = null;
    if (fallback != null) {
      if (multi && !(fallback instanceof List<?>)) fallback = List.of(fallback);
      checkDefault(type, multi, fallback);
      row.put("default", fallback);
    }
    String placeholder = textOf(raw.get("placeholder"));
    if (placeholder != null && placeholder.length() <= 200) row.put("placeholder", placeholder);
    Map<String, Object> source = source(raw.get("options_source"), type);
    if ("entity".equals(type) && (source == null || !"query".equals(source.get("kind")))) {
      throw new QueryProblem("BAD_PARAM_TYPE", "An entity parameter needs a source question");
    }
    if (source != null) row.put("options_source", source);
    return row;
  }

  private static boolean multi(String type, String selection) {
    if ("select_multi".equals(type)) return true;
    if (selection == null) return false;
    if ("multi".equals(selection)) return true;
    if ("single".equals(selection)) return false;
    throw new QueryProblem("BAD_PARAM_TYPE", "selection_mode must be single or multi");
  }

  private static void checkDefault(String type, boolean multi, Object value) {
    if (multi) {
      if (!(value instanceof List<?> list)) {
        throw new QueryProblem("BAD_PARAM_TYPE", "A multi-select default must be a list");
      }
      for (Object item : list) checkScalar(type.equals("select_multi") ? "text" : type, item);
      return;
    }
    checkScalar(type, value);
  }

  private static void checkScalar(String type, Object value) {
    if (value == null) return;
    try {
      switch (type) {
        case "number" -> new BigDecimal(String.valueOf(value));
        case "boolean" -> {
          if (!(value instanceof Boolean) && !"true".equalsIgnoreCase(String.valueOf(value)) && !"false".equalsIgnoreCase(String.valueOf(value))) {
            throw new QueryProblem("BAD_PARAM_TYPE", "Boolean default must be true or false");
          }
        }
        case "date" -> {
          String text = String.valueOf(value);
          if (!text.toLowerCase(Locale.ROOT).startsWith("preset:")) LocalDate.parse(text);
        }
        case "date_range" -> {
          if (!(value instanceof Map<?, ?>) && !(value instanceof String)) {
            throw new QueryProblem("BAD_PARAM_TYPE", "Date range default is invalid");
          }
        }
        default -> {
          if (value instanceof Map<?, ?> || value instanceof List<?>) {
            throw new QueryProblem("BAD_PARAM_TYPE", "Parameter default must be a single value");
          }
        }
      }
    } catch (QueryProblem ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new QueryProblem("BAD_PARAM_TYPE", "Parameter default does not match its type");
    }
  }

  private static Map<String, Object> source(Object raw, String type) {
    if (raw == null) return null;
    if (!(raw instanceof Map<?, ?> map)) {
      throw new QueryProblem("BAD_PARAM_TYPE", "options_source must be an object");
    }
    String kind = textOf(map.get("kind"));
    if (kind == null && map.get("question_id") != null) kind = "query";
    if (kind == null) return null;
    if (!Set.of("static", "column", "query").contains(kind)) {
      throw new QueryProblem("BAD_PARAM_TYPE", "Unknown parameter source");
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("kind", kind);
    if ("static".equals(kind)) {
      out.put("values", staticValues(map.get("values")));
      return out;
    }
    if ("column".equals(kind)) {
      String table = optionalIdentifier(map.get("table"), "table");
      String column = optionalIdentifier(map.get("column"), "column");
      if (table != null) out.put("table", table);
      if (column != null) out.put("column", column);
      String columnId = optionalUuid(map.get("column_id"));
      if (columnId != null) out.put("column_id", columnId);
      return out;
    }
    String questionId = optionalUuid(map.get("question_id"));
    if (questionId == null) {
      throw new QueryProblem("BAD_PARAM_TYPE", "Source question is required");
    }
    out.put("question_id", questionId);
    out.put("value_field", identifier(map.get("value_field"), "value_field"));
    out.put("display_field", identifier(map.get("display_field"), "display_field"));
    String sourceField = optionalIdentifier(map.get("source_field"), "source_field");
    if (sourceField != null) out.put("source_field", sourceField);
    if ("entity".equals(type) && sourceField == null) out.put("source_field", out.get("value_field"));
    return out;
  }

  private static List<Object> staticValues(Object raw) {
    if (!(raw instanceof List<?> list)) {
      throw new QueryProblem("BAD_PARAM_TYPE", "Static parameter values must be a list");
    }
    if (list.size() > MAX_STATIC) {
      throw new QueryProblem("BAD_PARAM_TYPE", "Too many static parameter values");
    }
    List<Object> values = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof String || item instanceof Number || item instanceof Boolean) values.add(item);
      else throw new QueryProblem("BAD_PARAM_TYPE", "Static parameter values must be plain values");
    }
    return values;
  }

  private static String identifier(Object raw, String label) {
    String text = textOf(raw);
    if (text == null || !IDENT.matcher(text).matches()) {
      throw new QueryProblem("BAD_PARAM_TYPE", label + " must be a plain name");
    }
    return text;
  }

  private static String optionalIdentifier(Object raw, String label) {
    if (raw == null || String.valueOf(raw).isBlank()) return null;
    return identifier(raw, label);
  }

  private static String optionalUuid(Object raw) {
    String text = textOf(raw);
    if (text == null) return null;
    try {
      return UUID.fromString(text).toString();
    } catch (IllegalArgumentException ex) {
      throw new QueryProblem("BAD_PARAM_TYPE", "Question id is invalid");
    }
  }

  private static String textOf(Object raw) {
    if (raw == null) return null;
    String text = String.valueOf(raw).trim();
    return text.isEmpty() ? null : text;
  }
}

package com.landata.query.infrastructure.sql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class UserAttributes {
  private static final Set<String> NAMES = Set.of("username", "user_name", "branches", "branch", "branch_ids");
  private static final Set<String> BRANCHES = Set.of("branches", "branch", "branch_ids");

  private UserAttributes() {}

  public static boolean isAttribute(String name) {
    return NAMES.contains(canonical(name));
  }

  public static boolean isBranches(String name) {
    return BRANCHES.contains(canonical(name));
  }

  public static String type(String name) {
    return isBranches(name) ? "select_multi" : "text";
  }

  public static String canonical(String name) {
    if (name == null) return "";
    String key = name.trim();
    if (key.startsWith("@")) key = key.substring(1);
    return key.toLowerCase(Locale.ROOT);
  }

  public static Map<String, Object> values(String username, List<Integer> branches) {
    String user = username == null || username.isBlank() ? null : username.trim();
    List<Integer> ids = integers(branches);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("username", user);
    out.put("user_name", user);
    out.put("branches", ids);
    out.put("branch", ids);
    out.put("branch_ids", ids);
    return out;
  }

  public static List<Integer> integers(Object raw) {
    List<Integer> ids = new ArrayList<>();
    if (raw instanceof List<?> list) {
      for (Object item : list) addInt(ids, item);
    } else {
      addInt(ids, raw);
    }
    return ids;
  }

  private static void addInt(List<Integer> ids, Object raw) {
    if (raw == null) return;
    Integer value = null;
    if (raw instanceof Number number) value = number.intValue();
    else {
      String text = String.valueOf(raw).trim();
      if (text.isEmpty()) return;
      try {
        value = Integer.valueOf(text);
      } catch (NumberFormatException ignored) {
        return;
      }
    }
    if (!ids.contains(value)) ids.add(value);
  }
}

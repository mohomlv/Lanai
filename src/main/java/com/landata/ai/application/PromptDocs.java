package com.landata.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Chart and dialect markdown from {@code docs/}. The LLM never opens files;
 * this class attaches main.md, one chart file, and only the connected dialect.
 */
@Component
public class PromptDocs {
  private static final Map<String, String> ALIASES = Map.ofEntries(
      Map.entry("row", "bar"),
      Map.entry("donut", "pie"),
      Map.entry("number", "scalar"),
      Map.entry("kpi", "scalar"),
      Map.entry("horizontal_bar", "bar"),
      Map.entry("horizontal-bar", "bar"),
      Map.entry("stacked-bar", "stacked_bar"),
      Map.entry("cumulative-line", "cumulative_line"),
      Map.entry("timeseries", "line"),
      Map.entry("time_series", "line"),
      Map.entry("scalar-comparison", "scalar_comparison")
  );

  private static final List<Intent> INTENTS = List.of(
      new Intent("scalar_comparison", 8, "previous period", "vs last", "change %", "wow", "mom", "yoy"),
      new Intent("cumulative_line", 8, "running total", "cumulative"),
      new Intent("stacked_bar", 8, "stacked", "split by"),
      new Intent("histogram", 7, "histogram", "distribution", "buckets"),
      new Intent("heatmap", 7, "heatmap", "weekday", "hour of day"),
      new Intent("waterfall", 7, "waterfall"),
      new Intent("cohort", 7, "cohort", "retention"),
      new Intent("funnel", 7, "funnel", "conversion", "steps"),
      new Intent("scatter", 7, "scatter", "correlation"),
      new Intent("combo", 7, "combo", "bars and line", "volume and"),
      new Intent("gauge", 7, "gauge", "progress toward", "target"),
      new Intent("pivot", 6, "pivot", "cross tab", "crosstab"),
      new Intent("area", 6, "area chart", "area"),
      new Intent("pie", 6, "pie", "donut", "share of", "percentage", "percent of"),
      new Intent("line", 5, "line", "trend", "over time", "per day", "per month", "per week", "time series"),
      new Intent("bar", 5, "bar", "ranking", "top 10", "top 5", "top n", "compare"),
      new Intent("scalar", 4, "total", "how many", "count", "average", "sum of", "kpi"),
      new Intent("table", 3, "table", "list", "raw records", "rows")
  );

  private final ObjectMapper mapper = new ObjectMapper();
  private String main = "";
  private String dialects = "";
  private final Map<String, String> chartFiles = new LinkedHashMap<>();
  private final Map<String, String> chartBodies = new LinkedHashMap<>();

  @PostConstruct
  void load() throws IOException {
    main = read("docs/main.md");
    dialects = read("docs/dialects.md");
    JsonNode root = mapper.readTree(read("docs/charts/index.json"));
    for (JsonNode item : root.path("charts")) {
      String type = item.path("chart_type").asText();
      String file = item.path("file").asText();
      if (type.isBlank() || file.isBlank()) continue;
      chartFiles.put(type, file);
      String path = file.startsWith("charts/") ? "docs/" + file : "docs/charts/" + file;
      chartBodies.put(type, read(path));
    }
  }

  public String main() {
    return main;
  }

  public String resolveChart(String requested, String prompt) {
    String chosen = canonical(requested);
    if (chosen != null && !"table".equals(chosen)) return chosen;
    String fromPrompt = fromPrompt(prompt);
    if (fromPrompt != null) return fromPrompt;
    return chosen == null ? "table" : chosen;
  }

  public boolean userPinnedChart(String requested) {
    String chosen = canonical(requested);
    return chosen != null && !"table".equals(chosen);
  }

  public String chartMarkdown(String chartType) {
    String type = canonical(chartType);
    if (type == null) type = "table";
    String body = chartBodies.get(type);
    if (body == null || body.isBlank()) body = chartBodies.getOrDefault("table", "");
    String file = chartFiles.getOrDefault(type, "charts/" + type + ".md");
    return "# Chart file already loaded: " + file + "\nUse this chart_type: `" + type + "`.\nKeep its output columns exactly.\n\n" + body;
  }

  public String dialectSlice(String datasourceType) {
    DialectColumn column = dialectColumn(datasourceType);
    StringBuilder out = new StringBuilder();
    out.append("# Dialect tokens for ").append(column.label()).append(" (database_type=").append(column.type()).append(")\n\n");
    out.append("Replace every `<<TOKEN>>` (inner tokens first). No `<<` or `>>` may remain.\n\n");
    out.append("| Token | ").append(column.label()).append(" |\n|---|---|\n");
    for (String[] row : tokenRows()) {
      if (column.index() >= row.length) continue;
      out.append("| ").append(row[0]).append(" | ").append(row[column.index()]).append(" |\n");
    }
    out.append('\n');
    String notes = dialectNotes(column);
    if (!notes.isBlank()) {
      out.append("## Notes\n").append(notes).append('\n');
    }
    if ("sqlserver".equals(column.type())) {
      out.append("""
          ## App safety for SQL Server
          COUNT must be COUNT_BIG. SUM of an int, smallint, or tinyint column must be SUM(CAST(column AS bigint)).
          Never CAST or CONVERT a value to int. Use DATEDIFF_BIG instead of DATEDIFF for second or millisecond.
          Unicode (Persian, Arabic) text literals need the N prefix.
          """);
    }
    if ("oracle".equals(column.type())) {
      out.append("## App notes for Oracle\nNever write LIMIT. Use FETCH FIRST n ROWS ONLY only when the chart file requires Top N.\n");
    }
    return out.toString();
  }

  public String systemPrompt(String datasourceType) {
    return """
        The extra files for this request are already attached in the user message
        (dialect tokens for this database, and one chart file). Do not ask to open other files.

        %s

        ## App notes (these win if they conflict with the files above)
        - database_type is %s
        - Reply with JSON:
        ```json
        { "chart_type": "line", "sql": "SELECT ...", "assumptions": "" }
        ```
        You may also wrap the SQL in a ```sql fence. One read-only SELECT / WITH ... SELECT only.
        - Use only tables and columns from the attached schema docs. Do not invent names.
        - The server already caps how many rows are returned. Do not add LIMIT/TOP 1000 unless the chart file requires a small Top N for categories.
        - Ask has no dashboard filters. Do not emit `[[ ]]` or `{{name}}`. If the user named a concrete filter, write a literal.
        - Replace every `<<TOKEN>>` with this database's SQL. No `<<` or `>>` may remain.
        - Line / area / combo charts must return columns named date, p1, and p1_title. Quote date (`[date]` on SQL Server).
        - If schema_docs include a query_guide, follow its chart type, output columns, and SQL shape.
        """.formatted(main, dialectColumn(datasourceType).type());
  }

  public String userMessage(String datasourceType, String chartType, String requested, String prompt, String schemaDocs) {
    String type = resolveChart(requested, prompt);
    String pinned = userPinnedChart(requested)
        ? "The user picked this chart. Do not switch types."
        : "This chart was chosen from the request. Switch only if section 3 of main.md is a clearly better fit, and say so in assumptions.";
    return """
        database_type: %s
        chart_type: %s
        %s

        <dialect>
        %s
        </dialect>

        <chart>
        %s
        </chart>

        <schema_docs>
        %s
        </schema_docs>

        user_request:
        %s
        """.formatted(
        dialectColumn(datasourceType).type(),
        type,
        pinned,
        dialectSlice(datasourceType),
        chartMarkdown(type),
        schemaDocs == null ? "" : schemaDocs,
        prompt == null ? "" : prompt
    );
  }

  String canonical(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String key = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    key = ALIASES.getOrDefault(key, key);
    return chartBodies.containsKey(key) ? key : null;
  }

  private String fromPrompt(String prompt) {
    if (prompt == null || prompt.isBlank()) return null;
    String text = prompt.toLowerCase(Locale.ROOT);
    String named = null;
    int namedScore = 0;
    for (String type : chartBodies.keySet()) {
      String needle = type.replace('_', ' ');
      if (text.contains(type) || text.contains(needle) || text.contains(type.replace('_', '-'))) {
        int score = 10;
        if (score > namedScore) {
          namedScore = score;
          named = type;
        }
      }
    }
    if (named != null) return named;
    String best = null;
    int bestScore = 0;
    for (Intent intent : INTENTS) {
      if (!chartBodies.containsKey(intent.type())) continue;
      for (String word : intent.words()) {
        if (text.contains(word) && intent.score() > bestScore) {
          bestScore = intent.score();
          best = intent.type();
        }
      }
    }
    return best;
  }

  private List<String[]> tokenRows() {
    List<String[]> rows = new ArrayList<>();
    boolean inTable = false;
    for (String line : dialects.split("\n")) {
      String trimmed = line.trim();
      if (!trimmed.startsWith("|")) {
        if (inTable) break;
        continue;
      }
      List<String> cells = cells(trimmed);
      if (cells.isEmpty()) continue;
      if (cells.get(0).equalsIgnoreCase("Token")) {
        inTable = true;
        continue;
      }
      if (!inTable) continue;
      if (cells.get(0).replace("-", "").isBlank()) continue;
      rows.add(cells.toArray(String[]::new));
    }
    return rows;
  }

  private String dialectNotes(DialectColumn column) {
    String needle = switch (column.type()) {
      case "sqlserver" -> "**SQL Server";
      case "postgresql" -> "**PostgreSQL";
      case "mysql" -> "**MySQL";
      case "oracle" -> "**Oracle";
      case "sqlite" -> "**SQLite";
      case "bigquery" -> "**BigQuery";
      case "snowflake" -> "**Snowflake";
      default -> "**PostgreSQL";
    };
    StringBuilder out = new StringBuilder();
    boolean inNotes = false;
    for (String line : dialects.split("\n")) {
      if (line.startsWith("## Dialect notes")) {
        inNotes = true;
        continue;
      }
      if (inNotes && line.startsWith("## ")) break;
      if (inNotes && line.contains(needle)) out.append(line.strip()).append('\n');
    }
    return out.toString();
  }

  private static List<String> cells(String line) {
    String body = line;
    if (body.startsWith("|")) body = body.substring(1);
    if (body.endsWith("|")) body = body.substring(0, body.length() - 1);
    List<String> cells = new ArrayList<>();
    for (String cell : body.split("\\|", -1)) cells.add(cell.trim());
    return cells;
  }

  static DialectColumn dialectColumn(String datasourceType) {
    String type = datasourceType == null ? "" : datasourceType.toLowerCase(Locale.ROOT);
    return switch (type) {
      case "sqlserver" -> new DialectColumn("sqlserver", "SQL Server", 1);
      case "postgres", "postgresql" -> new DialectColumn("postgresql", "PostgreSQL", 2);
      case "mysql", "mariadb" -> new DialectColumn("mysql", "MySQL / MariaDB", 3);
      case "oracle" -> new DialectColumn("oracle", "Oracle", 4);
      case "sqlite" -> new DialectColumn("sqlite", "SQLite", 5);
      case "bigquery" -> new DialectColumn("bigquery", "BigQuery", 6);
      case "snowflake" -> new DialectColumn("snowflake", "Snowflake", 7);
      default -> new DialectColumn(type.isBlank() ? "postgresql" : type, "PostgreSQL", 2);
    };
  }

  private static String read(String path) throws IOException {
    return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
  }

  record DialectColumn(String type, String label, int index) {}

  private record Intent(String type, int score, String... words) {}
}

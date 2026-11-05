export type ParamValue = string | string[];

export type ParameterSource = {
  kind?: string;
  question_id?: string;
  source_field?: string;
  display_field?: string;
  value_field?: string;
  values?: unknown[];
};

export type SqlParam = {
  name: string;
  type: string;
  label?: string;
  required?: boolean;
  default?: unknown;
  selection_mode?: "single" | "multi";
  options_source?: ParameterSource;
};

const PLACEHOLDER = /\{\{\s*([A-Za-z_][A-Za-z0-9_]*)(?:\.([A-Za-z_][A-Za-z0-9_]*))?\s*\}\}/g;

export function detectSqlParams(sql: string): SqlParam[] {
  const found = new Map<string, SqlParam>();
  if (!sql) return [];
  for (const match of sql.matchAll(PLACEHOLDER)) {
    const name = match[1];
    const part = match[2];
    const current = found.get(name);
    if (part && (part === "start" || part === "end" || part === "end_exclusive")) {
      found.set(name, { name, type: "date_range", label: current?.label ?? name });
    } else if (!current) {
      found.set(name, { name, type: guessType(name), label: name });
    }
  }
  return [...found.values()];
}

export function mergeSqlParams(detected: SqlParam[], existing: SqlParam[]): SqlParam[] {
  const merged = detected.map((item) => {
    const previous = existing.find((row) => row.name === item.name);
    if (!previous) return item;
    return {
      ...previous,
      ...item,
      type: item.type === "date_range" ? "date_range" : previous.type || item.type,
      label: previous.label || item.label,
      selection_mode: previous.selection_mode ?? item.selection_mode,
      default: previous.default,
      required: previous.required,
      options_source: previous.options_source,
    };
  });
  const extras = existing.filter((row) => !merged.some((item) => item.name === row.name));
  return [...merged, ...extras];
}

function guessType(name: string): string {
  const value = name.toLowerCase();
  if (value.includes("period") || value.includes("range") || value.endsWith("_from") || value.endsWith("_to")) {
    return "date_range";
  }
  if (value.includes("date") || value.includes("day") || value === "term") return "date";
  if (/(^id$|_id$|count|amount|qty|year)$/.test(value)) return "number";
  return "text";
}

export type CatalogName = { text: string; kind: string; detail?: string; schema?: string; table?: string };
export type SqlClause = "select" | "from" | "where";

export const USER_ATTRIBUTES: CatalogName[] = [
  { text: "@username", kind: "User", detail: "Signed-in username" },
  { text: "@branches", kind: "User", detail: "Integer branch ids for this user" },
];

const SQL_WORD = /^(select|from|join|inner|left|right|full|cross|outer|where|on|and|or|group|order|by|having|as|with|into)$/i;

export function sqlClause(beforeCursor: string): SqlClause {
  const text = (beforeCursor ?? "").replace(/--[^\n]*/g, " ").replace(/\/\*[\s\S]*?\*\//g, " ");
  let last = "select";
  for (const match of text.matchAll(/\b(select|from|join|inner|left|right|full|cross|where|on|having|group|order)\b/gi)) {
    last = match[1].toLowerCase();
  }
  if (last === "from" || last === "join" || last === "inner" || last === "left" || last === "right" || last === "full" || last === "cross") {
    return "from";
  }
  if (last === "where" || last === "on" || last === "having") return "where";
  return "select";
}

function kindName(item: CatalogName): string {
  return item.kind.split("·")[0].trim().toLowerCase();
}

function allowedForClause(item: CatalogName, clause: SqlClause, userToken: boolean): boolean {
  const kind = kindName(item);
  if (userToken) return kind === "user";
  if (kind === "user") return false;
  if (clause === "from") return kind === "table" || kind === "schema";
  return kind === "column" || kind === "table";
}

function matchScore(item: CatalogName, leaf: string, prefix: string): number {
  const text = item.text.toLowerCase();
  const parts = text.split(".");
  if (prefix) {
    if (text.startsWith(prefix + leaf)) return 0;
    if (leaf && parts[parts.length - 1]?.includes(leaf)) return 4;
    return 99;
  }
  if (!leaf) return kindName(item) === "table" ? 1 : 8;
  if (text === leaf || parts.includes(leaf)) return 0;
  if (text.startsWith(leaf) || parts.some((part) => part.startsWith(leaf))) return 1;
  if (parts.some((part) => part.includes(leaf)) || text.includes(leaf)) return 3;
  return 99;
}

export function filterCatalogNames(names: CatalogName[], token: string, clause: SqlClause = "select"): CatalogName[] {
  const needle = SQL_WORD.test(token) ? "" : token.toLowerCase();
  const userToken = token.startsWith("@") || needle.startsWith("@");
  const dot = userToken ? -1 : needle.lastIndexOf(".");
  const prefix = dot >= 0 ? needle.slice(0, dot + 1) : "";
  const leaf = (dot >= 0 ? needle.slice(dot + 1) : needle).replace(/^@/, "");
  const matched = names
    .filter((item) => allowedForClause(item, clause, userToken))
    .map((item) => ({ item, score: userToken
      ? (item.text.toLowerCase().startsWith(needle) ? 0 : item.text.toLowerCase().includes(leaf) ? 2 : 99)
      : matchScore(item, leaf, prefix) }))
    .filter((row) => row.score < 99)
    .sort((a, b) => a.score - b.score || a.item.text.length - b.item.text.length);
  const seen = new Set<string>();
  const unique: CatalogName[] = [];
  for (const row of matched) {
    const key = row.item.kind + ":" + row.item.text.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    unique.push(row.item);
    if (unique.length >= 60) break;
  }
  return unique;
}

export function insertCatalogName(token: string, name: string): string {
  const usable = SQL_WORD.test(token) ? "" : token;
  const dot = usable.lastIndexOf(".");
  if (dot >= 0 && !name.toLowerCase().startsWith(usable.slice(0, dot + 1).toLowerCase())) {
    return usable.slice(0, dot + 1) + name.slice(name.lastIndexOf(".") + 1);
  }
  return name;
}

export function extractSql(text: string): string | null {
  const fence = text.match(/```(?:sql)?\s*([\s\S]*?)```/i);
  if (fence?.[1]?.trim()) return fence[1].trim();
  const select = text.match(/\b(select\b[\s\S]+)$/i);
  if (select?.[1]?.trim()) return select[1].trim().replace(/;+\s*$/, "");
  return null;
}

# QuerySpec, Parameters and Dashboard Mapping

## 1. QuerySpec v1 (visual builder / `question_version.query_spec`)
```json
{
  "version": 1,
  "source": { "table": "public.orders", "alias": "o" },
  "joins": [
    { "type": "left", "table": "public.customers", "alias": "c",
      "on": [ { "left": "o.customer_id", "op": "=", "right": "c.id" } ] }
  ],
  "select": [
    { "alias": "month",   "expr": { "date_bucket": { "column": "o.created_at", "grain": "month" } } },
    { "alias": "region",  "expr": { "column": "c.region" } },
    { "alias": "revenue", "expr": { "agg": "sum", "column": "o.total" } }
  ],
  "filters": { "and": [
    { "column": "o.created_at", "op": "between", "value": { "param": "period" } },
    { "column": "o.status",     "op": "in",      "value": { "param": "status" } },
    { "or": [ { "column": "c.vip", "op": "=", "value": true }, { "column": "o.total", "op": ">", "value": 1000 } ] }
  ] },
  "group_by": [ "month", "region" ],
  "order_by": [ { "ref": "month", "dir": "asc" } ],
  "limit": 1000,
  "parameters": [
    { "name": "period", "type": "date_range", "label": "Period", "required": true,
      "default": { "start": "2026-01-01", "end": "2026-12-31" } },
    { "name": "status", "type": "select_multi", "label": "Status",
      "options_source": { "kind": "static", "values": ["paid", "open", "refunded"] }, "default": ["paid"] }
  ]
}
```

### 1.1 Field reference
| Field | Rules |
|---|---|
| `version` | integer, currently `1` |
| `source.table` | `schema.table`, must exist and be visible in the catalog |
| `joins[]` | `type`: `inner`\|`left`\|`right`\|`full` (full only where dialect supports); `on[]`: equality/comparison between qualified columns; suggested from FKs |
| `select[]` | `alias` (required, `[a-z_][a-z0-9_]*`), `expr` one of: `{column}`, `{agg, column?, distinct?}`, `{date_bucket:{column,grain}}`, `{arith:{op:+\|-\|*\|/, left:expr, right:expr}}`, `{literal}`, `{case:{when:[{filter,then}],else}}` |
| `agg` | `count`, `count_distinct`, `sum`, `avg`, `min`, `max` (`column` omitted for `count(*)`) |
| `grain` | `day`, `week`, `month`, `quarter`, `year`, `hour` |
| `filters` | tree: `{and:[…]}`, `{or:[…]}`, `{not:{…}}`, or leaf `{column, op, value}` |
| `group_by[]` | select aliases or qualified columns. The compiler **expands aliases to the underlying expression** (SQL Server and Oracle do not allow aliases in `GROUP BY`) |
| `order_by[]` | `{ref: alias|column, dir}` |
| `limit` | ≤ datasource `max_rows` |
| `parameters[]` | see §3 |

### 1.2 Operators
`=`, `!=`, `<`, `<=`, `>`, `>=`, `between`, `in`, `not_in`, `contains`, `not_contains`, `starts_with`, `ends_with` (case-insensitive; `%`/`_` in values are escaped), `is_null`, `not_null`, `is_empty`, `not_empty`.
A leaf `value` is a literal, a list, or `{ "param": "<name>" }`. Literals are **still bound** as `?`.

### 1.3 Validation
Every table/column resolves against the catalog (`visible=true`); aliases unique; aggregates + non-aggregates require `group_by` coverage (validator reports `MISSING_GROUP_BY`); types of operator operands are compatible; params referenced are declared.

### 1.4 Compilation example (same spec, two dialects)
PostgreSQL:
```sql
SELECT date_trunc('month', "o"."created_at") AS "month", "c"."region" AS "region", SUM("o"."total") AS "revenue"
FROM "public"."orders" AS "o"
LEFT JOIN "public"."customers" AS "c" ON "o"."customer_id" = "c"."id"
WHERE "o"."created_at" >= ? AND "o"."created_at" < ? AND "o"."status" IN (?, ?)
  AND ("c"."vip" = ? OR "o"."total" > ?)
GROUP BY date_trunc('month', "o"."created_at"), "c"."region"
ORDER BY "month" ASC
LIMIT 1000
```
SQL Server:
```sql
SELECT TOP (1000) DATEADD(month, DATEDIFF(month, 0, [o].[created_at]), 0) AS [month], [c].[region] AS [region], SUM([o].[total]) AS [revenue]
FROM [public].[orders] AS [o]
LEFT JOIN [public].[customers] AS [c] ON [o].[customer_id] = [c].[id]
WHERE [o].[created_at] >= ? AND [o].[created_at] < ? AND [o].[status] IN (?, ?)
  AND ([c].[vip] = ? OR [o].[total] > ?)
GROUP BY DATEADD(month, DATEDIFF(month, 0, [o].[created_at]), 0), [c].[region]
ORDER BY [month] ASC
```
(`TOP` is used when there is no `OFFSET`; with `ORDER BY` it is valid. `between` on a `date_range` param compiles to `>= start AND < end + 1 day`.)

## 2. Parameter types
| Type | Default shape | Widget | Binds as |
|---|---|---|---|
| `text` | `"abc"` | text input, or a list when `selection_mode` is `multi` | 1 × string, or N × string |
| `number` | `42` / `3.14` | number input, or a list when `selection_mode` is `multi` | 1 × long/decimal, or N |
| `date` | `"2026-03-01"` | date picker (+ presets: today, yesterday) | 1 × `java.sql.Date` |
| `date_range` | `{"start","end"}` or `{"preset":"last_30_days"}` | range picker with presets | 2 × date (`start`, `end`) |
| `select` | `"paid"` | dropdown | 1 × typed by option type |
| `select_multi` | `["paid","open"]` | multi dropdown | N × typed (IN list) |
| `boolean` | `true` | toggle | 1 × boolean (SQL Server: bit) |
| `entity` | `"12"` or `["12","15"]` | single or multi list from another question | 1 value, or N values (IN list) |

`selection_mode` is `single` or `multi`. `select_multi` is always multi. `date_range` is always single. Multi values are a JSON list and expand to `IN (?, ?, …)`.

Presets (`date_range`/`date`): `today`, `yesterday`, `last_7_days`, `last_30_days`, `last_90_days`, `this_week`, `this_month`, `last_month`, `this_quarter`, `this_year`, `last_year`. Resolved server-side using the user's timezone.

### 2.1 Declaration (`parameters[]` item)
```json
{
  "name": "region",                     // [a-z_][a-z0-9_]*, unique per question
  "type": "select_multi",
  "label": "Region",
  "required": false,
  "default": ["EU"],
  "placeholder": "All regions",
  "selection_mode": "multi",
  "options_source": { "kind": "static", "values": ["EU","US","APAC"] }
}
```
`options_source.kind`: `static` (values), `column` (`{table, column}` → distinct values cached in `parameter_value_cache`, max 500), `query` (`{question_id, value_field, display_field, source_field}`).

A `query` source must be a saved question with `is_parameter = true`. `display_field` is the label shown in the list. `value_field` is the value bound into SQL. `source_field` records which source column supplies that value; it defaults to `value_field`. All three names must be listed in that question's `parameter_fields`. The caller's membership role must have a row in `role_parameter_question` for that question id. The check runs when the parameter is saved, when its option list is loaded, and when that parameter question itself is executed. Empty allowlist means the role cannot use any parameter question.

JSON Schema for a declaration lives at `src/main/resources/schemas/parameter.schema.json`; `required` + no `default` means the card shows "needs input".

## 3. Native SQL syntax
```sql
SELECT date_trunc('month', o.created_at) AS month, SUM(o.total) AS revenue
FROM public.orders o
WHERE o.created_at >= {{period.start}} AND o.created_at < {{period.end}}
[[ AND o.status IN ({{status}}) ]]
[[ AND o.region = {{region}} ]]
GROUP BY 1 ORDER BY 1
```
Rules:
1. `{{name}}` is a value placeholder; `{{name.start}}`, `{{name.end}}` (inclusive) and `{{name.end_exclusive}}` (= end + 1 day, computed by the server) address the parts of a `date_range`. Using `{{period}}` alone for a `date_range` is an error. For timestamp columns always filter `>= {{p.start}} AND < {{p.end_exclusive}}`.
2. `@username` and `@branches` (also `@user_name`, `@branch`, `@branch_ids`) are **user attributes**, not request parameters. They expand to `{{@name}}` and bind from the signed-in membership. `@branches` is an integer IN-list. The caller cannot override them. Use them inside `[[ … ]]` when the attribute may be empty.
3. `[[ … ]]` is an **optional block**: removed entirely when every parameter or user attribute inside it is empty (null/blank/empty list); rendered otherwise. Blocks cannot be nested. If an empty user attribute is left outside `[[ ]]`, rendering fails with `NEEDS_INPUT`.
3. Placeholders are allowed only in **value positions** (comparison right-hand side, `IN (…)`, `BETWEEN … AND …`, `LIKE`). Not allowed in identifiers, `LIMIT/TOP` counts, function names, or inside string literals/comments — the parser rejects them.
4. A parameter used twice binds twice (positional `?`, in order of appearance).
5. `select_multi` expands to `IN (?, ?, …)`; empty + required → error; empty + optional → block removed. Max elements = `Dialect.maxBindParameters()` minus other binds.
6. Declared-but-unused and used-but-undeclared parameters are errors (`UNUSED_PARAM`, `UNDECLARED_PARAM`) — these map to the AI validation codes.
7. Parsing order: `ParameterParser` → rendered SQL with `?` → **JSqlParser validation on the rendered SQL** (with dummy values for typing) → execution.

## 4. Dashboard parameters and mapping
### 4.1 Declaration
`dashboard_parameter` rows: `slug`, `label`, `type`, `default_value`, `required`, `options_source`.
### 4.2 Mapping (`dashboard_card.param_mappings`)
```json
[
  { "dashboard_param": "date_range", "question_param": "period" },
  { "dashboard_param": "region",     "question_param": "region" }
]
```
Compatibility (dashboard → question): same type; `date_range` → `date_range`; `date` → `date`; `select` → `select`/`text`; `select_multi` → `select_multi`; `text` → `text`; `number` → `number`; `boolean` → `boolean`. A `select` can feed a `select_multi` (wrapped in a list). Incompatible mappings are rejected on save.
### 4.3 Resolution algorithm (per card)
```
values = {}
for p in card.question.parameters: values[p.name] = p.default
for m in card.param_mappings:
    v = dashboardValues[m.dashboard_param]            # URL value, else dashboard default
    if v is not empty: values[m.question_param] = coerce(v, targetType)
for p in card.question.parameters:
    if p.required and empty(values[p.name]): card.state = NEEDS_INPUT
resolve presets with user timezone; execute with QueryExecutor(source='dashboard')
```
Empty dashboard value → the question's own default applies (so clearing a filter shows defaults, not "all"); an explicit "All" option on selects maps to *empty*, which removes optional blocks.
### 4.4 URL format
`/dashboards/{id}?period=2026-01-01~2026-03-31&region=EU,US&status=paid` — dates ISO, ranges `start~end`, lists comma-separated and URL-encoded, presets as `period=preset:last_30_days`. Unknown params are ignored.
### 4.5 Auto-mapping (AI dashboards, copy-paste cards)
When a card is added or an AI dashboard is assembled, mappings are created automatically for each dashboard parameter whose `slug` equals a question parameter `name` and whose types are compatible; the UI shows unmapped dashboard parameters per card with a "Connect…" action.

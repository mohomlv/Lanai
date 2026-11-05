# Schema Doc Template — one table

The Table Documenter must produce **exactly** this structure. Code parses it (`TableDocValidator`), chunks it by section, and embeds it. Keep prose short, factual and grounded in the supplied metadata; write `unknown` instead of guessing.

````markdown
---
table: <schema.table>                     # exactly as requested
type: <table|view>
purpose: <one sentence: what business thing a row represents and why it exists>
grain: <one row per …>
primary_key: [<col>, …]
tags: [<business-area>, <transactional|reference|log|bridge|aggregate>]
related_tables: [<schema.table>, …]       # only tables from the provided list
time_columns: [<col>, …]                  # best columns for time filtering/bucketing
key_measures: [<col>, …]                  # numeric columns commonly aggregated
confidence: <high|medium|low>
---

# <schema.table>

## Summary
2–4 sentences: what it stores, who creates rows (system/user/import), lifecycle, volume class (small/medium/large from the row estimate).

## Columns
| Column | Type | Meaning | Notes |
|---|---|---|---|
| `id` | bigint | Order identifier | PK |
| `status` | text | Order state | values: `paid`, `open`, `refunded` (only if samples were provided) |

One row per column, in ordinal order. First cell = exact column name in backticks. Mark PII columns as `PII — do not display samples`. Mention units/currency/timezone when evident.

## Relationships
- `orders.customer_id` → `customers.id` (many orders per customer). Join: `JOIN customers c ON c.id = orders.customer_id`
- Inferred relationships are labelled `(inferred)`.

## Common questions
- 3–5 business questions this table can answer, phrased the way a business user would ask.

## Query tips and caveats
- Filtering/bucketing advice (which date column to use), soft-delete flags, status values to include/exclude for "real" figures, duplicate risks, nullable traps.

## Open questions for the owner
- Things that could not be determined from the metadata (empty list if none).
````

## Validation rules (enforced)
1. Front matter is valid YAML; `table` matches; `related_tables ⊆` provided table list.
2. Every row in `## Columns` names an existing column; every catalog column appears (missing ones are appended by code as a stub row).
3. No sections other than the six above; ≤ 6000 characters.
4. No instructions or commentary outside the document; no code fences around the whole document in the stored version.

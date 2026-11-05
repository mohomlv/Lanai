# Chart: bar (also horizontal bar and Top N)

**Use when:** compare categories, rank things, "top 10 ...", "by branch / by product".

## Output contract
Same as pie so the renderer is shared.
| Column | Type | Meaning |
|---|---|---|
| `id` | int/text | category key |
| `amount` | number | bar length |
| `title` | text | category label |

One row per bar.

## Rules
- Sort by `amount DESC` unless the category is ordinal (months, age bands): then sort by the natural order.
- Cap with Top N (10-20) when categories are unbounded.
  - SQL Server: `SELECT TOP 10 ...` (no trailing limit).
  - Others: `<<LIMIT 10>>` at the very end (see [../dialects.md](../dialects.md)).
- Use `COUNT(DISTINCT ...)` if joins duplicate rows.

## Template
```sql
SELECT
    b.id                 AS id,
    COUNT(DISTINCT o.id) AS amount,
    b.name               AS title
FROM orders o
INNER JOIN branches b ON b.id = o.branch_id
WHERE 1 = 1
    [[AND o.term = {{term}}]]
    [[AND b.province_id = {{province}}]]
GROUP BY b.id, b.name
ORDER BY amount DESC
<<LIMIT 10>>;
```

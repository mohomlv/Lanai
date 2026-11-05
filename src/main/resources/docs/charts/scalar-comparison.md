# Chart: scalar_comparison (KPI vs previous period)

**Use when:** the user wants a number with change versus the previous period ("revenue this month vs last month", "growth").

## Output contract
| Column | Type | Meaning |
|---|---|---|
| `value` | number | current period |
| `previous` | number | previous period |
| `change_pct` | number | percent change (NULL if previous = 0) |
| `title` | text | label |

Exactly **1 row**.

## Rules
- Two periods of equal length, non-overlapping. Default: last 30 days vs the 30 days before.
- Use `<<PCT(cur.v - prev.v, prev.v)>>` (guards divide by zero).
- Replace tokens from [../dialects.md](../dialects.md).

## Template
```sql
WITH cur AS (
    SELECT COALESCE(SUM(o.amount), 0) AS v
    FROM orders o
    WHERE 1 = 1
        AND <<DAY(o.created_at)>> >= <<ADD_DAYS(<<TODAY>>, -30)>>
        [[AND o.branch_id = {{branch}}]]
),
prev AS (
    SELECT COALESCE(SUM(o.amount), 0) AS v
    FROM orders o
    WHERE 1 = 1
        AND <<DAY(o.created_at)>> >= <<ADD_DAYS(<<TODAY>>, -60)>>
        AND <<DAY(o.created_at)>> <  <<ADD_DAYS(<<TODAY>>, -30)>>
        [[AND o.branch_id = {{branch}}]]
)
SELECT
    cur.v  AS value,
    prev.v AS previous,
    <<PCT(cur.v - prev.v, prev.v)>> AS change_pct,
    'Revenue (last 30 days)' AS title
FROM cur, prev;
```

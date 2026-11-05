# Chart: gauge (progress toward a target)

**Use when:** the user wants progress against a goal ("how close are we to the target").

## Output contract
| Column | Type | Meaning |
|---|---|---|
| `value` | number | actual |
| `target` | number | goal |
| `title` | text | label |

Exactly **1 row**.

## Rules
- Take `target` from a table or a required `{{target}}` parameter if one exists (do not wrap it in `[[ ]]`: the column must always exist). Never invent a target; if none is available, say so in `assumptions` and use a clearly named placeholder.
- `value` is never NULL.

## Template
```sql
WITH actual AS (
    SELECT COALESCE(SUM(o.amount), 0) AS v
    FROM orders o
    WHERE 1 = 1
        [[AND o.term = {{term}}]]
        [[AND o.branch_id = {{branch}}]]
)
SELECT
    actual.v AS value,
    1000000  AS target,   -- replace with the real target (table value or required {{target}} param)
    'Revenue vs target' AS title
FROM actual;
```

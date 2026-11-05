# Chart: heatmap

**Use when:** two categorical axes and an intensity ("orders by weekday and hour", "branch x month").

## Output contract
| Column | Type | Meaning |
|---|---|---|
| `x` | number/text | column axis |
| `y` | number/text | row axis |
| `amount` | number | cell value |

One row per cell.

## Rules
- Both axes must be low-cardinality (roughly 7 x 24 or less).
- `ORDER BY x, y` is mandatory.
- Cells with no data may be omitted.

## Template (weekday x hour)
```sql
SELECT
    <<DOW(o.created_at)>>  AS x,
    <<HOUR(o.created_at)>> AS y,
    COUNT(*)               AS amount
FROM orders o
WHERE 1 = 1
    [[AND o.term = {{term}}]]
    [[AND o.branch_id = {{branch}}]]
GROUP BY <<DOW(o.created_at)>>, <<HOUR(o.created_at)>>
ORDER BY x, y;
```

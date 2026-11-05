# Chart: pivot (cross-tab)

**Use when:** a cross-tab with a small, known set of columns ("revenue per branch by term").

## Output contract
| Column | Type | Meaning |
|---|---|---|
| first column | text | row dimension (readable alias) |
| one column per value | number | readable alias per pivoted value |
| optional last column | number | total |

One row per row-dimension value.

## Rules
- Use **conditional aggregation** (portable). Do not use dialect-specific `PIVOT` syntax.
- Only pivot when the column values are known and few (up to about 12). Otherwise use [stacked-bar.md](stacked-bar.md) or [table.md](table.md).
- Aliases are double-quoted display names.

## Template
```sql
SELECT
    b.name AS "Branch",
    SUM(CASE WHEN o.term = 1 THEN o.amount ELSE 0 END) AS "Term 1",
    SUM(CASE WHEN o.term = 2 THEN o.amount ELSE 0 END) AS "Term 2",
    SUM(o.amount)                                      AS "Total"
FROM orders o
INNER JOIN branches b ON b.id = o.branch_id
WHERE 1 = 1
    [[AND b.province_id = {{province}}]]
GROUP BY b.name
ORDER BY "Total" DESC;
```

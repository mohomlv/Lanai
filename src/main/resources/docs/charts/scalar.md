# Chart: scalar (KPI number)

**Use when:** the user wants one number: total, count, average, sum.

## Output contract
| Column | Type | Meaning |
|---|---|---|
| `value` | number | the KPI (never NULL) |
| `title` | text | short label |

Exactly **1 row**.

## Rules
- Wrap sums with `COALESCE(..., 0)`.
- Use `COUNT(DISTINCT ...)` if joins can duplicate rows.
- Apply every relevant optional filter.

## Template
```sql
SELECT
    COALESCE(SUM(o.amount), 0) AS value,
    'Total revenue'            AS title
FROM orders o
INNER JOIN branches b ON b.id = o.branch_id
WHERE 1 = 1
    [[AND o.term = {{term}}]]
    [[AND b.province_id = {{province}}]]
    [[AND b.id = {{branch}}]];
```

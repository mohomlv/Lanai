# Chart: stacked_bar (also grouped bar)

**Use when:** categories split by a second dimension ("per branch, men vs women", "orders per month by status").

## Output contract
| Column | Type | Meaning |
|---|---|---|
| `category` | text | x-axis category |
| `p1`, `p2`, ... | number | one series per column |
| `p1_title`, `p2_title`, ... | text | constant label of each series (same on every row) |

One row per category.

## Rules
- Series should be mutually exclusive if the chart is stacked.
- Maximum about 5 series.
- `ORDER BY category` (or by a numeric order column for ordinal categories).

## Template
```sql
SELECT
    b.name AS category,

    COUNT(CASE WHEN c.gender = 'M'  THEN 1 END) AS p1,
    'Male'                                      AS p1_title,

    COUNT(CASE WHEN c.gender <> 'M' THEN 1 END) AS p2,
    'Female'                                    AS p2_title
FROM orders o
INNER JOIN customers c ON c.id = o.customer_id
INNER JOIN branches b  ON b.id = o.branch_id
WHERE 1 = 1
    [[AND o.term = {{term}}]]
    [[AND b.province_id = {{province}}]]
GROUP BY b.name
ORDER BY category;
```

# Chart: scatter (also bubble)

**Use when:** the relation between two numeric measures per entity ("orders vs revenue per customer").

## Output contract
| Column | Type | Meaning |
|---|---|---|
| `x` | number | x-axis measure |
| `y` | number | y-axis measure |
| `label` | text | entity name (tooltip) |
| `size` | number | optional, bubble size |

One row per point.

## Rules
- One row per entity: group by the entity key and name.
- Cap points (`TOP 500` / `<<LIMIT 500>>`).
- Omit `size` unless the user asks for a bubble chart.

## Template
```sql
SELECT
    COUNT(o.id)                AS x,
    SUM(o.amount)              AS y,
    c.name                     AS label,
    COUNT(DISTINCT o.category) AS size
FROM customers c
INNER JOIN orders o ON o.customer_id = c.id
WHERE 1 = 1
    [[AND o.term = {{term}}]]
GROUP BY c.id, c.name;
```

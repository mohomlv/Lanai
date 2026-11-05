# Chart: funnel

**Use when:** conversion through ordered steps ("registered -> ordered -> paid").

## Output contract
| Column | Type | Meaning |
|---|---|---|
| `step_order` | int | 1..n |
| `step` | text | step label |
| `amount` | number | count at that step |

One row per step.

## Rules
- Each step is a **subset** of the previous one, so amounts must be non-increasing.
- Count the same entity (e.g. distinct customers) in every step.
- `ORDER BY step_order` is mandatory.

## Template
```sql
WITH s AS (
    SELECT
        COUNT(DISTINCT c.id) AS registered,
        COUNT(DISTINCT CASE WHEN o.id IS NOT NULL THEN c.id END) AS ordered,
        COUNT(DISTINCT CASE WHEN o.status = 'paid' THEN c.id END) AS paid
    FROM customers c
    LEFT JOIN orders o ON o.customer_id = c.id
    WHERE 1 = 1
        [[AND o.term = {{term}}]]
)
SELECT 1 AS step_order, 'Registered' AS step, registered AS amount FROM s
UNION ALL
SELECT 2, 'Ordered', ordered FROM s
UNION ALL
SELECT 3, 'Paid', paid FROM s
ORDER BY step_order;
```

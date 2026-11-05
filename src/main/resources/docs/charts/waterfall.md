# Chart: waterfall

**Use when:** positive and negative contributions that build a total ("sales - refunds - fees = net").

## Output contract
| Column | Type | Meaning |
|---|---|---|
| `step_order` | int | 1..n |
| `step` | text | step label |
| `amount` | number | signed value; the last row is the total |

One row per step.

## Rules
- Negative steps are returned as negative numbers.
- The final row is the computed total (must equal the sum of the previous rows).
- `ORDER BY step_order` is mandatory.

## Template
```sql
WITH s AS (
    SELECT
        COALESCE(SUM(CASE WHEN o.type = 'sale'   THEN o.amount END), 0) AS sales,
        COALESCE(SUM(CASE WHEN o.type = 'refund' THEN o.amount END), 0) AS refunds,
        COALESCE(SUM(CASE WHEN o.type = 'fee'    THEN o.amount END), 0) AS fees
    FROM orders o
    WHERE 1 = 1
        [[AND o.term = {{term}}]]
)
SELECT 1 AS step_order, 'Sales'   AS step, sales            AS amount FROM s
UNION ALL
SELECT 2, 'Refunds', -refunds FROM s
UNION ALL
SELECT 3, 'Fees',    -fees    FROM s
UNION ALL
SELECT 4, 'Net',     sales - refunds - fees FROM s
ORDER BY step_order;
```

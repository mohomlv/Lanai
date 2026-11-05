# Chart: cumulative_line (running total)

**Use when:** "cumulative", "running total", "total so far".

## Output contract
Same as [line.md](line.md): `date`, `p1`, `p1_title` (add more series pairs if needed).

## Rules
- Aggregate per period in a CTE, then apply a window `SUM(...) OVER (ORDER BY ...)`.
- MySQL older than 8.0 (no window functions): use a correlated subquery instead.
- `ORDER BY "date"` is mandatory.

## Template
```sql
WITH daily AS (
    SELECT <<DAY(o.created_at)>> AS d, SUM(o.amount) AS day_total
    FROM orders o
    WHERE 1 = 1
        [[AND o.term = {{term}}]]
        [[AND o.branch_id = {{branch}}]]
    GROUP BY <<DAY(o.created_at)>>
)
SELECT
    d AS "date",
    SUM(day_total) OVER (ORDER BY d ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS p1,
    'Cumulative revenue' AS p1_title
FROM daily
ORDER BY "date";
```

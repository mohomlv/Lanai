# Chart: cohort (retention)

**Use when:** retention by signup or first-purchase cohort ("how many customers come back in month 1, 2, 3").

## Output contract
| Column | Type | Meaning |
|---|---|---|
| `cohort` | date | cohort start (month) |
| `period` | int | months since cohort start (0 = start) |
| `users` | number | active entities in that period |
| `retention_pct` | number | `users` / cohort size at period 0, in percent |

One row per cohort x period.

## Rules
- Cohort = the month of the entity's first event.
- Use `<<MONTHS_BETWEEN(a,b)>>` from [../dialects.md](../dialects.md) for `period`.
- This is the most dialect-sensitive chart: test it on the target database.
- `ORDER BY cohort, period` is mandatory.

## Template
```sql
WITH first_order AS (
    SELECT o.customer_id, MIN(<<MONTH(o.created_at)>>) AS cohort
    FROM orders o
    GROUP BY o.customer_id
),
activity AS (
    SELECT DISTINCT o.customer_id, <<MONTH(o.created_at)>> AS active_month
    FROM orders o
),
joined AS (
    SELECT
        f.cohort,
        a.customer_id,
        <<MONTHS_BETWEEN(f.cohort, a.active_month)>> AS period
    FROM first_order f
    INNER JOIN activity a ON a.customer_id = f.customer_id
),
sizes AS (
    SELECT cohort, COUNT(DISTINCT customer_id) AS size
    FROM joined
    WHERE period = 0
    GROUP BY cohort
)
SELECT
    j.cohort,
    j.period,
    COUNT(DISTINCT j.customer_id) AS users,
    <<PCT(COUNT(DISTINCT j.customer_id), s.size)>> AS retention_pct
FROM joined j
INNER JOIN sizes s ON s.cohort = j.cohort
GROUP BY j.cohort, j.period, s.size
ORDER BY j.cohort, j.period;
```

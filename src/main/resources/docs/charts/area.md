# Chart: area (also stacked area)

**Use when:** a trend where the series are **parts of a total** ("revenue by channel over time").

## Output contract
Identical to [line.md](line.md): `date`, `p1`, `p1_title`, `p2`, `p2_title`, ...

## Rules
- Use the line template from [line.md](line.md).
- Series must be **mutually exclusive** (no "All" series), otherwise the stack double counts.
- `ORDER BY "date"` is mandatory.

## Template
```sql
SELECT
    <<MONTH(o.created_at)>> AS "date",

    SUM(CASE WHEN o.channel = 'web'    THEN o.amount ELSE 0 END) AS p1,
    'Web'                                                       AS p1_title,

    SUM(CASE WHEN o.channel = 'mobile' THEN o.amount ELSE 0 END) AS p2,
    'Mobile'                                                    AS p2_title,

    SUM(CASE WHEN o.channel NOT IN ('web', 'mobile') THEN o.amount ELSE 0 END) AS p3,
    'Other'                                                     AS p3_title
FROM orders o
WHERE 1 = 1
    [[AND o.term = {{term}}]]
    [[AND o.branch_id = {{branch}}]]
GROUP BY <<MONTH(o.created_at)>>
ORDER BY "date";
```

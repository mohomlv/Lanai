# Chart: line (time series, one or more series)

**Use when:** trend over time ("per day", "per month", "over the term").

## Output contract
| Column | Type | Meaning |
|---|---|---|
| `date` | date | x-axis (always named `date`, quoted: it is a reserved word) |
| `p1`, `p2`, ... | number | one series per column; `p1` is the total/primary series |
| `p1_title`, `p2_title`, ... | text | constant label of each series |

One row per date.

## Rules
- `ORDER BY "date"` is mandatory (SQL Server: `ORDER BY [date]`).
- Group by the same expression you select. Write the real dialect expression (`CAST(x AS DATE)` on SQL Server), not leftover `<<DAY(x)>>`.
- Maximum about 5 series.
- Quote `date` per [../dialects.md](../dialects.md).
- Ask has no dashboard filters: omit `[[AND ... {{param}}]]`. Only add a literal WHERE if the user named a value.

## Template
```sql
SELECT
    <<DAY(o.created_at)>> AS "date",

    COUNT(*)                                    AS p1,
    'All'                                       AS p1_title,

    COUNT(CASE WHEN c.gender = 'M'  THEN 1 END) AS p2,
    'Male'                                      AS p2_title,

    COUNT(CASE WHEN c.gender <> 'M' THEN 1 END) AS p3,
    'Female'                                    AS p3_title
FROM orders o
INNER JOIN customers c ON c.id = o.customer_id
INNER JOIN branches b  ON b.id = o.branch_id
WHERE 1 = 1
    [[AND o.term = {{term}}]]
    [[AND b.id = {{branch}}]]
    [[AND b.province_id = {{province}}]]
GROUP BY <<DAY(o.created_at)>>
ORDER BY "date";
```

## Variant: first event per entity
```sql
WITH first_event AS (
    SELECT e.*, ROW_NUMBER() OVER (PARTITION BY e.entity_id ORDER BY e.id ASC) AS rn
    FROM events e
    WHERE 1 = 1
        [[AND e.term = {{term}}]]
)
SELECT <<DAY(created_at)>> AS "date", COUNT(*) AS p1, 'First events' AS p1_title
FROM first_event
WHERE rn = 1
GROUP BY <<DAY(created_at)>>
ORDER BY "date";
```

## Real-world example (SQL Server, dormitory data): first payment per request, by gender
```sql
WITH FirstFactor AS (
    SELECT
        f.id AS factorId, f.paymentDate, f.requstid AS requestId, r.term, r.jender,
        d.id AS dormitoryId, b.id AS branchId,
        ROW_NUMBER() OVER (PARTITION BY f.requstid ORDER BY f.id ASC) AS rn
    FROM Tbl_Factores f
    INNER JOIN Tbl_RezerveRequest r ON r.id = f.requstid
    INNER JOIN Tbl_Dormitories d    ON d.id = r.dormitoryid
    INNER JOIN Tbl_Branch b         ON b.id = d.b_id
    WHERE f.status = 1 AND r.status = 5 AND f.ismanual != 1
        [[AND r.term = {{term}}]]
        [[AND b.id = {{branch}}]]
        [[AND b.ProvinceId = {{province}}]]
        [[AND d.id = {{dormitory}}]]
)
SELECT
    CAST(paymentDate AS DATE) AS "date",
    COUNT(*) AS p1, N'همه' AS p1_title,
    COUNT(CASE WHEN jender = N'مرد'  THEN 1 END) AS p2, N'برادران' AS p2_title,
    COUNT(CASE WHEN jender <> N'مرد' THEN 1 END) AS p3, N'خواهران' AS p3_title
FROM FirstFactor
WHERE rn = 1
GROUP BY CAST(paymentDate AS DATE)
ORDER BY "date";
```

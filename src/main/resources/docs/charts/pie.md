# Chart: pie (also donut)

**Use when:** the user wants share of a whole, parts, percentages, distribution across a few categories.

## Output contract
| Column | Type | Meaning |
|---|---|---|
| `id` | int | 1..n, stable per slice |
| `amount` | number | slice size |
| `title` | text | slice label |

One row per slice.

## Rules
- Slices must be **mutually exclusive** and add up to the total.
- Maximum about 8 slices (group the rest as "Other").
- Pattern A (fixed slices): base CTE -> classification -> `summary` CTE with `p1..pN` -> `UNION ALL` unpivot.
- Pattern B (slices come from data): one row per category with `ROW_NUMBER()` as `id`.

## Template A: fixed slices
```sql
WITH base AS (
    SELECT o.id, COUNT(*) OVER (PARTITION BY o.customer_id) AS orders_per_customer
    FROM orders o
    INNER JOIN branches b ON b.id = o.branch_id
    WHERE 1 = 1
        [[AND o.term = {{term}}]]
        [[AND b.province_id = {{province}}]]
        [[AND b.id = {{branch}}]]
),
summary AS (
    SELECT
        COUNT(CASE WHEN orders_per_customer = 1 THEN 1 END) AS p1,
        COUNT(CASE WHEN orders_per_customer > 1 THEN 1 END) AS p2
    FROM base
)
SELECT 1 AS id, p1 AS amount, 'New customers'       AS title FROM summary
UNION ALL
SELECT 2 AS id, p2 AS amount, 'Returning customers' AS title FROM summary;
```

## Template B: slices from data
```sql
SELECT
    ROW_NUMBER() OVER (ORDER BY COUNT(*) DESC) AS id,
    COUNT(*)                                   AS amount,
    o.category                                 AS title
FROM orders o
WHERE 1 = 1
    [[AND o.term = {{term}}]]
GROUP BY o.category;
```

## Real-world example (SQL Server, dormitory data): cash vs installment
```sql
WITH request_factor AS (
    SELECT r.dormitoryId AS d_id, COUNT(f.id) AS factor_count
    FROM Tbl_RezerveRequest r
    INNER JOIN Tbl_Factores f    ON f.requstid = r.id
    INNER JOIN Tbl_Dormitories d ON r.dormitoryid = d.id
    INNER JOIN Tbl_Branch b      ON b.id = d.b_id
    WHERE 1 = 1
        AND b.id != 1111
        [[AND r.term = {{term}}]]
        [[AND d.id = {{dormitory}}]]
        [[AND b.ProvinceId = {{province}}]]
        [[AND b.id = {{branch}}]]
    GROUP BY r.id, r.dormitoryId
),
dormitory_status AS (
    SELECT d_id,
        SUM(CASE WHEN factor_count = 1 THEN 1 ELSE 0 END) AS cnt_cash,
        SUM(CASE WHEN factor_count > 1 THEN 1 ELSE 0 END) AS cnt_installment
    FROM request_factor
    GROUP BY d_id
),
summary AS (
    SELECT
        COUNT(CASE WHEN cnt_cash > 0 AND cnt_installment = 0 THEN d_id END) AS p1,
        COUNT(CASE WHEN cnt_cash = 0 AND cnt_installment > 0 THEN d_id END) AS p2,
        COUNT(CASE WHEN cnt_cash > 0 AND cnt_installment > 0 THEN d_id END) AS p3
    FROM dormitory_status
)
SELECT 1 AS id, p1 AS amount, N'نقدی'          AS title FROM summary
UNION ALL
SELECT 2 AS id, p2 AS amount, N'اقساطی'        AS title FROM summary
UNION ALL
SELECT 3 AS id, p3 AS amount, N'اقساطی و نقدی' AS title FROM summary;
```

# Chart: table

**Use when:** raw records or lists ("list the orders", "show details").

## Output contract
Any columns, with **readable aliases** (user's language, double-quoted). One row per record.

## Rules
- Always cap rows: SQL Server `SELECT TOP 1000 ...`; others `<<LIMIT 1000>>` at the end.
- Always a stable `ORDER BY` (usually newest first).
- Select only the columns the user needs. Never `SELECT *`.

## Template
```sql
SELECT
    o.id         AS "Order ID",
    b.name       AS "Branch",
    c.name       AS "Customer",
    o.amount     AS "Amount",
    o.created_at AS "Created at"
FROM orders o
INNER JOIN customers c ON c.id = o.customer_id
INNER JOIN branches b  ON b.id = o.branch_id
WHERE 1 = 1
    [[AND o.term = {{term}}]]
    [[AND b.province_id = {{province}}]]
    [[AND b.id = {{branch}}]]
ORDER BY o.created_at DESC
<<LIMIT 1000>>;
```

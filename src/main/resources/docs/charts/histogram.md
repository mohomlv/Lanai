# Chart: histogram

**Use when:** the distribution of a numeric value ("how are order amounts distributed", "age distribution").

## Output contract
| Column | Type | Meaning |
|---|---|---|
| `bucket_order` | number | numeric lower bound of the bucket (sort key) |
| `bucket` | text | label, e.g. `0-99` |
| `amount` | number | rows in the bucket |

One row per bucket.

## Rules
- Fixed-width buckets: `FLOOR(value / width)`. Pick a width that gives 8-20 buckets.
- The frontend sorts by `bucket_order`; `ORDER BY bucket_order` is mandatory.
- Build `bucket` from `bucket_order` and the width with `<<CONCAT(a,b)>>` and `<<STR(x)>>` ([../dialects.md](../dialects.md)).

## Template (width = 100)
```sql
WITH bucketed AS (
    SELECT FLOOR(o.amount / 100) AS b
    FROM orders o
    WHERE 1 = 1
        [[AND o.term = {{term}}]]
)
SELECT
    b * 100 AS bucket_order,
    <<CONCAT(<<CONCAT(<<STR(b * 100)>>, '-')>>, <<STR(b * 100 + 99)>>)>> AS bucket,
    COUNT(*) AS amount
FROM bucketed
GROUP BY b
ORDER BY bucket_order;
```

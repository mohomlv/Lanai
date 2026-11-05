# Chart: combo (bars + line)

**Use when:** a volume and a rate/average together ("orders (bars) and average amount (line) per month").

## Output contract
| Column | Type | Meaning |
|---|---|---|
| `date` | date | x-axis |
| `p1`, `p2`, ... | number | series values |
| `p1_title`, `p2_title`, ... | text | series labels |
| `p1_type`, `p2_type`, ... | text | `'bar'` or `'line'` for each series |

One row per date.

## Rules
- Put volumes on `bar`, rates/averages on `line`.
- Maximum about 4 series.
- `ORDER BY "date"` is mandatory.

## Template
```sql
SELECT
    <<MONTH(o.created_at)>> AS "date",

    COUNT(*)         AS p1,
    'Orders'         AS p1_title,
    'bar'            AS p1_type,

    AVG(o.amount)    AS p2,
    'Average amount' AS p2_title,
    'line'           AS p2_type
FROM orders o
WHERE 1 = 1
    [[AND o.term = {{term}}]]
    [[AND o.branch_id = {{branch}}]]
GROUP BY <<MONTH(o.created_at)>>
ORDER BY "date";
```

# Dialect Tokens

Chart templates use `<<TOKEN>>` placeholders. Replace each with the value for the connected database. Nested tokens are allowed, e.g. `<<ADD_DAYS(<<TODAY>>, -30)>>`.

## Token table

| Token | SQL Server | PostgreSQL | MySQL / MariaDB | Oracle | SQLite | BigQuery | Snowflake |
|---|---|---|---|---|---|---|---|
| `<<LIMIT n>>` (end of query) | *(use `SELECT TOP n`)* | `LIMIT n` | `LIMIT n` | `FETCH FIRST n ROWS ONLY` | `LIMIT n` | `LIMIT n` | `LIMIT n` |
| `<<DAY(x)>>` | `CAST(x AS DATE)` | `DATE_TRUNC('day', x)::date` | `DATE(x)` | `TRUNC(x)` | `DATE(x)` | `DATE(x)` | `DATE_TRUNC('day', x)` |
| `<<WEEK(x)>>` | `DATEADD(week, DATEDIFF(week, 0, x), 0)` | `DATE_TRUNC('week', x)::date` | `DATE_SUB(DATE(x), INTERVAL WEEKDAY(x) DAY)` | `TRUNC(x, 'IW')` | `DATE(x, 'weekday 0', '-6 days')` | `DATE_TRUNC(x, WEEK)` | `DATE_TRUNC('week', x)` |
| `<<MONTH(x)>>` | `DATEFROMPARTS(YEAR(x), MONTH(x), 1)` | `DATE_TRUNC('month', x)::date` | `DATE_FORMAT(x, '%Y-%m-01')` | `TRUNC(x, 'MM')` | `DATE(x, 'start of month')` | `DATE_TRUNC(x, MONTH)` | `DATE_TRUNC('month', x)` |
| `<<YEAR(x)>>` | `YEAR(x)` | `EXTRACT(YEAR FROM x)` | `YEAR(x)` | `EXTRACT(YEAR FROM x)` | `CAST(STRFTIME('%Y', x) AS INTEGER)` | `EXTRACT(YEAR FROM x)` | `YEAR(x)` |
| `<<HOUR(x)>>` | `DATEPART(hour, x)` | `EXTRACT(HOUR FROM x)` | `HOUR(x)` | `EXTRACT(HOUR FROM CAST(x AS TIMESTAMP))` | `CAST(STRFTIME('%H', x) AS INTEGER)` | `EXTRACT(HOUR FROM x)` | `HOUR(x)` |
| `<<DOW(x)>>` | `DATEPART(weekday, x)` | `EXTRACT(DOW FROM x)` | `DAYOFWEEK(x)` | `TO_NUMBER(TO_CHAR(x, 'D'))` | `CAST(STRFTIME('%w', x) AS INTEGER)` | `EXTRACT(DAYOFWEEK FROM x)` | `DAYOFWEEK(x)` |
| `<<TODAY>>` | `CAST(GETDATE() AS DATE)` | `CURRENT_DATE` | `CURDATE()` | `TRUNC(SYSDATE)` | `DATE('now')` | `CURRENT_DATE()` | `CURRENT_DATE` |
| `<<DAYS_BETWEEN(a,b)>>` | `DATEDIFF(day, a, b)` | `(b::date - a::date)` | `DATEDIFF(b, a)` | `(TRUNC(b) - TRUNC(a))` | `JULIANDAY(b) - JULIANDAY(a)` | `DATE_DIFF(b, a, DAY)` | `DATEDIFF(day, a, b)` |
| `<<MONTHS_BETWEEN(a,b)>>` | `DATEDIFF(month, a, b)` | `(EXTRACT(YEAR FROM b)-EXTRACT(YEAR FROM a))*12 + EXTRACT(MONTH FROM b)-EXTRACT(MONTH FROM a)` | `TIMESTAMPDIFF(MONTH, a, b)` | `MONTHS_BETWEEN(b, a)` | `(CAST(STRFTIME('%Y',b) AS INTEGER)-CAST(STRFTIME('%Y',a) AS INTEGER))*12 + CAST(STRFTIME('%m',b) AS INTEGER)-CAST(STRFTIME('%m',a) AS INTEGER)` | `DATE_DIFF(b, a, MONTH)` | `DATEDIFF(month, a, b)` |
| `<<ADD_DAYS(x,n)>>` | `DATEADD(day, n, x)` | `x + INTERVAL 'n days'` | `DATE_ADD(x, INTERVAL n DAY)` | `x + n` | `DATE(x, '+n days')` | `DATE_ADD(x, INTERVAL n DAY)` | `DATEADD(day, n, x)` |
| `<<CONCAT(a,b)>>` | `CONCAT(a, b)` | `a \|\| b` | `CONCAT(a, b)` | `a \|\| b` | `a \|\| b` | `CONCAT(a, b)` | `a \|\| b` |
| `<<STR(x)>>` | `CAST(x AS NVARCHAR(100))` | `x::text` | `CAST(x AS CHAR)` | `TO_CHAR(x)` | `CAST(x AS TEXT)` | `CAST(x AS STRING)` | `x::varchar` |
| `<<PCT(a,b)>>` (percent, 1 decimal) | `ROUND(100.0 * a / NULLIF(b, 0), 1)` | `ROUND(100.0 * a / NULLIF(b, 0), 1)` | `ROUND(100.0 * a / NULLIF(b, 0), 1)` | `ROUND(100 * a / NULLIF(b, 0), 1)` | `ROUND(100.0 * a / NULLIF(b, 0), 1)` | `ROUND(100 * SAFE_DIVIDE(a, b), 1)` | `ROUND(100.0 * a / NULLIF(b, 0), 1)` |
| `<<MEDIAN(x)>>` | `PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY x) OVER ()` | `PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY x)` | *(no built-in; window + row_number)* | `MEDIAN(x)` | *(no built-in; window + row_number)* | `APPROX_QUANTILES(x, 2)[OFFSET(1)]` | `MEDIAN(x)` |
| Quote reserved identifier (e.g. date) | `"date"` | `"date"` | `` `date` `` | `"DATE"` | `"date"` | `` `date` `` | `"DATE"` |
| Text literal | `N'text'` | `'text'` | `'text'` | `N'text'` | `'text'` | `'text'` | `'text'` |
| One-row select without table | `SELECT 1` | `SELECT 1` | `SELECT 1` | `SELECT 1 FROM DUAL` | `SELECT 1` | `SELECT 1` | `SELECT 1` |
| Window functions | yes | yes | 8.0+ only | yes | 3.25+ only | yes | yes |
| CTEs (`WITH`) | yes | yes | 8.0+ only | yes | yes | yes | yes |

## Dialect notes
- **SQL Server**: put `TOP n` right after `SELECT`. `ORDER BY` is not allowed in a CTE/subquery unless `TOP` is present. Unicode (Persian, Arabic) text literals need the `N` prefix.
- **Oracle**: no `AS` for table aliases (`FROM orders o`); column aliases may use `AS`. Unquoted identifiers are upper-case. Use `FETCH FIRST n ROWS ONLY` instead of `LIMIT`.
- **PostgreSQL**: unquoted identifiers fold to lower-case. Cast with `::`.
- **MySQL older than 8.0**: no CTEs and no window functions. Rewrite CTEs as derived tables (`FROM (...) t`) and replace `ROW_NUMBER()` with a self-join or a correlated subquery.
- **SQLite**: dates are text; use `DATE()` / `STRFTIME`. No `FULL OUTER JOIN` before 3.39.
- **BigQuery**: tables are `` `project.dataset.table` ``. Use `SAFE_DIVIDE`.
- **Snowflake**: identifiers are upper-case unless quoted.

## How to apply
1. Take the template from the chart file.
2. Replace every `<<TOKEN>>` (inner tokens first) with the value for the connected database.
3. Make sure no `<<` or `>>` remains in the final SQL.

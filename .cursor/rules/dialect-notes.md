<!-- prompt: dialect-notes@1 -->
# SQL dialect notes (inserted as `<<dialect_notes>>`; keep only the section of the datasource's dialect)

## postgres
- Quote identifiers with double quotes only when needed (mixed case, reserved words).
- Limit: `LIMIT n`. Date bucket: `date_trunc('month', col)`. Today: `CURRENT_DATE`. Now: `now()`.
- Concat: `||` or `concat()`. Cast: `col::date` or `CAST(col AS date)`. Case-insensitive match: `ILIKE`.
- Interval math: `CURRENT_DATE - INTERVAL '30 days'`. Booleans: `true/false`.
- Time series without gaps: `generate_series(start, end, interval '1 month')`.
- Write `GROUP BY` with the full expressions (portable), not ordinals.

## sqlserver
- Quote identifiers with `[brackets]`. **No `LIMIT`**: use `SELECT TOP (n) …` or `ORDER BY … OFFSET 0 ROWS FETCH NEXT n ROWS ONLY` (needs ORDER BY).
- Date bucket: `DATETRUNC(month, col)` (SQL Server 2022+) else `DATEADD(month, DATEDIFF(month, 0, col), 0)`. Today: `CAST(GETDATE() AS date)`. Now: `SYSDATETIME()`.
- Concat: `CONCAT(a, b)` or `+` (NULL-sensitive). Booleans are `bit` (1/0) — no `true/false` literals. Null default: `ISNULL`/`COALESCE`.
- Date math: `DATEADD(day, -30, CAST(GETDATE() AS date))`, `DATEDIFF(day, a, b)`.
- `GROUP BY` cannot use select aliases or ordinals — repeat the expression. Text comparison case depends on collation.
- Avoid `SELECT DISTINCT` + `ORDER BY` on expressions not in the select list.

## mysql
- Quote identifiers with backticks. Limit: `LIMIT n`. Date bucket month: `DATE_FORMAT(col, '%Y-%m-01')` (returns text) or `DATE_SUB(DATE(col), INTERVAL DAYOFMONTH(col)-1 DAY)` (date). Today: `CURDATE()`. Now: `NOW()`.
- Concat: `CONCAT()` (`||` is OR unless `PIPES_AS_CONCAT`). Interval math: `DATE_SUB(CURDATE(), INTERVAL 30 DAY)`.
- `ONLY_FULL_GROUP_BY` is usually on: every non-aggregated select expression must be in `GROUP BY`. String comparison is case-insensitive by default collation.

## mariadb
- Same as MySQL. `FETCH FIRST` is supported in recent versions but prefer `LIMIT`.

## oracle
- Identifiers are upper-case unless quoted; avoid quoting unless the catalog shows mixed case. Limit: `FETCH FIRST n ROWS ONLY` (12c+) or `ROWNUM <= n`.
- Date bucket: `TRUNC(col, 'MM')` (month), `'IW'` week, `'Q'` quarter, `'YYYY'` year. Today: `TRUNC(SYSDATE)`. Now: `SYSTIMESTAMP`.
- Concat `||`. Null default `NVL`. Empty string equals NULL. No boolean SQL type before 23c (use 1/0 or 'Y'/'N' as in the catalog).
- Date math: `SYSDATE - 30`, `ADD_MONTHS(col, -1)`. `GROUP BY` needs full expressions.

# Database Overview Template

Produced once per datasource after all table docs. Always included in query-generation context, so keep it compact (≤ 4000 chars).

````markdown
---
datasource: <name>
dialect: <postgres|sqlserver|mysql|oracle>
tables: <count>
domains: [<domain>, …]
confidence: <high|medium|low>
---

# <Datasource name> — overview

## What this database is
2–4 sentences from the owner's description and the table purposes.

## Subject areas
| Area | Tables | Notes |
|---|---|---|
| Sales | `public.orders`, `public.order_items` | transactional core |

## Core entities and how they connect
- `customers` 1—N `orders` (via `orders.customer_id`)
- Key join paths as short chains; mark `(inferred)` where no real FK exists.

## Conventions
Naming, soft deletes, timestamps/timezones, currency, status vocabularies, multi-tenant columns (if any).

## Caveats
Known data-quality traps and tables to avoid for reporting (staging/log/archive).

## Starter questions
5–8 business questions this database can answer.
````

## Validation rules
All tables named exist; join paths are backed by FKs (real or labelled inferred); `domains` and the Subject areas table agree; no content outside the template.

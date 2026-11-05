# Backend architecture

Landata is a **modular monolith** with a **layered layout inside each module**. It is not Clean Architecture / full DDD (JPA entities live under infrastructure).

## Module packages

Top-level modules under `com.landata`: `identity`, `datasource`, `knowledge`, `query`, `ai`, `dashboard`, `viz`, `admin`, `billing`, plus `shared`.

Inside each feature module use:

```
com.landata.<module>
  api/                         Controllers, problem handlers, HTTP DTOs
  application/                 Use-case services (@Service). Ports (interfaces) when swappable
  infrastructure/
    persistence/               JPA @Entity + Spring Data repositories
    jdbc/ | sql/ | llm/        Technical adapters (customer JDBC, SQL compile, HTTP LLM)
  config/                      Module Spring @Configuration when needed
```

`shared` holds cross-cutting pieces: JWT, crypto, API errors, properties. Do not put feature use cases there.

## Rules

1. **Controllers stay thin** — call application services; no JDBC or LLM HTTP in controllers.
2. **Service interface only when there is a real swap** — e.g. `LlmClient`. Do not invent `FooService` + `FooServiceImpl` for every class.
3. **Spring Data repos stay interfaces** — no pass-through repository impl that only calls `save()`.
4. **Persistence is not the domain model** — call `infrastructure.persistence` what it is. Pure domain types (if any) can live in `application` or a small `domain` package without JPA.
5. **No dump `utils` packages** — prefer named helpers next to the code that uses them (`sql`, `jdbc`, `security`).
6. **Boundaries** — prefer depending on another module’s `application` API, not its `infrastructure.persistence` entities, except where unavoidable (e.g. admin reading `QueryRunRepository`). Tighten over time with Spring Modulith.

## Customer SQL

Customer databases are never mapped with JPA. Execution goes through datasource JDBC (`PoolManager`) and query `infrastructure.sql` (`SqlCompiler`, `QueryValidator`, `NativeSql`).

## Background work

JobRunr jobs are invoked from application services (catalog sync, AI query generation). Keep job entry methods on Spring beans so JobRunr can proxy them.

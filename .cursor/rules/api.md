# REST API Surface (v1)

Base path `/api/v1`. JSON, `Authorization: Bearer <jwt>`. Errors: `application/problem+json` `{type,title,status,code,detail,traceId}`. All resources are scoped to the token's `company_id`; foreign ids return `404`. List endpoints accept `page,size` (or `cursor`) and return `{items,page,total}`.
Roles: **V** viewer · **E** editor · **A** admin · **O** owner.

## Auth and companies
| Method | Path | Purpose | Role |
|---|---|---|---|
| POST | `/auth/signup` · `/auth/login` · `/auth/refresh` · `/auth/logout` | account + tokens | public |
| POST | `/auth/verify-email` · `/auth/forgot-password` · `/auth/reset-password` | | public |
| GET/PATCH | `/me` | profile + memberships + `branch_ids` + `platform_admin`; PATCH `{username}` | any |
| GET/POST | `/companies` | list mine / create | any |
| POST | `/companies/{id}/switch` | new token for that company | member |
| GET/PATCH/DELETE | `/company` | current company | V / A / O |
| GET/PATCH/DELETE | `/company/members[/{userId}]` | list, update `{role,username,branch_ids[]}`, remove | A |
| POST/GET/DELETE | `/company/invitations[/{id}]` | invite, list, revoke | A |
| POST | `/invitations/{token}/accept` | accept invite | auth |

## Datasources and catalog
| Method | Path | Purpose | Role |
|---|---|---|---|
| GET/POST | `/datasources` | list / create (password write-only) | V / A |
| GET/PATCH/DELETE | `/datasources/{id}` | detail (no secrets) / update / delete | V / A |
| POST | `/datasources/test` · `/datasources/{id}/test` | test connection (new params / saved) | A |
| PUT | `/datasources/{id}/permissions` | per-role query/native/manage | A |
| POST | `/datasources/{id}/sync` | sync catalog (202) | A |
| GET | `/datasources/{id}/tables` | tables (filters: `visible`, `q`) | V |
| GET/PATCH | `/tables/{id}` · `/columns/{id}` | read / set `visible`, `is_pii`, `display_name`, `user_note` | V / A |
| GET/POST/DELETE | `/datasources/{id}/fks[/{fkId}]` | list / add manual / remove or confirm inferred | A |
| GET/PUT | `/datasources/{id}/sync-schedule` | cron | A |
| GET | `/datasources/{id}/values?table=&column=&q=` | distinct values for filter widgets | E |

## Knowledge (AI docs)
| Method | Path | Purpose | Role |
|---|---|---|---|
| POST | `/datasources/{id}/scrape` | start scrape job `{table_ids?}` (202) | A |
| GET | `/scrape-jobs/{id}` · `/scrape-jobs/{id}/items` | progress | A |
| POST | `/scrape-jobs/{id}/cancel` · `/scrape-job-items/{id}/retry` | | A |
| GET | `/datasources/{id}/docs` · `/docs/{id}` | list / read (`?versions=true`) | V |
| POST | `/datasources/{id}/docs` | add a user query-guide markdown `{title, markdown}` and embed it | E |
| PUT | `/docs/{id}` | user edit → new version, re-embed | E |
| DELETE | `/docs/{id}` | remove a user query-guide (and its embeddings) | E |
| POST | `/docs/{id}/approve` · `/reject` | handle `proposed` versions | A |
| GET/POST/PATCH/DELETE | `/datasources/{id}/glossary[/{id}]` | glossary | A |
| GET/POST/PATCH/DELETE | `/datasources/{id}/metrics[/{id}]` | metric definitions | A |

## Queries
| Method | Path | Purpose | Role |
|---|---|---|---|
| POST | `/queries/run` | run ad-hoc `{datasource_id, mode, query_spec|native_sql, parameters{}}` → `{run_id, columns, rows, truncated, duration_ms, sql?}` | E (V on saved) |
| POST | `/queries/compile` | QuerySpec → SQL preview (no execution) | E |
| POST | `/queries/{runId}/cancel` | cancel | E |
| POST | `/queries/validate` | static validation (parse, catalog, params) | E |
| GET/POST | `/questions` | list / create (version 1) | V / E |
| GET/PATCH/DELETE | `/questions/{id}` | meta / rename-move / archive | V / E |
| POST | `/questions/{id}/versions` | new version | E |
| GET | `/questions/{id}/versions[/{n}]` | history | V |
| POST | `/questions/{id}/run` | run saved with `parameters{}`. A question marked as a parameter source is rejected unless the caller's role is allowlisted | V |
| GET | `/parameter-questions` | parameter questions the caller's role may use as sources | V |
| GET | `/parameter-questions/{id}/options` | `{items:[{value,label}]}` from `value_field` and `display_field`. Same allowlist as above | V |
| GET/PUT | `/parameter-access` | read or replace `{grants:[{role,question_id}]}` for membership roles | A |
| GET/POST/PATCH/DELETE | `/collections[/{id}]` | folders | V / E |

## Visualization
| GET | `/chart-templates` · `/chart-templates/{key}` | list / template (schema, requirements) | V |
| POST | `/viz/auto` | `{columns[]}` → suggested template + config | V |
| POST | `/viz/validate` | `{template, config, columns}` → issues | E |
| POST | `/viz/pivot` | server-side pivot of a result | V |

## Dashboards
| Method | Path | Purpose | Role |
|---|---|---|---|
| GET/POST | `/dashboards` | list / create | V / E |
| GET/PUT/DELETE | `/dashboards/{id}` | full structure (tabs, cards, params) / replace layout / archive | V / E |
| POST | `/dashboards/{id}/cards` · PATCH/DELETE `/cards/{id}` | card CRUD (incl. `param_mappings`) | E |
| PUT | `/dashboards/{id}/parameters` | replace dashboard parameters | E |
| POST | `/dashboards/{id}/data` | run cards `{parameters{}, card_ids?, refresh?}` → per-card `{data|error|needs_input}` | V |

## AI
| Method | Path | Purpose | Role |
|---|---|---|---|
| POST | `/ai/conversations` | `{datasource_id, target_type}` | E |
| GET | `/ai/conversations/{id}` | messages + drafts | E |
| POST | `/ai/conversations/{id}/messages` | user message → creates `ai_request` (202). `Idempotency-Key` supported | E |
| GET | `/ai/requests/{id}` | status, result, validation issues | E |
| GET | `/ai/requests/{id}/events` | **SSE** progress: `status`, `retrieval`, `draft`, `validation`, `done`, `error` | E |
| POST | `/ai/requests/{id}/accept` | save draft (`{name, collection_id}`); dashboards accept all or `card_keys[]` | E |
| POST | `/ai/requests/{id}/discard` | | E |
| POST | `/ai/requests/{id}/feedback` | `{rating, comment, corrected_sql, use_for_training}` | E |
| GET | `/ai/usage` | tokens/requests per period | A |
| GET/PUT | `/ai/provider-config` | company key/model (secret write-only) | A |
| GET | `/ai/models` | models from provider | A |

## Admin
| GET/PATCH | `/platform/users[/{userId}]` | list every account and edit username, role, `branch_ids` | platform admin (email or branch code in `application.yml`) |
| GET | `/audit-log` | filter by user/action/date | A |
| GET/PUT | `/quotas` | read (A) / set (platform admin) | A |
| GET | `/query-runs` | execution history | A |
| POST/GET/DELETE | `/api-keys[/{id}]` *(phase 7)* | | A |
| POST | `/embed-tokens` *(phase 7)* | | E |

## Conventions worth fixing early
- Run results: `columns:[{name,type(normalized),native_type}]`, `rows:[[…]]` (array-of-arrays for compactness), `truncated:boolean`, `row_limit`, `cache:{hit,age_s}`.
- Parameters in run requests: object keyed by parameter name; ranges as `{start,end}`; presets as `{"preset":"last_30_days"}`.
- Long-running: `202 Accepted` + `Location`; progress via polling or SSE; never block > 30 s on HTTP.

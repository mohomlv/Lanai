# Security Requirements

Treat this product as a credential vault plus a remote SQL executor plus an LLM gateway: each is a high-value target. Every item below is a requirement with an owning module and a test.

## 1. Assets and threats
| Asset | Threats |
|---|---|
| Customer DB credentials | theft via API/logs/backups; cross-tenant leakage |
| Customer data (results, samples) | tenant crossover; exfiltration through AI provider; over-broad queries |
| App infrastructure | SSRF through datasource hosts; JDBC property abuse (RCE/file read); SQL injection into customer DBs; DoS via heavy queries |
| AI pipeline | prompt injection from DB content (table names, comments, sample values, docs); hallucinated destructive SQL; data leakage to provider |
| Accounts | credential stuffing, token theft, privilege escalation across companies |

## 2. Credentials and secrets
- Customer DB passwords, SSH keys and per-company API keys: **Tink AES-GCM** with envelope encryption; KEK from KMS/Vault/K8s Secret; `secret_key_id` stored for rotation; background `RotateKeysJob` re-encrypts.
- DTOs: secret fields are `@JsonProperty(access = WRITE_ONLY)`; responses return only `has_password: true`.
- Never logged: passwords, API keys, tokens, bound parameter values, full prompts with samples (prompt text retention limited). `LogScrubber` test enforces patterns.
- Platform secrets (Cursor key, DB, keyset) only via env/Vault; no secrets in the repo, images or Helm values.
- Heap dumps/crash reports disabled or scrubbed in production.

## 3. Datasource connection safety
- **Never accept a raw JDBC URL.** Build from `type, host, port, database, ssl_mode` per `DriverAdapter.buildJdbcUrl`.
- **JDBC property allowlist** per dialect (`DriverAdapter.allowedProperties`): e.g. `loginTimeout`, `connectTimeout`, `socketTimeout`, `applicationName`, `trustServerCertificate`(admin only). Explicitly blocked: `socketFactory*`, `sslfactory*`, `autoDeserialize`, `allowLoadLocalInfile`, `allowUrlInLocalInfile`, `allowMultiQueries`, `queryInterceptors`, `connectionAttributes`, any class-name property, `jdbcCompliantTruncation` abuse, init SQL.
- **SSRF:** `HostValidator` resolves the hostname once, rejects loopback, link-local (incl. `169.254.169.254`), unspecified, multicast, and internal service CIDRs; private ranges only for allow-listed companies/self-hosted installs; pin the resolved IP for the socket (prevents DNS rebinding); restrict ports to configured list; re-validate on every edit and at pool creation.
- TLS: default `require`; warn on `disable`; for `verify-full` allow uploading a CA (stored encrypted).
- SSH tunnel (optional): host key pinning; keys stored encrypted; no agent forwarding.
- Prefer customers giving a **dedicated read-only user**; the connection test detects write privileges and warns, with copy-paste `GRANT` guidance per dialect.

## 4. Query execution guardrails (`QueryExecutor` is the only path)
1. Authorization: role + `datasource_permission` (`can_query`, `can_native_sql`).
2. JSqlParser: exactly one statement; `SELECT`/`WITH … SELECT` only; reject `INTO`, `FOR UPDATE`, DDL/DML, `EXEC`, `CALL`, `DECLARE`, temp tables.
3. Function denylist (case-insensitive, also inside subqueries): Postgres `pg_read_file`, `pg_ls_dir`, `lo_import`, `lo_export`, `dblink*`, `copy`, `pg_sleep`; SQL Server `xp_*`, `sp_*`, `OPENROWSET`, `OPENQUERY`, `OPENDATASOURCE`, `BULK`; MySQL `LOAD_FILE`, `INTO OUTFILE/DUMPFILE`, `SLEEP`, `BENCHMARK`; Oracle `UTL_*`, `DBMS_*`. Parse failure ⇒ reject.
4. Tables referenced must exist in the catalog and be `visible`; hidden tables are rejected even in native SQL.
5. Values only via `PreparedStatement` binds; identifiers only from the catalog (never user text). `@username` / `@branches` bind from the signed-in membership and must not be taken from request parameter values.
6. Session read-only + timeout + row cap + concurrency bulkhead (technical.md §5).
7. Cancellation endpoint; per-company rate limit and quota; runaway query kill on pool eviction.
8. Result size cap (rows × columns) and cell truncation for huge text/binary.

## 5. Tenant isolation
`company_id` on every table; Hibernate filter; explicit `companyId` params; Redis keys and vector filters include it; JobRunr payloads carry it; S3/file paths (exports) prefixed by it. **Required tests:** `CrossTenantIT` for every controller; a repository-lint test that fails if a repository method lacks a company scope; vector search test proving company B cannot retrieve company A chunks.

## 6. AI-specific controls
- **Sharing level** per datasource (`none`/`schema_only`/`schema_and_samples`); UI discloses exactly what is sent to the provider. Default `schema_only`.
- PII columns/`exclude_from_samples` never carry values to the provider; sample values truncated and limited.
- **Credentials never enter prompts**, MCP configs, or agent env.
- **Prompt injection:** all DB-derived and user-derived text is delimited and declared as data; output is machine-validated (JSON Schema + catalog + SQL safety); agents have no tools/repos; nothing from an agent is executed without passing the full validation pipeline; agent text is never rendered as HTML (escape/markdown-sanitize).
- Provider keys: per-company BYO key supported; platform key rate-limited per company; quota enforcement before every provider call.
- Data retention at provider: delete agents when done (`DELETE /v1/agents/{id}`); company deletion deletes all agents. Document provider terms in the privacy policy.
- Optional MCP tool server (later): short-lived scoped tokens (company+datasource+TTL), read-only tools, same validators, audit every call.

## 7. Authentication and sessions
- Passwords: Argon2id (or bcrypt cost ≥ 12); breach-list check optional; rate limit + progressive delay on login; email verification required before creating a company.
- JWT access (15 min, `company_id`, `role`, `username`, `branch_ids`, `jti`) + rotating refresh tokens with reuse detection (revoke family); refresh stored hashed; logout revokes. `@branches` binds from the access-token claim, not a membership query.
- Cookies (if used): `HttpOnly; Secure; SameSite=Lax` + CSRF token for state-changing requests; otherwise Authorization header only. Strict CORS allowlist.
- Authorization: method-level `@PreAuthorize` using membership role; object-level checks via company scope + collection permissions; owner-only actions (delete company, transfer ownership).
- MFA/SSO (OIDC) planned; keep `user_account.password_hash` nullable.
- API keys: shown once, stored as SHA-256, scoped, revocable, per-key rate limit.
- Embed/public links: signed tokens, locked parameters, expiry, revocation, no access to other resources.

## 8. Platform hardening
Security headers (CSP, HSTS, X-Content-Type-Options, frame-ancestors), request size limits, Jackson polymorphic typing disabled, dependency scanning (OWASP Dependency-Check/Trivy) and SBOM in CI, container runs non-root/read-only FS, network policies (app can reach customer DB ports and provider; nothing else), secrets scanning (gitleaks) in CI.

## 9. Audit and compliance
`audit_log` entries for auth events, datasource create/update/delete/test, permission changes, query runs (hash+rows+duration only), AI requests (+provider, tokens), exports, key rotation. Export/delete of a company's data (GDPR-style) via admin job; retention per `entities.md §11`.

## 10. Incident readiness
Key-rotation runbook (KEK and per-company), "disable datasource globally" kill switch, provider kill switch (feature flag → fallback or AI off), alerting on: spikes in rejected SQL, cross-tenant 404 anomalies, failed logins, provider error rate, pool exhaustion.

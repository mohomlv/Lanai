"use client";

import { FormEvent, useState } from "react";
import { api, ApiError } from "@/lib/api/client";
import type { Datasource } from "@/lib/api/types";

const TYPES = [
  { value: "postgres", label: "PostgreSQL", port: 5432 },
  { value: "mysql", label: "MySQL", port: 3306 },
  { value: "mariadb", label: "MariaDB", port: 3306 },
  { value: "sqlserver", label: "SQL Server", port: 1433 },
  { value: "oracle", label: "Oracle", port: 1521 },
  { value: "redshift", label: "Amazon Redshift", port: 5439 },
  { value: "clickhouse", label: "ClickHouse", port: 8123 },
  { value: "snowflake", label: "Snowflake", port: 443 },
  { value: "trino", label: "Trino", port: 8080 },
  { value: "starburst", label: "Starburst", port: 8080 },
  { value: "presto", label: "Presto", port: 8080 },
  { value: "h2", label: "H2", port: 9092 },
  { value: "sqlite", label: "SQLite", port: 0 },
];

type Props = {
  initial?: Partial<Datasource>;
  onSaved: (ds: Datasource, scrapeJobId?: string) => void;
};

export function DatasourceForm({ initial, onSaved }: Props) {
  const [name, setName] = useState(initial?.name ?? "");
  const [type, setType] = useState(initial?.type ?? "postgres");
  const [host, setHost] = useState(initial?.host ?? "localhost");
  const [port, setPort] = useState(initial?.port ?? 5432);
  const [databaseName, setDatabaseName] = useState(initial?.database_name ?? "");
  const [username, setUsername] = useState(initial?.username ?? "");
  const [password, setPassword] = useState("");
  const [sslMode, setSslMode] = useState(initial?.ssl_mode ?? "disable");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [sharing, setSharing] = useState(initial?.ai_sharing_level ?? "schema_only");
  const [error, setError] = useState<string | null>(null);
  const [testResult, setTestResult] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [scrapeOnSave, setScrapeOnSave] = useState(true);
  const [scrapeWithAi, setScrapeWithAi] = useState(true);
  const [savedId, setSavedId] = useState<string | null>(initial?.id ?? null);

  function body(includePassword: boolean) {
    const payload: Record<string, unknown> = {
      name,
      type,
      host,
      port: Number(port),
      database_name: databaseName,
      username,
      ssl_mode: sslMode,
      description,
      ai_sharing_level: sharing,
    };
    if (includePassword && password) payload.password = password;
    return payload;
  }

  async function testConnection() {
    setError(null);
    setTestResult(null);
    try {
      const res = await api<{ ok?: boolean; server_version?: string; writable?: boolean }>(
        "/api/v1/datasources/test",
        { method: "POST", body: JSON.stringify({ ...body(true), password: password || "x" }) },
      );
      setTestResult(
        res.ok === false
          ? "Connection failed"
          : `OK${res.server_version ? ` · ${res.server_version}` : ""}${res.writable ? " · writable user (prefer read-only)" : ""}`,
      );
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Test failed");
    }
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const path = initial?.id ? `/api/v1/datasources/${initial.id}` : "/api/v1/datasources";
      const method = initial?.id ? "PATCH" : "POST";
      const payload = body(!initial?.id || !!password);
      if (initial?.id && !password) delete payload.password;
      const ds = await api<Datasource>(path, { method, body: JSON.stringify(payload) });
      setSavedId(ds.id);
      let scrapeJobId: string | undefined;
      if (!initial?.id && scrapeOnSave) {
        const job = await api<{ id: string }>(`/api/v1/datasources/${ds.id}/scrape`, {
          method: "POST",
          body: JSON.stringify({ use_ai: scrapeWithAi }),
        });
        scrapeJobId = job.id;
      }
      onSaved(ds, scrapeJobId);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Save failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <form className="card stack" onSubmit={onSubmit}>
      {error && <div className="error-banner">{error}</div>}
      {testResult && <p className="muted">{testResult}</p>}
      <div className="grid-2">
        <label className="label">
          Name
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} required />
        </label>
        <label className="label">
          Type
          <select
            className="select"
            value={type}
            onChange={(e) => {
              const t = e.target.value;
              setType(t);
              const meta = TYPES.find((x) => x.value === t);
              if (meta) setPort(meta.port);
            }}
          >
            {TYPES.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </select>
        </label>
        <label className="label">
          Host
          <input className="input" value={host} onChange={(e) => setHost(e.target.value)} required />
        </label>
        <label className="label">
          Port
          <input
            className="input"
            type="number"
            value={port}
            onChange={(e) => setPort(Number(e.target.value))}
            required
          />
        </label>
        <label className="label">
          {type === "sqlite" ? "Database file" : type === "snowflake" ? "Database" : type === "oracle" ? "Service name" : "Database"}
          <input
            className="input"
            value={databaseName}
            onChange={(e) => setDatabaseName(e.target.value)}
            placeholder={type === "snowflake" ? "Host is account.snowflakecomputing.com" : type === "sqlite" ? "C:\\data\\app.db" : ""}
            required
          />
        </label>
        <label className="label">
          Username
          <input className="input" value={username} onChange={(e) => setUsername(e.target.value)} required />
        </label>
        <label className="label">
          Password {initial?.id ? "(leave blank to keep)" : ""}
          <input
            className="input"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required={!initial?.id}
          />
        </label>
        <label className="label">
          SSL mode
          <select className="select" value={sslMode} onChange={(e) => setSslMode(e.target.value)}>
            <option value="disable">disable</option>
            <option value="require">require</option>
            <option value="verify-full">verify-full</option>
          </select>
        </label>
        <label className="label">
          AI sharing level
          <select className="select" value={sharing} onChange={(e) => setSharing(e.target.value)}>
            <option value="none">none — no AI / skeleton docs only</option>
            <option value="schema_only">schema_only — schema + docs, no samples</option>
            <option value="schema_and_samples">schema_and_samples — includes column samples</option>
          </select>
        </label>
      </div>
      <label className="label">
        Business description (fed to AI)
        <textarea
          className="textarea"
          rows={4}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="What this database is about, important tables, glossary…"
        />
      </label>
      {!initial?.id && (
        <label className="label" style={{ flexDirection: "row", alignItems: "center", gap: "0.5rem" }}>
          <input type="checkbox" checked={scrapeOnSave} onChange={(e) => setScrapeOnSave(e.target.checked)} />
          After save, sync the catalog and write base.md plus one markdown file per table
        </label>
      )}
      {!initial?.id && scrapeOnSave && (
        <label className="label" style={{ flexDirection: "row", alignItems: "center", gap: "0.5rem" }}>
          <input type="checkbox" checked={scrapeWithAi} onChange={(e) => setScrapeWithAi(e.target.checked)} />
          Use AI to write those files. Either way, each file includes related tables and their fields.
        </label>
      )}
      {savedId && error && (
        <p className="muted">
          The datasource was saved. Open it and run Scrape docs again if this attempt stopped early.{" "}
          <a href={`/datasources/${savedId}`}>Open datasource</a>
        </p>
      )}
      <div className="row">
        <button type="button" className="btn" onClick={testConnection}>
          Test connection
        </button>
        <button type="submit" className="btn btn-primary" disabled={loading}>
          {loading ? "Saving…" : "Save"}
        </button>
      </div>
    </form>
  );
}

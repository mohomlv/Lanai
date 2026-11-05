"use client";

import { FormEvent, Suspense, useEffect, useMemo, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import ReactECharts from "echarts-for-react";
import { api, ApiError } from "@/lib/api/client";
import type { Datasource, Page, QueryResult } from "@/lib/api/types";
import { CHARTS, chartOption as toChartOption, scalarText } from "@/lib/charts";
import { SqlEditor, type SqlEditorHandle } from "@/components/SqlEditor";
import { ParameterBar } from "@/components/ParameterBar";
import { USER_ATTRIBUTES, type CatalogName } from "@/lib/sql-names";
import { detectSqlParams, mergeSqlParams, type ParamValue, type SqlParam } from "@/lib/sql-params";

function parameterSnippet(name: string, type: string, column: string, optional: boolean) {
  const col = column.trim();
  let body = `{{${name}}}`;
  if (col && type === "date_range") body = `${col} >= {{${name}.start}} AND ${col} < {{${name}.end_exclusive}}`;
  else if (col && type === "select_multi") body = `${col} IN ({{${name}}})`;
  else if (col) body = `${col} = {{${name}}}`;
  if (col) body = ` AND ${body}`;
  return optional ? `[[${body}]]` : body.trim();
}

function QueriesInner() {
  const params = useSearchParams();
  const router = useRouter();
  const collectionId = params.get("collection") ?? "";
  const [datasourceId, setDatasourceId] = useState("");
  const [questionName, setQuestionName] = useState("Saved question");
  const [chart, setChart] = useState("table");
  const sqlRef = useRef("SELECT 1 AS n");
  const editorRef = useRef<SqlEditorHandle | null>(null);
  const [parameters, setParameters] = useState<SqlParam[]>([]);
  const [paramValues, setParamValues] = useState<Record<string, ParamValue>>({});
  const [paramName, setParamName] = useState("");
  const [paramType, setParamType] = useState("text");
  const [paramColumn, setParamColumn] = useState("");
  const [paramOptional, setParamOptional] = useState(true);
  const [names, setNames] = useState<CatalogName[]>([]);
  useEffect(() => {
    if (!datasourceId) {
      setNames(USER_ATTRIBUTES);
      return;
    }
    let cancelled = false;
    api<{ items: CatalogName[] }>(`/api/v1/datasources/${datasourceId}/autocomplete`)
      .then((res) => {
        if (!cancelled) setNames([...(res.items ?? []), ...USER_ATTRIBUTES]);
      })
      .catch(() => {
        if (!cancelled) setNames([]);
      });
    return () => {
      cancelled = true;
    };
  }, [datasourceId]);
  const [result, setResult] = useState<QueryResult | null>(null);
  const [chartOption, setChartOption] = useState<object | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [asParameter, setAsParameter] = useState(false);
  const [valueField, setValueField] = useState("");
  const [displayField, setDisplayField] = useState("");

  const datasources = useQuery({
    queryKey: ["datasources"],
    queryFn: () => api<Page<Datasource> | Datasource[]>("/api/v1/datasources"),
  });
  const dsItems = useMemo(
    () => (Array.isArray(datasources.data) ? datasources.data : datasources.data?.items ?? []),
    [datasources.data],
  );
  const columnNames = useMemo(() => {
    const seen = new Set<string>();
    for (const column of result?.columns ?? []) {
      if (/^[A-Za-z_][A-Za-z0-9_]*$/.test(column.name)) seen.add(column.name);
    }
    return [...seen];
  }, [result]);

  useEffect(() => {
    if (columnNames.length === 0) {
      setValueField("");
      setDisplayField("");
      return;
    }
    setValueField((current) => (columnNames.includes(current) ? current : columnNames[0]));
    setDisplayField((current) => (columnNames.includes(current) ? current : columnNames[Math.min(1, columnNames.length - 1)]));
  }, [columnNames]);

  async function run() {
    setBusy(true);
    setError(null);
    setChartOption(null);
    try {
      const res = await api<QueryResult>("/api/v1/queries/run", {
        method: "POST",
        body: JSON.stringify({
          datasource_id: datasourceId,
          mode: "native",
          native_sql: sqlRef.current,
          parameter_definitions: parameters,
          parameter_values: paramValues,
        }),
      });
      setResult(res);
      if (res.columns?.length) setChartOption(toChartOption(chart, res));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Run failed");
    } finally {
      setBusy(false);
    }
  }

  async function saveQuestion() {
    if (!datasourceId || !sqlRef.current) return;
    if (asParameter && (!columnNames.includes(valueField) || !columnNames.includes(displayField))) {
      setError("Run the query, then choose the field used and the field shown.");
      return;
    }
    setBusy(true);
    try {
      const saved = await api<{ id: string }>("/api/v1/questions", {
        method: "POST",
        body: JSON.stringify({
          datasource_id: datasourceId,
          name: questionName || "Saved question",
          collection_id: collectionId || undefined,
          mode: "native",
          native_sql: sqlRef.current,
          parameters: parameters,
          viz_template_key: chart,
          is_parameter: asParameter,
          parameter_fields: asParameter
            ? { fields: columnNames, value_field: valueField, display_field: displayField }
            : undefined,
        }),
      });
      if (saved.id) router.push(`/questions/${saved.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Save failed");
    } finally {
      setBusy(false);
    }
  }

  function addParam(e: FormEvent) {
    e.preventDefault();
    const name = paramName.trim().replace(/[^A-Za-z0-9_]/g, "");
    if (!name) return;
    const snippet = parameterSnippet(name, paramType, paramColumn, paramOptional);
    if (editorRef.current && !sqlRef.current.includes(`{{${name}}}`)) {
      editorRef.current.insertAtCursor(snippet);
      sqlRef.current = editorRef.current.getValue();
    }
    setParameters((current) => (current.some((item) => item.name === name) ? current : [...current, { name, type: paramType }]));
    setParamName("");
  }

  return (
    <div className="stack">
      <h1>Query runner</h1>
      <p className="muted">Run native SQL against a connected datasource (SELECT only).</p>
      {error && <div className="error-banner">{error}</div>}

      <label className="label" style={{ maxWidth: 420 }}>
        Datasource
        <select className="select" value={datasourceId} onChange={(e) => setDatasourceId(e.target.value)}>
          <option value="">Select…</option>
          {dsItems.map((d) => (
            <option key={d.id} value={d.id}>
              {d.name}
            </option>
          ))}
        </select>
      </label>

      <label className="label" style={{ maxWidth: 420 }}>
        Question name
        <input className="input" value={questionName} onChange={(e) => setQuestionName(e.target.value)} />
      </label>

      <div className="query-layout">
      <div className="stack">
      <ParameterBar
        parameters={parameters}
        values={paramValues}
        onChange={(name, value) => setParamValues((current) => ({ ...current, [name]: value }))}
      />
      <div className="card" style={{ overflow: "visible", padding: 0 }}>
        <SqlEditor
          defaultValue="SELECT 1 AS n"
          names={names}
          onChange={(sql) => {
            sqlRef.current = sql;
            setParameters((current) => mergeSqlParams(detectSqlParams(sql), current));
          }}
          onReady={(handle) => {
            editorRef.current = handle;
          }}
        />
      </div>
      <p className="muted">
        Pick a data source. Tab opens the saved name list and moves to the next name. Enter inserts the highlighted name.
        Type @ for user attributes such as @username and @branches. Wrap a filter in [[ ]] so it is omitted when that attribute is empty.
      </p>

      <label className="label" style={{ maxWidth: 280 }}>
        Chart
        <select
          className="select"
          value={chart}
          onChange={(e) => {
            const next = e.target.value;
            setChart(next);
            if (result) setChartOption(toChartOption(next, result));
          }}
        >
          {CHARTS.map((item) => (
            <option key={item.key} value={item.key}>
              {item.label}
            </option>
          ))}
        </select>
      </label>

      <section className="card stack">
        <h2>Parameter source</h2>
        <p className="muted">
          Mark this question so other questions can use its rows as a list. The field used is the value stored in SQL. The field shown is the label people see.
        </p>
        <label className="label">
          <span className="row">
            <input type="checkbox" checked={asParameter} onChange={(e) => setAsParameter(e.target.checked)} />
            Use as a parameter
          </span>
        </label>
        {asParameter && columnNames.length === 0 && (
          <p className="muted">
            {result
              ? "Alias the columns with plain names such as city_id and city_name, then run again."
              : "Run the query first. The result columns become the fields you can use and show."}
          </p>
        )}
        {asParameter && columnNames.length > 0 && (
          <div className="row" style={{ alignItems: "flex-end" }}>
            <label className="label" style={{ minWidth: 180 }}>
              Field used
              <select className="select" value={valueField} onChange={(e) => setValueField(e.target.value)}>
                {columnNames.map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
              </select>
            </label>
            <label className="label" style={{ minWidth: 180 }}>
              Field shown
              <select className="select" value={displayField} onChange={(e) => setDisplayField(e.target.value)}>
                {columnNames.map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
              </select>
            </label>
          </div>
        )}
      </section>

      <div className="row">
        <button type="button" className="btn btn-primary" disabled={!datasourceId || busy} onClick={run}>
          {busy ? "Running…" : "Run"}
        </button>
        <button type="button" className="btn" disabled={!datasourceId || busy} onClick={saveQuestion}>
          Save as question
        </button>
      </div>
      </div>
      <aside className="card stack">
        <h2>Parameters</h2>
        <p className="muted">
          Type {"{{name}}"} in the SQL and a filter appears above the question. Use @username or @branches for the signed-in user.
          Wrap a filter in [[ ]] and it disappears when the value is empty.
        </p>
        {parameters.length === 0 && <p className="muted">No {"{{parameters}}"} in the SQL yet.</p>}
        <form className="stack" onSubmit={addParam}>
          <input className="input" placeholder="name" value={paramName} onChange={(e) => setParamName(e.target.value)} />
          <select className="select" value={paramType} onChange={(e) => setParamType(e.target.value)}>
            {["text", "number", "date", "date_range", "select", "select_multi", "boolean"].map((type) => (
              <option key={type} value={type}>{type}</option>
            ))}
          </select>
          <input className="input" placeholder="column (optional)" value={paramColumn} onChange={(e) => setParamColumn(e.target.value)} />
          <label className="label" style={{ flexDirection: "row", alignItems: "center", gap: "0.45rem" }}>
            <input type="checkbox" checked={paramOptional} onChange={(e) => setParamOptional(e.target.checked)} />
            Hide with [[ ]] when empty
          </label>
          {paramType === "date_range" && <p className="muted">Value: 2026-01-01~2026-01-31 or preset:last_30_days</p>}
          <button className="btn" type="submit">Add parameter</button>
        </form>
      </aside>
      </div>

      {result && (
        <>
          {chart === "scalar" && (
            <div className="card">
              <p style={{ fontSize: 40, margin: 0 }}>{scalarText(result)}</p>
            </div>
          )}
          {chartOption && (
            <div className="card">
              <ReactECharts option={chartOption} style={{ height: 320 }} />
            </div>
          )}
          <div className="card" style={{ overflow: "auto" }}>
            <p className="muted">
              {result.duration_ms ?? "—"} ms
              {result.truncated ? " · truncated" : ""}
              {result.sql ? ` · ${result.sql}` : ""}
            </p>
            <table className="table">
              <thead>
                <tr>
                  {result.columns.map((c) => (
                    <th key={c.name}>{c.name}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {result.rows.slice(0, 200).map((row, i) => (
                  <tr key={i}>
                    {row.map((cell, j) => (
                      <td key={j} className="mono">
                        {cell == null ? "" : String(cell)}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}

export default function QueriesPage() {
  return (
    <Suspense>
      <QueriesInner />
    </Suspense>
  );
}


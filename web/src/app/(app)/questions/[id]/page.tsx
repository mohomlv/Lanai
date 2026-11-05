"use client";

import { useParams } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import ReactECharts from "echarts-for-react";
import { api, ApiError } from "@/lib/api/client";
import type { QueryResult, Question } from "@/lib/api/types";
import { CHARTS, chartOption, scalarText } from "@/lib/charts";
import { detectSqlParams, mergeSqlParams, type ParamValue, type SqlParam } from "@/lib/sql-params";
import { QuestionParameters } from "@/components/QuestionParameters";

type QuestionView = Question & {
  version?: { native_sql?: string; parameters?: SqlParam[]; mode?: string; viz_template_key?: string };
};

export default function QuestionDetailPage() {
  const { id } = useParams<{ id: string }>();
  const qc = useQueryClient();
  const [result, setResult] = useState<QueryResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [values, setValues] = useState<Record<string, ParamValue>>({});
  const [sqlText, setSqlText] = useState("");
  const [chart, setChart] = useState("table");
  const [loaded, setLoaded] = useState("");

  const question = useQuery({
    queryKey: ["question", id],
    queryFn: () => api<QuestionView>(`/api/v1/questions/${id}`),
  });

  const version = question.data?.version;
  const savedParameters = version?.parameters ?? [];
  const parameters = mergeSqlParams(detectSqlParams(sqlText || version?.native_sql || question.data?.native_sql || ""), savedParameters);

  useEffect(() => {
    if (!question.data || loaded === question.data.id + (version?.native_sql ?? "")) return;
    setSqlText(version?.native_sql ?? question.data.native_sql ?? "");
    setChart(version?.viz_template_key || "table");
    setLoaded(question.data.id + (version?.native_sql ?? ""));
  }, [question.data, version?.native_sql, version?.viz_template_key, loaded]);

  async function saveVersion(nextParameters: SqlParam[], nextSql = sqlText, nextChart = chart) {
    await api(`/api/v1/questions/${id}/versions`, {
      method: "POST",
      body: JSON.stringify({
        mode: version?.mode ?? "native",
        native_sql: nextSql,
        parameters: nextParameters,
        viz_template_key: nextChart,
      }),
    });
    qc.invalidateQueries({ queryKey: ["question", id] });
  }

  async function run() {
    setError(null);
    try {
      const res = await api<QueryResult>("/api/v1/queries/run", {
        method: "POST",
        body: JSON.stringify({
          datasource_id: question.data?.datasource_id,
          mode: "native",
          native_sql: sqlText,
          parameter_definitions: parameters,
          parameter_values: values,
        }),
      });
      setResult(res);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Run failed");
    }
  }

  async function saveSql(e: FormEvent) {
    e.preventDefault();
    try {
      await saveVersion(parameters);
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save question");
    }
  }

  if (question.isLoading) return <p className="muted">Loading…</p>;
  if (question.isError) {
    const message = question.error instanceof ApiError ? question.error.message : "Question not found";
    return <div className="error-banner">{message}</div>;
  }
  if (!question.data) return <div className="error-banner">Question not found</div>;

  const option = result ? chartOption(chart, result) : null;

  return (
    <div className="stack">
      <h1>{question.data.name}</h1>
      {question.data.description && <p className="muted">{question.data.description}</p>}
      {error && <div className="error-banner">{error}</div>}
      <QuestionParameters
        questionId={id}
        sql={sqlText}
        parameters={parameters}
        values={values}
        onValue={(name, value) => setValues((current) => ({ ...current, [name]: value }))}
        onSql={setSqlText}
        isParameter={Boolean(question.data.is_parameter)}
        parameterFields={question.data.parameter_fields}
        saveParameters={(next, nextSql) => saveVersion(next, nextSql)}
      />
      <form className="card stack" onSubmit={saveSql}>
        <label className="label">
          Chart
          <select
            className="select"
            value={chart}
            onChange={(e) => {
              setChart(e.target.value);
              void saveVersion(parameters, sqlText, e.target.value).catch((err) =>
                setError(err instanceof ApiError ? err.message : "Could not save chart"),
              );
            }}
          >
            {CHARTS.map((item) => (
              <option key={item.key} value={item.key}>
                {item.label}
              </option>
            ))}
          </select>
        </label>
        <label className="label">
          SQL
          <textarea className="input mono" rows={8} value={sqlText} onChange={(e) => setSqlText(e.target.value)} />
        </label>
        <p className="muted">
          {"{{name}}"} is filled in by the server. A block inside [[ ]] is removed when its value is empty, so that filter is hidden.
          A date range value is 2026-01-01~2026-01-31 or preset:last_30_days.
        </p>
        <button className="btn" type="submit">
          Save SQL
        </button>
      </form>
      <div className="row">
        <button type="button" className="btn btn-primary" onClick={run}>
          Run
        </button>
        <span className="muted">Runs the SQL in the editor before you save it.</span>
      </div>
      {result && option && (
        <div className="card">
          <ReactECharts option={option} style={{ height: 320 }} />
        </div>
      )}
      {result && chart === "scalar" && (
        <div className="card">
          <p className="muted">{result.columns[0]?.name}</p>
          <p style={{ fontSize: 40, margin: 0 }}>{scalarText(result)}</p>
        </div>
      )}
      {result && (
        <div className="card" style={{ overflow: "auto" }}>
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
      )}
    </div>
  );
}

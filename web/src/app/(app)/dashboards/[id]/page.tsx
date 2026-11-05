"use client";

import { FormEvent, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "@/lib/api/client";
import type { Dashboard, DashboardCard, Page, Question, QueryResult } from "@/lib/api/types";

export default function DashboardDetailPage() {
  const { id } = useParams<{ id: string }>();
  const qc = useQueryClient();
  const [questionId, setQuestionId] = useState("");
  const [cardData, setCardData] = useState<Record<string, QueryResult | { error?: string }>>({});
  const [error, setError] = useState<string | null>(null);
  const [paramSlug, setParamSlug] = useState("");
  const [paramLabel, setParamLabel] = useState("");
  const [paramType, setParamType] = useState("text");
  const [paramValues, setParamValues] = useState<Record<string, string>>({});
  const [picker, setPicker] = useState(false);
  const [pickerFolder, setPickerFolder] = useState("");

  const dashboard = useQuery({
    queryKey: ["dashboard", id],
    queryFn: () => api<Dashboard>(`/api/v1/dashboards/${id}`),
  });
  const questions = useQuery({
    queryKey: ["questions"],
    queryFn: () => api<Page<Question> | Question[]>("/api/v1/questions"),
  });
  const folders = useQuery({
    queryKey: ["collections"],
    queryFn: () => api<Page<{ id: string; name: string; parent_id?: string }> | { id: string; name: string; parent_id?: string }[]>("/api/v1/collections"),
  });

  const qItems = Array.isArray(questions.data) ? questions.data : questions.data?.items ?? [];
  const folderItems = Array.isArray(folders.data) ? folders.data : folders.data?.items ?? [];
  const cards: DashboardCard[] = useMemo(() => {
    const data = dashboard.data as Dashboard & { cards?: DashboardCard[] };
    if (data?.cards?.length) return data.cards;
    return (data?.tabs ?? []).flatMap((t) => t.cards ?? []);
  }, [dashboard.data]);

  const tabId = dashboard.data?.tabs?.[0]?.id;

  const addCard = useMutation({
    mutationFn: () =>
      api(`/api/v1/dashboards/${id}/cards`, {
        method: "POST",
        body: JSON.stringify({
          card_type: "question",
          question_id: questionId,
          tab_id: tabId,
          x: 0,
          y: cards.length * 4,
          w: 12,
          h: 8,
          param_mappings: {},
        }),
      }),
    onSuccess: () => {
      setQuestionId("");
      qc.invalidateQueries({ queryKey: ["dashboard", id] });
    },
  });

  async function loadData() {
    setError(null);
    try {
      const res = await api<{ cards?: { card_id: string; data?: QueryResult; error?: string }[] }>(
        `/api/v1/dashboards/${id}/data`,
        { method: "POST", body: JSON.stringify({ parameters: paramValues }) },
      );
      const mapped: Record<string, QueryResult | { error?: string }> = {};
      for (const item of res.cards ?? []) {
        if (item.data) mapped[item.card_id] = item.data;
        else if (item.error) mapped[item.card_id] = { error: item.error };
      }
      setCardData(mapped);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Load failed");
    }
  }

  if (dashboard.isLoading) return <p className="muted">Loading…</p>;
  if (!dashboard.data) return <div className="error-banner">Not found</div>;

  return (
    <div className="stack">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h1>{dashboard.data.name}</h1>
        <button type="button" className="btn btn-primary" onClick={loadData}>
          Refresh data
        </button>
      </div>
      {error && <div className="error-banner">{error}</div>}

      <section className="card stack">
        <h2>Dashboard parameters</h2>
        {(dashboard.data.parameters ?? []).map((parameter) => {
          const row = parameter as { slug?: string; label?: string; type?: string };
          const slug = row.slug ?? "";
          return (
            <label key={slug} className="label">
              {row.label || slug} ({row.type})
              <input
                className="input"
                value={paramValues[slug] ?? ""}
                onChange={(e) => setParamValues((current) => ({ ...current, [slug]: e.target.value }))}
              />
            </label>
          );
        })}
        <form
          className="row"
          onSubmit={(e: FormEvent) => {
            e.preventDefault();
            const existing = (dashboard.data.parameters ?? []) as { slug: string; label: string; type: string }[];
            void api(`/api/v1/dashboards/${id}/parameters`, {
              method: "PUT",
              body: JSON.stringify({
                parameters: [...existing, { slug: paramSlug, label: paramLabel || paramSlug, type: paramType }],
              }),
            }).then(() => {
              setParamSlug("");
              setParamLabel("");
              qc.invalidateQueries({ queryKey: ["dashboard", id] });
            });
          }}
        >
          <input className="input" style={{ maxWidth: 140 }} placeholder="slug" value={paramSlug} onChange={(e) => setParamSlug(e.target.value)} required />
          <input className="input" style={{ maxWidth: 160 }} placeholder="label" value={paramLabel} onChange={(e) => setParamLabel(e.target.value)} />
          <select className="select" value={paramType} onChange={(e) => setParamType(e.target.value)}>
            {["text", "number", "date", "date_range", "select", "select_multi", "boolean"].map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
          <button className="btn" type="submit">
            Add parameter
          </button>
        </form>
      </section>

      <div className="card stack">
        <div className="row">
          <button type="button" className="btn" onClick={() => setPicker((open) => !open)}>
            Choose question from folders
          </button>
          <button type="button" className="btn btn-primary" disabled={!questionId || !tabId} onClick={() => addCard.mutate()}>
            Add to dashboard
          </button>
        </div>
        {questionId && <p className="muted">Selected: {qItems.find((question) => question.id === questionId)?.name ?? questionId}</p>}
        {picker && (
          <div className="stack">
            <div className="row">
              <button type="button" className={`btn${!pickerFolder ? " btn-primary" : ""}`} onClick={() => setPickerFolder("")}>
                Top
              </button>
              {folderItems
                .filter((folder) => (folder.parent_id || "") === pickerFolder)
                .map((folder) => (
                  <button key={folder.id} type="button" className="btn" onClick={() => setPickerFolder(folder.id)}>
                    {folder.name}
                  </button>
                ))}
            </div>
            {qItems
              .filter((question) => (question.collection_id || "") === pickerFolder)
              .map((question) => (
                <button
                  key={question.id}
                  type="button"
                  className="btn"
                  onClick={() => {
                    setQuestionId(question.id);
                    setPicker(false);
                  }}
                >
                  {question.name}
                </button>
              ))}
          </div>
        )}
      </div>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(24, minmax(0, 1fr))",
          gap: "0.75rem",
        }}
      >
        {cards.length === 0 && <p className="muted">No cards yet.</p>}
        {cards.map((c) => {
          const data = cardData[c.id] as QueryResult | { error?: string } | undefined;
          const err = data && "error" in data ? data.error : undefined;
          const table = data && "columns" in data ? data : undefined;
          return (
            <div
              key={c.id}
              className="card"
              style={{
                gridColumn: `span ${Math.min(24, Math.max(6, c.w ?? 12))}`,
                minHeight: 180,
                overflow: "auto",
              }}
            >
              <div className="muted mono" style={{ fontSize: "0.75rem" }}>
                {qItems.find((question) => question.id === c.question_id)?.name ?? c.question_id}
              </div>
              {err && <div className="error-banner">{err}</div>}
              {table?.columns ? (
                <table className="table">
                  <thead>
                    <tr>
                      {table.columns.map((col) => (
                        <th key={col.name}>{col.name}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {(table.rows ?? []).slice(0, 20).map((row, i) => (
                      <tr key={i}>
                        {row.map((cell, j) => (
                          <td key={j}>{cell == null ? "" : String(cell)}</td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              ) : (
                !err && <p className="muted">Click Refresh data</p>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

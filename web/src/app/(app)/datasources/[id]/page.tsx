"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "@/lib/api/client";
import type { CatalogTable, Datasource, Page, SchemaDoc } from "@/lib/api/types";
import { DatasourceForm } from "@/components/DatasourceForm";
import { useEffect, useRef, useState } from "react";

export default function DatasourceDetailPage() {
  const { id } = useParams<{ id: string }>();
  const qc = useQueryClient();
  const [scrapeMsg, setScrapeMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [guideTitle, setGuideTitle] = useState("");
  const [guideMarkdown, setGuideMarkdown] = useState("");
  const alive = useRef(true);
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);

  const ds = useQuery({
    queryKey: ["datasource", id],
    queryFn: () => api<Datasource>(`/api/v1/datasources/${id}`),
  });

  const tables = useQuery({
    queryKey: ["tables", id],
    queryFn: () => api<Page<CatalogTable> | CatalogTable[]>(`/api/v1/datasources/${id}/tables`),
  });

  const docs = useQuery({
    queryKey: ["docs", id],
    queryFn: () => api<Page<SchemaDoc> | SchemaDoc[]>(`/api/v1/datasources/${id}/docs`),
  });

  const tableItems = Array.isArray(tables.data) ? tables.data : tables.data?.items ?? [];
  const docItems = Array.isArray(docs.data) ? docs.data : docs.data?.items ?? [];
  const guideItems = docItems.filter((doc) => doc.kind === "query_guide");
  const schemaItems = docItems.filter((doc) => doc.kind !== "query_guide");

  const sync = useMutation({
    mutationFn: () => api(`/api/v1/datasources/${id}/sync`, { method: "POST" }),
    onSuccess: () => {
      setScrapeMsg("Catalog sync queued");
      setTimeout(() => qc.invalidateQueries({ queryKey: ["tables", id] }), 2000);
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : "Sync failed"),
  });

  const test = useMutation({
    mutationFn: () => api<{ ok?: boolean; server_version?: string }>(`/api/v1/datasources/${id}/test`, { method: "POST" }),
    onSuccess: (r) => setScrapeMsg(`Test OK${r.server_version ? ` · ${r.server_version}` : ""}`),
    onError: (e) => setError(e instanceof ApiError ? e.message : "Test failed"),
  });

  const scrape = useMutation({
    mutationFn: (useAi: boolean) =>
      api<{ id: string; status?: string }>(`/api/v1/datasources/${id}/scrape`, {
        method: "POST",
        body: JSON.stringify({ use_ai: useAi }),
      }),
    onSuccess: (job) => {
      setScrapeMsg("Scrape queued. Writing base.md and each table file, including related tables.");
      watchScrape(job.id);
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : "Scrape failed"),
  });

  function watchScrape(jobId: string) {
    let stopped = false;
    const tick = async () => {
      for (let i = 0; i < 150 && !stopped && alive.current; i++) {
        const job = await api<{ status: string; done_items?: number; total_items?: number; ai_docs?: number; model?: string; error?: string }>(
          `/api/v1/scrape-jobs/${jobId}`,
        );
        const progress = job.total_items ? ` ${job.done_items ?? 0}/${job.total_items}` : "";
        if (job.status === "done" || job.status === "partial" || job.status === "failed") {
          const model = job.model === "used" ? ` Model wrote ${job.ai_docs ?? 0} docs.` : " Catalog markdown was saved.";
          setScrapeMsg(`Scrape ${job.status}.${progress}.${model}${job.error ? ` ${job.error}` : ""}`);
          qc.invalidateQueries({ queryKey: ["docs", id] });
          qc.invalidateQueries({ queryKey: ["tables", id] });
          return;
        }
        setScrapeMsg(`Scrape ${job.status}.${progress}`);
        qc.invalidateQueries({ queryKey: ["docs", id] });
        await new Promise((resolve) => setTimeout(resolve, 2000));
      }
    };
    void tick().catch((e) => setError(e instanceof ApiError ? e.message : "Could not read the scrape job"));
    return () => {
      stopped = true;
    };
  }

  useEffect(() => {
    const jobId = new URLSearchParams(window.location.search).get("scrape");
    if (!jobId) return;
    return watchScrape(jobId);
    // The job id in the URL is the only trigger.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const addGuide = useMutation({
    mutationFn: () =>
      api<SchemaDoc>(`/api/v1/datasources/${id}/docs`, {
        method: "POST",
        body: JSON.stringify({ title: guideTitle, markdown: guideMarkdown }),
      }),
    onSuccess: () => {
      setGuideTitle("");
      setGuideMarkdown("");
      setScrapeMsg("Query guide saved and embedded.");
      qc.invalidateQueries({ queryKey: ["docs", id] });
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : "Could not save the query guide"),
  });

  const deleteGuide = useMutation({
    mutationFn: (docId: string) => api(`/api/v1/docs/${docId}`, { method: "DELETE" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["docs", id] }),
    onError: (e) => setError(e instanceof ApiError ? e.message : "Could not delete the query guide"),
  });

  const patchTable = useMutation({
    mutationFn: ({ tableId, visible }: { tableId: string; visible: boolean }) =>
      api(`/api/v1/tables/${tableId}`, { method: "PATCH", body: JSON.stringify({ visible }) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["tables", id] }),
  });

  if (ds.isLoading) return <p className="muted">Loading…</p>;
  if (!ds.data) return <div className="error-banner">Datasource not found</div>;

  return (
    <div className="stack">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <div>
          <h1>{ds.data.name}</h1>
          <p className="muted mono">
            {ds.data.type} · {ds.data.host}:{ds.data.port}/{ds.data.database_name}
          </p>
        </div>
        <div className="row">
          <Link className="btn" href={`/ask?datasource=${id}`}>
            Ask AI
          </Link>
          <button type="button" className="btn" onClick={() => test.mutate()} disabled={test.isPending}>
            Test
          </button>
          <button type="button" className="btn" onClick={() => sync.mutate()} disabled={sync.isPending}>
            Sync catalog
          </button>
          <button type="button" className="btn" onClick={() => scrape.mutate(false)} disabled={scrape.isPending}>
            Scrape without AI
          </button>
          <button type="button" className="btn btn-primary" onClick={() => scrape.mutate(true)} disabled={scrape.isPending}>
            Scrape with AI
          </button>
        </div>
      </div>

      {error && <div className="error-banner">{error}</div>}
      {scrapeMsg && <p className="muted">{scrapeMsg}</p>}

      <p className="muted">
        AI sharing: <strong>{ds.data.ai_sharing_level ?? "schema_only"}</strong> — controls what schema/samples leave
        your network for the model.
      </p>

      <h2>Connection</h2>
      <DatasourceForm
        initial={ds.data}
        onSaved={() => {
          qc.invalidateQueries({ queryKey: ["datasource", id] });
          setScrapeMsg("Saved");
        }}
      />

      <h2>Catalog ({tableItems.length})</h2>
      <div className="card">
        {tableItems.length === 0 ? (
          <p className="muted">No tables yet — run Sync catalog.</p>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Table</th>
                <th>Columns</th>
                <th>Visible</th>
              </tr>
            </thead>
            <tbody>
              {tableItems.map((t) => (
                <tr key={t.id}>
                  <td className="mono">
                    {t.schema_name ? `${t.schema_name}.` : ""}
                    {t.table_name}
                  </td>
                  <td className="muted">{(t.columns ?? []).map((c) => c.name).join(", ") || "—"}</td>
                  <td>
                    <input
                      type="checkbox"
                      checked={t.visible !== false}
                      onChange={(e) => patchTable.mutate({ tableId: t.id, visible: e.target.checked })}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <h2>Query guides ({guideItems.length})</h2>
      <div className="card stack">
        <p className="muted">
          Add markdown for the reports you want Ask to create. Each file is embedded and retrieved when the question matches.
        </p>
        <form
          className="stack"
          onSubmit={(e) => {
            e.preventDefault();
            addGuide.mutate();
          }}
        >
          <label className="label">
            Title
            <input
              className="input"
              value={guideTitle}
              onChange={(e) => setGuideTitle(e.target.value)}
              placeholder="Monthly occupancy by gender"
            />
          </label>
          <label className="label">
            Markdown
            <textarea
              className="textarea mono"
              rows={10}
              value={guideMarkdown}
              onChange={(e) => setGuideMarkdown(e.target.value)}
              required
              placeholder={`# Monthly occupancy by gender\n\nWhen the user wants a line chart of first payments over time.\n\nChart: line\nColumns: date, p1, p1_title, p2, p2_title\n\nUse the payment and request tables. Include a short example SELECT if you have one.`}
            />
          </label>
          <button className="btn btn-primary" type="submit" disabled={addGuide.isPending || !guideMarkdown.trim()}>
            {addGuide.isPending ? "Saving…" : "Save query guide"}
          </button>
        </form>
        {guideItems.length === 0 ? (
          <p className="muted">No query guides yet. Add one above.</p>
        ) : (
          <ul className="stack">
            {guideItems.map((doc) => (
              <li key={doc.id} className="row" style={{ justifyContent: "space-between", alignItems: "center" }}>
                <Link href={`/docs/${doc.id}`}>
                  <span className="badge">{doc.kind}</span> {doc.title || "Query guide"} · v{doc.version ?? 1}
                </Link>
                <button
                  type="button"
                  className="btn btn-danger"
                  onClick={() => deleteGuide.mutate(doc.id)}
                  disabled={deleteGuide.isPending}
                >
                  Remove
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      <h2>Schema docs ({schemaItems.length})</h2>
      <div className="card stack">
        {schemaItems.length === 0 ? (
          <p className="muted">No docs — run Scrape docs after sync.</p>
        ) : (
          schemaItems.map((doc) => (
            <Link key={doc.id} href={`/docs/${doc.id}`}>
              <span className="badge">{doc.kind}</span> {doc.title ? `${doc.title} · ` : ""}v{doc.version ?? 1} · {doc.source ?? "ai"}
            </Link>
          ))
        )}
        <Link href={`/datasources/${id}/knowledge`}>Glossary & metrics →</Link>
      </div>
    </div>
  );
}

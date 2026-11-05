"use client";

import { FormEvent, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api/client";
import type { SchemaDoc } from "@/lib/api/types";

export default function DocEditorPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [doc, setDoc] = useState<SchemaDoc | null>(null);
  const [title, setTitle] = useState("");
  const [markdown, setMarkdown] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    api<SchemaDoc>(`/api/v1/docs/${id}`)
      .then((d) => {
        setDoc(d);
        setTitle(d.title ?? "");
        setMarkdown(d.markdown ?? "");
      })
      .catch((e) => setError(e instanceof ApiError ? e.message : "Load failed"));
  }, [id]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const next = await api<SchemaDoc>(`/api/v1/docs/${id}`, {
        method: "PUT",
        body: JSON.stringify({ markdown, title }),
      });
      setDoc(next);
      setTitle(next.title ?? title);
      setMarkdown(next.markdown ?? markdown);
      setSaved(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Save failed");
    }
  }

  if (!doc && !error) return <p className="muted">Loading…</p>;

  return (
    <div className="stack">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <div>
          <h1>{doc?.kind === "query_guide" ? "Query guide" : "Schema doc"}</h1>
          <p className="muted">
            {doc?.title ? `${doc.title} · ` : ""}
            {doc?.kind} · v{doc?.version} · {doc?.source}
          </p>
        </div>
        <button type="button" className="btn" onClick={() => router.back()}>
          Back
        </button>
      </div>
      {error && <div className="error-banner">{error}</div>}
      {saved && <p className="muted">Saved (new version).</p>}
      <form className="card stack" onSubmit={onSubmit}>
        {doc?.kind === "query_guide" && (
          <label className="label">
            Title
            <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} />
          </label>
        )}
        <textarea className="textarea mono" rows={24} value={markdown} onChange={(e) => setMarkdown(e.target.value)} />
        <button className="btn btn-primary" type="submit">
          Save
        </button>
      </form>
    </div>
  );
}

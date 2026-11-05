"use client";

import { FormEvent, useState } from "react";
import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import type { Page } from "@/lib/api/types";

type Glossary = { id: string; term: string; definition: string };
type Metric = { id: string; name: string; expression_sql: string };

export default function KnowledgePage() {
  const { id } = useParams<{ id: string }>();
  const qc = useQueryClient();
  const [term, setTerm] = useState("");
  const [definition, setDefinition] = useState("");
  const [metricName, setMetricName] = useState("");
  const [expression, setExpression] = useState("");

  const glossary = useQuery({
    queryKey: ["glossary", id],
    queryFn: () => api<Page<Glossary> | Glossary[]>(`/api/v1/datasources/${id}/glossary`),
  });
  const metrics = useQuery({
    queryKey: ["metrics", id],
    queryFn: () => api<Page<Metric> | Metric[]>(`/api/v1/datasources/${id}/metrics`),
  });

  const gItems = Array.isArray(glossary.data) ? glossary.data : glossary.data?.items ?? [];
  const mItems = Array.isArray(metrics.data) ? metrics.data : metrics.data?.items ?? [];

  const addGlossary = useMutation({
    mutationFn: () =>
      api(`/api/v1/datasources/${id}/glossary`, {
        method: "POST",
        body: JSON.stringify({ term, definition }),
      }),
    onSuccess: () => {
      setTerm("");
      setDefinition("");
      qc.invalidateQueries({ queryKey: ["glossary", id] });
    },
  });

  const addMetric = useMutation({
    mutationFn: () =>
      api(`/api/v1/datasources/${id}/metrics`, {
        method: "POST",
        body: JSON.stringify({ name: metricName, expression_sql: expression }),
      }),
    onSuccess: () => {
      setMetricName("");
      setExpression("");
      qc.invalidateQueries({ queryKey: ["metrics", id] });
    },
  });

  const delGlossary = useMutation({
    mutationFn: (gid: string) => api(`/api/v1/datasources/${id}/glossary/${gid}`, { method: "DELETE" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["glossary", id] }),
  });

  const delMetric = useMutation({
    mutationFn: (mid: string) => api(`/api/v1/datasources/${id}/metrics/${mid}`, { method: "DELETE" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["metrics", id] }),
  });

  function submitGlossary(e: FormEvent) {
    e.preventDefault();
    addGlossary.mutate();
  }

  function submitMetric(e: FormEvent) {
    e.preventDefault();
    addMetric.mutate();
  }

  return (
    <div className="stack">
      <h1>Glossary & metrics</h1>
      <p className="muted">These terms are included when the AI builds SQL for this datasource.</p>

      <div className="grid-2">
        <section className="card stack">
          <h2>Glossary</h2>
          <form className="stack" onSubmit={submitGlossary}>
            <input className="input" placeholder="Term" value={term} onChange={(e) => setTerm(e.target.value)} required />
            <textarea
              className="textarea"
              placeholder="Definition"
              value={definition}
              onChange={(e) => setDefinition(e.target.value)}
              required
            />
            <button className="btn btn-primary" type="submit">
              Add term
            </button>
          </form>
          <ul>
            {gItems.map((g) => (
              <li key={g.id} className="row" style={{ justifyContent: "space-between" }}>
                <span>
                  <strong>{g.term}</strong> — {g.definition}
                </span>
                <button type="button" className="btn btn-danger" onClick={() => delGlossary.mutate(g.id)}>
                  Remove
                </button>
              </li>
            ))}
          </ul>
        </section>

        <section className="card stack">
          <h2>Metrics</h2>
          <form className="stack" onSubmit={submitMetric}>
            <input
              className="input"
              placeholder="Name"
              value={metricName}
              onChange={(e) => setMetricName(e.target.value)}
              required
            />
            <textarea
              className="textarea mono"
              placeholder="expression SQL"
              value={expression}
              onChange={(e) => setExpression(e.target.value)}
              required
            />
            <button className="btn btn-primary" type="submit">
              Add metric
            </button>
          </form>
          <ul>
            {mItems.map((m) => (
              <li key={m.id} className="row" style={{ justifyContent: "space-between" }}>
                <span>
                  <strong>{m.name}</strong>
                  <div className="mono muted">{m.expression_sql}</div>
                </span>
                <button type="button" className="btn btn-danger" onClick={() => delMetric.mutate(m.id)}>
                  Remove
                </button>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </div>
  );
}

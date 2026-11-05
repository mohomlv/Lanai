"use client";

import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import type { Datasource, Page } from "@/lib/api/types";

export default function DatasourcesPage() {
  const qc = useQueryClient();
  const list = useQuery({
    queryKey: ["datasources"],
    queryFn: () => api<Page<Datasource> | Datasource[]>("/api/v1/datasources"),
  });

  const items = Array.isArray(list.data) ? list.data : list.data?.items ?? [];

  const remove = useMutation({
    mutationFn: (id: string) => api(`/api/v1/datasources/${id}`, { method: "DELETE" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["datasources"] }),
  });

  return (
    <div className="stack">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <div>
          <h1>Datasources</h1>
          <p className="muted">Connect customer databases, sync catalog, then scrape docs for AI.</p>
        </div>
        <Link className="btn btn-primary" href="/datasources/new">
          Add datasource
        </Link>
      </div>

      <div className="card">
        {list.isLoading && <p className="muted">Loading…</p>}
        {list.isError && <div className="error-banner">Failed to load datasources</div>}
        {!list.isLoading && items.length === 0 && <p className="muted">No datasources yet.</p>}
        {items.length > 0 && (
          <table className="table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Type</th>
                <th>Host</th>
                <th>Status</th>
                <th>Sharing</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {items.map((ds) => (
                <tr key={ds.id}>
                  <td>
                    <Link href={`/datasources/${ds.id}`}>{ds.name}</Link>
                  </td>
                  <td>
                    <span className="badge">{ds.type}</span>
                  </td>
                  <td className="mono">
                    {ds.host}:{ds.port}/{ds.database_name}
                  </td>
                  <td>
                    <span className={`badge ${ds.status === "ready" || ds.status === "ok" ? "badge-ok" : ""}`}>
                      {ds.status ?? "—"}
                    </span>
                  </td>
                  <td className="muted">{ds.ai_sharing_level ?? "schema_only"}</td>
                  <td>
                    <button
                      type="button"
                      className="btn btn-danger"
                      onClick={() => {
                        if (confirm(`Delete ${ds.name}?`)) remove.mutate(ds.id);
                      }}
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

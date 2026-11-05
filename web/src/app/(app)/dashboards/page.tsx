"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "@/lib/api/client";
import type { Dashboard, Page } from "@/lib/api/types";

export default function DashboardsPage() {
  const qc = useQueryClient();
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const list = useQuery({
    queryKey: ["dashboards"],
    queryFn: () => api<Page<Dashboard> | Dashboard[]>("/api/v1/dashboards"),
  });
  const items = Array.isArray(list.data) ? list.data : list.data?.items ?? [];

  const create = useMutation({
    mutationFn: () =>
      api<Dashboard>("/api/v1/dashboards", {
        method: "POST",
        body: JSON.stringify({ name: name.trim() || "New dashboard" }),
      }),
    onSuccess: (d) => {
      setName("");
      qc.invalidateQueries({ queryKey: ["dashboards"] });
      if (d.id) window.location.href = `/dashboards/${d.id}`;
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : "Could not create the dashboard"),
  });

  function onCreate(e: FormEvent) {
    e.preventDefault();
    setError(null);
    create.mutate();
  }

  return (
    <div className="stack">
      <h1>Dashboards</h1>
      {error && <div className="error-banner">{error}</div>}
      <form className="row" onSubmit={onCreate}>
        <input
          className="input"
          style={{ maxWidth: 280 }}
          placeholder="New dashboard name"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <button className="btn btn-primary" type="submit" disabled={create.isPending}>
          {create.isPending ? "Creating…" : "Create dashboard"}
        </button>
      </form>
      <div className="card">
        {items.length === 0 && <p className="muted">No dashboards yet.</p>}
        <ul>
          {items.map((d) => (
            <li key={d.id}>
              <Link href={`/dashboards/${d.id}`}>{d.name}</Link>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

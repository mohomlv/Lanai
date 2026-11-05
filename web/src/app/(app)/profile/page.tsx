"use client";

import { FormEvent, useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api/client";
import type { Me } from "@/lib/api/types";
import { useQuery, useQueryClient } from "@tanstack/react-query";

export default function ProfilePage() {
  const qc = useQueryClient();
  const me = useQuery({ queryKey: ["me"], queryFn: () => api<Me>("/api/v1/me") });
  const [username, setUsername] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const branches = me.data?.branch_ids ?? [];
  const memberships = me.data?.memberships ?? [];

  useEffect(() => {
    if (me.data?.username) setUsername(me.data.username);
  }, [me.data?.username]);

  async function save(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await api("/api/v1/me", { method: "PATCH", body: JSON.stringify({ username }) });
      setMsg("Username saved");
      qc.invalidateQueries({ queryKey: ["me"] });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save username");
    }
  }

  return (
    <div className="stack">
      <h1>Profile</h1>
      <section className="card stack">
        <p>
          {me.data?.display_name} · {me.data?.email}
        </p>
        <p className="muted">
          Role in the active company: {me.data?.role ?? "none"}
          {me.data?.platform_admin ? " · platform admin" : ""}
        </p>
        <form className="stack" onSubmit={save} style={{ maxWidth: 320 }}>
          <label className="label">
            Username
            <input className="input" value={username} onChange={(e) => setUsername(e.target.value)} />
          </label>
          <button className="btn" type="submit">
            Save username
          </button>
        </form>
        {error && <p className="muted">{error}</p>}
        {msg && <p className="muted">{msg}</p>}
      </section>

      <section className="card stack">
        <h2>My branches</h2>
        <p className="muted">These integer branch ids are used as @branches in SQL for the company you are in now.</p>
        {branches.length === 0 ? (
          <p className="muted">No branches assigned for this company.</p>
        ) : (
          <ul>
            {branches.map((id) => (
              <li key={id} className="mono">
                {id}
              </li>
            ))}
          </ul>
        )}
        {memberships.length > 1 && (
          <div className="stack">
            <h3>All companies</h3>
            {memberships.map((item) => (
              <p key={item.company_id}>
                <strong>{item.company_name || item.company_id}</strong>
                <span className="muted"> · {item.role} · branches {(item.branch_ids ?? []).join(", ") || "none"}</span>
              </p>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

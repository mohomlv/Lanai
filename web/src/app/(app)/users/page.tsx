"use client";

import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "@/lib/api/client";
import type { Me, MembershipView, Page, PlatformUser } from "@/lib/api/types";

export default function UsersPage() {
  const qc = useQueryClient();
  const me = useQuery({ queryKey: ["me"], queryFn: () => api<Me>("/api/v1/me") });
  const users = useQuery({
    queryKey: ["platform-users"],
    enabled: Boolean(me.data?.platform_admin),
    queryFn: () => api<Page<PlatformUser> | PlatformUser[]>("/api/v1/platform/users"),
  });
  const items = Array.isArray(users.data) ? users.data : users.data?.items ?? [];

  if (me.isFetched && !me.data?.platform_admin) {
    return (
      <div className="stack">
        <h1>Users</h1>
        <p className="muted">Only a platform admin can view every account. Set the email or branch code in application.yml.</p>
      </div>
    );
  }

  return (
    <div className="stack">
      <h1>Users</h1>
      <p className="muted">Every account in the system. Edit username, role, and integer branch ids.</p>
      <ul className="stack">
        {items.map((user) => (
          <li key={user.user_id} className="card stack">
            <UserEditor user={user} onSaved={() => qc.invalidateQueries({ queryKey: ["platform-users"] })} />
          </li>
        ))}
      </ul>
    </div>
  );
}

function UserEditor({ user, onSaved }: { user: PlatformUser; onSaved: () => void }) {
  const [username, setUsername] = useState(user.username ?? "");
  const [displayName, setDisplayName] = useState(user.display_name ?? "");
  const [memberships, setMemberships] = useState<MembershipView[]>(user.memberships ?? []);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function save() {
    setBusy(true);
    setError(null);
    try {
      await api(`/api/v1/platform/users/${user.user_id}`, {
        method: "PATCH",
        body: JSON.stringify({
          username: username.trim() || undefined,
          display_name: displayName.trim() || undefined,
          memberships: memberships.map((item) => ({
            company_id: item.company_id,
            role: item.role,
            branch_ids: item.branch_ids ?? [],
          })),
        }),
      });
      onSaved();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save user");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="stack">
      <p>
        <strong>{user.display_name || user.email}</strong>
        {user.platform_admin ? <span className="muted"> · platform admin</span> : null}
        <br />
        <span className="muted">{user.email}</span>
      </p>
      <div className="row" style={{ flexWrap: "wrap", alignItems: "flex-end" }}>
        <label className="label" style={{ minWidth: 180 }}>
          Username
          <input className="input" value={username} onChange={(e) => setUsername(e.target.value)} />
        </label>
        <label className="label" style={{ minWidth: 180 }}>
          Display name
          <input className="input" value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
        </label>
      </div>
      {(memberships.length === 0 ? [{ company_id: "", company_name: "No company", role: "", branch_ids: [] }] : memberships).map((item, index) =>
        item.company_id ? (
          <div key={item.company_id} className="row" style={{ flexWrap: "wrap", alignItems: "flex-end" }}>
            <label className="label" style={{ minWidth: 180 }}>
              {item.company_name || "Company"}
              <select
                className="select"
                value={item.role}
                disabled={item.role === "owner"}
                onChange={(e) => {
                  const role = e.target.value;
                  setMemberships((current) => current.map((row, i) => (i === index ? { ...row, role } : row)));
                }}
              >
                {(item.role === "owner" ? ["owner"] : ["admin", "editor", "viewer"]).map((role) => (
                  <option key={role} value={role}>
                    {role}
                  </option>
                ))}
              </select>
            </label>
            <label className="label" style={{ minWidth: 220 }}>
              Branches
              <input
                className="input"
                value={(item.branch_ids ?? []).join(", ")}
                onChange={(e) => {
                  const branch_ids = e.target.value
                    .split(/[,\s]+/)
                    .map((value) => value.trim())
                    .filter(Boolean)
                    .map((value) => Number(value))
                    .filter((value) => Number.isInteger(value));
                  setMemberships((current) => current.map((row, i) => (i === index ? { ...row, branch_ids } : row)));
                }}
                placeholder="10, 20, 30"
              />
            </label>
          </div>
        ) : (
          <p key="none" className="muted">
            This account is not in a company yet.
          </p>
        ),
      )}
      <button type="button" className="btn" disabled={busy} onClick={() => void save()}>
        {busy ? "Saving…" : "Save"}
      </button>
      {error && <p className="muted">{error}</p>}
    </div>
  );
}

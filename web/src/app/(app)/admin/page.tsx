"use client";

import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "@/lib/api/client";
import type { CompanyMember, Page } from "@/lib/api/types";
import { ParameterAccessEditor } from "@/components/ParameterAccessEditor";

export default function AdminPage() {
  const qc = useQueryClient();
  const [inviteEmail, setInviteEmail] = useState("");
  const [keyName, setKeyName] = useState("");

  const members = useQuery({
    queryKey: ["members"],
    queryFn: () => api<Page<CompanyMember> | CompanyMember[]>("/api/v1/company/members"),
  });
  const invites = useQuery({
    queryKey: ["invites"],
    queryFn: () => api<Page<{ id: string; email: string; status: string }> | unknown[]>("/api/v1/company/invitations"),
  });
  const keys = useQuery({
    queryKey: ["api-keys"],
    queryFn: () => api<Page<{ id: string; name: string; key_prefix: string }> | unknown[]>("/api/v1/api-keys"),
  });
  const audit = useQuery({
    queryKey: ["audit"],
    queryFn: () => api<Page<{ id: string; action: string; created_at: string }> | unknown[]>("/api/v1/audit-log"),
  });
  const runs = useQuery({
    queryKey: ["query-runs"],
    queryFn: () => api<Page<{ id: string; status: string }> | unknown[]>("/api/v1/query-runs"),
  });
  const quotas = useQuery({
    queryKey: ["quotas"],
    queryFn: () => api<Record<string, unknown>>("/api/v1/quotas"),
  });

  const memberItems = Array.isArray(members.data) ? members.data : (members.data as Page<unknown>)?.items ?? [];
  const inviteItems = Array.isArray(invites.data) ? invites.data : (invites.data as Page<unknown>)?.items ?? [];
  const keyItems = Array.isArray(keys.data) ? keys.data : (keys.data as Page<unknown>)?.items ?? [];
  const auditItems = Array.isArray(audit.data) ? audit.data : (audit.data as Page<unknown>)?.items ?? [];
  const runItems = Array.isArray(runs.data) ? runs.data : (runs.data as Page<unknown>)?.items ?? [];

  const invite = useMutation({
    mutationFn: () =>
      api("/api/v1/company/invitations", {
        method: "POST",
        body: JSON.stringify({ email: inviteEmail, role: "editor" }),
      }),
    onSuccess: () => {
      setInviteEmail("");
      qc.invalidateQueries({ queryKey: ["invites"] });
    },
  });

  const createKey = useMutation({
    mutationFn: () => api("/api/v1/api-keys", { method: "POST", body: JSON.stringify({ name: keyName }) }),
    onSuccess: () => {
      setKeyName("");
      qc.invalidateQueries({ queryKey: ["api-keys"] });
    },
  });

  return (
    <div className="stack">
      <h1>Admin</h1>
      <ParameterAccessEditor />

      <section className="card stack">
        <h2>Members</h2>
        <p className="muted">Each member can have a username and a list of integer branch ids used as @username and @branches in SQL.</p>
        <ul className="stack">
          {(memberItems as CompanyMember[]).map((m) => (
            <li key={m.user_id}>
              <MemberEditor member={m} onSaved={() => qc.invalidateQueries({ queryKey: ["members"] })} />
            </li>
          ))}
        </ul>
        <form
          className="row"
          onSubmit={(e: FormEvent) => {
            e.preventDefault();
            invite.mutate();
          }}
        >
          <input
            className="input"
            style={{ maxWidth: 260 }}
            placeholder="Invite email"
            value={inviteEmail}
            onChange={(e) => setInviteEmail(e.target.value)}
          />
          <button className="btn" type="submit">
            Invite
          </button>
        </form>
        <p className="muted">Pending invites: {(inviteItems as unknown[]).length}</p>
      </section>

      <section className="card stack">
        <h2>API keys</h2>
        <form
          className="row"
          onSubmit={(e) => {
            e.preventDefault();
            createKey.mutate();
          }}
        >
          <input
            className="input"
            style={{ maxWidth: 220 }}
            value={keyName}
            onChange={(e) => setKeyName(e.target.value)}
            placeholder="Key name"
          />
          <button className="btn" type="submit">
            Create
          </button>
        </form>
        <ul>
          {(keyItems as { id: string; name?: string; key_prefix?: string }[]).map((k) => (
            <li key={k.id} className="mono">
              {k.name} · {k.key_prefix}…
            </li>
          ))}
        </ul>
      </section>

      <section className="card stack">
        <h2>Quotas</h2>
        <pre className="mono" style={{ margin: 0, whiteSpace: "pre-wrap" }}>
          {JSON.stringify(quotas.data ?? {}, null, 2)}
        </pre>
      </section>

      <section className="card stack">
        <h2>Audit log</h2>
        <ul>
          {(auditItems as { id: string; action?: string; created_at?: string }[]).slice(0, 30).map((a) => (
            <li key={a.id}>
              {a.action} · {a.created_at}
            </li>
          ))}
        </ul>
      </section>

      <section className="card stack">
        <h2>Query runs</h2>
        <ul>
          {(runItems as { id: string; status?: string }[]).slice(0, 30).map((r) => (
            <li key={r.id} className="mono">
              {r.id} · {r.status}
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}

function MemberEditor({ member, onSaved }: { member: CompanyMember; onSaved: () => void }) {
  const [username, setUsername] = useState(member.username ?? "");
  const [branches, setBranches] = useState((member.branch_ids ?? []).join(", "));
  const [role, setRole] = useState(member.role ?? "viewer");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function save() {
    setBusy(true);
    setError(null);
    try {
      await api(`/api/v1/company/members/${member.user_id}`, {
        method: "PATCH",
        body: JSON.stringify({
          username: username.trim() || undefined,
          role: member.role === "owner" ? undefined : role,
          branch_ids: branches
            .split(/[,\s]+/)
            .map((item) => item.trim())
            .filter(Boolean)
            .map((item) => Number(item))
            .filter((item) => Number.isInteger(item)),
        }),
      });
      onSaved();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save member");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="stack" style={{ gap: "0.45rem" }}>
      <strong>{member.display_name || member.email}</strong>
      <span className="muted">{member.email}</span>
      <div className="row" style={{ alignItems: "flex-end", flexWrap: "wrap" }}>
        <label className="label" style={{ minWidth: 160 }}>
          Username
          <input className="input" value={username} onChange={(e) => setUsername(e.target.value)} />
        </label>
        <label className="label" style={{ minWidth: 200 }}>
          Branches
          <input
            className="input"
            value={branches}
            onChange={(e) => setBranches(e.target.value)}
            placeholder="10, 20, 30"
          />
        </label>
        <label className="label" style={{ minWidth: 140 }}>
          Role
          <select className="select" value={role} onChange={(e) => setRole(e.target.value)} disabled={member.role === "owner"}>
            {(member.role === "owner" ? ["owner"] : ["admin", "editor", "viewer"]).map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
          </select>
        </label>
        <button type="button" className="btn" disabled={busy} onClick={() => void save()}>
          {busy ? "Saving…" : "Save"}
        </button>
      </div>
      {error && <p className="muted">{error}</p>}
    </div>
  );
}

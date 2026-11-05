"use client";

import { FormEvent, Suspense, useEffect, useMemo, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { api, ApiError, authorizedFetch } from "@/lib/api/client";
import type { AiRequest, Datasource, Page, QueryResult } from "@/lib/api/types";
import { CHARTS, chartOption, scalarText } from "@/lib/charts";
import { SqlEditor } from "@/components/SqlEditor";
import { extractSql, USER_ATTRIBUTES, type CatalogName } from "@/lib/sql-names";
import ReactECharts from "echarts-for-react";
import Link from "next/link";

type ChatMsg = { role: "user" | "assistant"; content: string };

type ConversationSummary = { id: string; title: string; datasource_id?: string; provider_config_id?: string; updated_at?: string; created_at?: string };
type SavedAgent = { id: string; name: string; provider: string; default_model_id?: string; api_url?: string };

const doneStatuses = new Set(["ready", "failed", "needs_clarification", "accepted", "discarded"]);

function AskInner() {
  const params = useSearchParams();
  const router = useRouter();
  const initialDs = params.get("datasource") ?? "";
  const [datasourceId, setDatasourceId] = useState(initialDs);
  const [conversationId, setConversationId] = useState<string | null>(params.get("conversation"));
  const loadedConversation = useRef<string | null>(null);
  const [messages, setMessages] = useState<ChatMsg[]>([]);
  const [input, setInput] = useState("");
  const [draft, setDraft] = useState<AiRequest | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [acceptName, setAcceptName] = useState("AI question");
  const [chart, setChart] = useState("table");
  const [draftSql, setDraftSql] = useState("");
  const [checkedSql, setCheckedSql] = useState("");
  const [preview, setPreview] = useState<QueryResult | null>(null);
  const [agentId, setAgentId] = useState("");
  const [historyOpen, setHistoryOpen] = useState(false);
  const [names, setNames] = useState<CatalogName[]>([]);

  useEffect(() => {
    if (!datasourceId) {
      setNames(USER_ATTRIBUTES);
      return;
    }
    let cancelled = false;
    api<{ items: CatalogName[] }>(`/api/v1/datasources/${datasourceId}/autocomplete`)
      .then((res) => {
        if (!cancelled) setNames([...(res.items ?? []), ...USER_ATTRIBUTES]);
      })
      .catch(() => {
        if (!cancelled) setNames([]);
      });
    return () => {
      cancelled = true;
    };
  }, [datasourceId]);

  const datasources = useQuery({
    queryKey: ["datasources"],
    queryFn: () => api<Page<Datasource> | Datasource[]>("/api/v1/datasources"),
  });
  const dsItems = useMemo(() => {
    return Array.isArray(datasources.data) ? datasources.data : datasources.data?.items ?? [];
  }, [datasources.data]);
  const agents = useQuery({
    queryKey: ["ai-agents"],
    queryFn: () => api<{ items: SavedAgent[] }>("/api/v1/ai/agents"),
  });
  const history = useQuery({
    queryKey: ["ai-conversations"],
    queryFn: () => api<{ items: ConversationSummary[] }>("/api/v1/ai/conversations"),
  });

  useEffect(() => {
    if (agentId || !agents.data?.items.length) return;
    if (agents.data.items.length === 1) setAgentId(agents.data.items[0].id);
  }, [agentId, agents.data]);

  useEffect(() => {
    const id = params.get("conversation");
    if (!id || loadedConversation.current === id) return;
    loadedConversation.current = id;
    let cancelled = false;
      api<{ id: string; datasource_id?: string; provider_config_id?: string; messages: { role: "user" | "assistant"; content: string }[] }>(
      `/api/v1/ai/conversations/${id}`,
    )
      .then((conv) => {
        if (cancelled) return;
        setConversationId(conv.id);
        if (conv.datasource_id) setDatasourceId(conv.datasource_id);
        if (conv.provider_config_id) setAgentId(conv.provider_config_id);
        setMessages(conv.messages.map((message) => ({ role: message.role, content: message.content })));
        const lastSql = [...conv.messages].reverse().map((message) => extractSql(message.content)).find((sql) => sql);
        if (lastSql) {
          setDraftSql(lastSql);
          setCheckedSql("");
          setPreview(null);
        }
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [params]);

  function rememberConversation(id: string, datasource: string) {
    const query = new URLSearchParams();
    if (datasource) query.set("datasource", datasource);
    query.set("conversation", id);
    router.replace(`/ask?${query.toString()}`);
  }

  async function ensureConversation() {
    if (conversationId) return conversationId;
    if (!datasourceId) throw new Error("Pick a datasource");
    const conv = await api<{ id: string }>("/api/v1/ai/conversations", {
      method: "POST",
      body: JSON.stringify({ datasource_id: datasourceId, target_type: "question" }),
    });
    setConversationId(conv.id);
    loadedConversation.current = conv.id;
    rememberConversation(conv.id, datasourceId);
    return conv.id;
  }

  function replyText(req: AiRequest) {
    if (req.error_message) return `Error: ${req.error_message}`;
    if (req.result?.native_sql) return `${req.result.summary ?? "Draft SQL"}:\n${req.result.native_sql}`;
    return `Working… ${req.status}`;
  }

  async function streamRequest(id: string): Promise<AiRequest> {
    const res = await authorizedFetch(`/api/v1/ai/requests/${id}/events`);
    if (!res.ok || !res.body) {
      throw new ApiError(res.status, "STREAM", "Could not open the answer stream");
    }
    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    let latest: AiRequest = { id, status: "queued" };
    const show = (req: AiRequest) => {
      latest = req;
      const text = replyText(req);
      setMessages((m) => {
        const next = [...m];
        const last = next[next.length - 1];
        if (last?.role === "assistant") next[next.length - 1] = { role: "assistant", content: text };
        else next.push({ role: "assistant", content: text });
        return next;
      });
    };
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const parts = buffer.split("\n\n");
      buffer = parts.pop() ?? "";
      for (const part of parts) {
        const data = part
          .split("\n")
          .filter((line) => line.startsWith("data:"))
          .map((line) => line.slice(5).trim())
          .join("");
        if (!data) continue;
        try {
          show(JSON.parse(data) as AiRequest);
        } catch {
          // Ignore a partial SSE frame; the poller still has the request.
        }
      }
    }
    return latest;
  }

  async function waitForRequest(initial: AiRequest): Promise<AiRequest> {
    if (doneStatuses.has(initial.status)) {
      const text = replyText(initial);
      setMessages((m) => [...m, { role: "assistant", content: text }]);
      return initial;
    }
    const streamed = streamRequest(initial.id).catch(() => null);
    const deadline = Date.now() + 120_000;
    let latest = initial;
    while (Date.now() < deadline) {
      await new Promise((resolve) => setTimeout(resolve, 1000));
      latest = await api<AiRequest>(`/api/v1/ai/requests/${initial.id}`);
      const text = replyText(latest);
      setMessages((m) => {
        const next = [...m];
        const last = next[next.length - 1];
        if (last?.role === "assistant") next[next.length - 1] = { role: "assistant", content: text };
        else next.push({ role: "assistant", content: text });
        return next;
      });
      if (latest.status === "failed") {
        throw new Error(latest.error_message || "The answer failed");
      }
      if (doneStatuses.has(latest.status)) return latest;
    }
    const timedOut = (await streamed) ?? latest;
    if (timedOut.status === "failed") {
      throw new Error(timedOut.error_message || "The answer failed");
    }
    if (!doneStatuses.has(timedOut.status)) {
      throw new Error("The answer timed out. Try again.");
    }
    return timedOut;
  }

  async function onSend(e: FormEvent) {
    e.preventDefault();
    if (!input.trim()) return;
    setBusy(true);
    setError(null);
    setDraft(null);
    const content = input.trim();
    setInput("");
    setMessages((m) => [...m, { role: "user", content }]);
    try {
      const cid = await ensureConversation();
      const req = await api<AiRequest>(`/api/v1/ai/conversations/${cid}/messages`, {
        method: "POST",
        body: JSON.stringify({
          content,
          provider_config_id: agentId || undefined,
          viz_template_key: chart,
        }),
        idempotencyKey: crypto.randomUUID(),
      });
      const final = await waitForRequest(req);
      setDraft(final);
      setDraftSql(final.result?.native_sql ?? "");
      if (final.result?.viz?.template) setChart(final.result.viz.template);
      setCheckedSql("");
      setPreview(null);
      history.refetch();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : err instanceof Error ? err.message : "Failed");
    } finally {
      setBusy(false);
    }
  }

  async function checkDraft() {
    if (!draftSql.trim() || !datasourceId) return;
    setBusy(true);
    setError(null);
    setPreview(null);
    try {
      const res = await api<QueryResult>("/api/v1/queries/run", {
        method: "POST",
        body: JSON.stringify({
          datasource_id: datasourceId,
          mode: "native",
          native_sql: draftSql,
          parameter_definitions: draft?.result?.parameters ?? [],
          parameter_values: {},
        }),
      });
      setPreview(res);
      setCheckedSql(draftSql);
    } catch (err) {
      setCheckedSql("");
      setError(err instanceof ApiError ? err.message : "The query failed. Fix it before saving.");
    } finally {
      setBusy(false);
    }
  }

  async function accept() {
    if (!draft?.id) return;
    if (checkedSql !== draftSql || !preview) {
      setError("Check the query first. Save is available after a successful run.");
      return;
    }
    setBusy(true);
    try {
      const res = await api<AiRequest & { question_id?: string }>(`/api/v1/ai/requests/${draft.id}/accept`, {
        method: "POST",
        body: JSON.stringify({ name: acceptName, viz_template_key: chart, native_sql: draftSql }),
      });
      setDraft(res);
      setMessages((m) => [
        ...m,
        {
          role: "assistant",
          content: res.result?.question_id
            ? `Accepted as question ${res.result.question_id}`
            : "Accepted.",
        },
      ]);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Accept failed");
    } finally {
      setBusy(false);
    }
  }

  async function discard() {
    if (!draft?.id) return;
    await api(`/api/v1/ai/requests/${draft.id}/discard`, { method: "POST" });
    setDraft(null);
    setDraftSql("");
    setCheckedSql("");
    setPreview(null);
  }

  const queryChecked = !!preview && checkedSql === draftSql;
  const previewOption = preview ? chartOption(chart, preview) : null;

  const recentChats = [...(history.data?.items ?? [])].sort((a, b) =>
    String(b.updated_at ?? b.created_at ?? "").localeCompare(String(a.updated_at ?? a.created_at ?? "")),
  );

  return (
    <div className={`ask-layout${historyOpen ? " history-open" : ""}`}>
    <div className="stack">
      <div className="row" style={{ justifyContent: "space-between", alignItems: "flex-start" }}>
        <div>
          <h1>Ask AI</h1>
          <p className="muted">
            Natural language → SQL using base.md and each table document from the selected datasource.
          </p>
        </div>
        <button type="button" className="btn" onClick={() => setHistoryOpen((open) => !open)}>
          {historyOpen ? "Hide history" : "History"}
        </button>
      </div>

      <label className="label" style={{ maxWidth: 420 }}>
        Datasource
        <select
          className="select"
          value={datasourceId}
          onChange={(e) => {
            setDatasourceId(e.target.value);
            setConversationId(null);
            loadedConversation.current = null;
            setMessages([]);
            setDraft(null);
            setDraftSql("");
            setCheckedSql("");
            setPreview(null);
            router.replace(e.target.value ? `/ask?datasource=${e.target.value}` : "/ask");
          }}
          disabled={!!conversationId}
        >
          <option value="">Select…</option>
          {dsItems.map((d) => (
            <option key={d.id} value={d.id}>
              {d.name}
            </option>
          ))}
        </select>
      </label>

      <label className="label" style={{ maxWidth: 420 }}>
        Agent
        <select className="select" value={agentId} onChange={(e) => setAgentId(e.target.value)}>
          <option value="">Saved company agent…</option>
          {(agents.data?.items ?? []).map((agent) => (
            <option key={agent.id} value={agent.id}>
              {agent.name}
              {agent.default_model_id ? ` · ${agent.default_model_id}` : ""}
            </option>
          ))}
        </select>
      </label>
      {(agents.data?.items.length ?? 0) === 0 && (
        <p className="muted">
          No agent is saved for this company yet. Add a provider or custom API URL in Settings. The key stays in the database.
        </p>
      )}

      {error && <div className="error-banner">{error}</div>}

      <div className="card stack">
        <div className="chat-log">
          {messages.length === 0 && <p className="muted">Ask something like “top 10 customers by revenue last month”.</p>}
          {messages.map((m, i) => {
            const sql = m.role === "assistant" ? extractSql(m.content) : null;
            return (
              <button
                key={`${m.role}-${i}-${m.content.slice(0, 24)}`}
                type="button"
                className={`bubble ${m.role}${sql ? " pickable" : ""}`}
                onClick={() => {
                  if (!sql) return;
                  setDraftSql(sql);
                  const named = /Chart:\s*([a-z_]+)/i.exec(m.content);
                  if (named?.[1]) setChart(named[1].toLowerCase());
                  setCheckedSql("");
                  setPreview(null);
                  setError(null);
                }}
              >
                {m.content}
              </button>
            );
          })}
        </div>
        <form className="chat-compose" onSubmit={onSend}>
          <textarea
            className="textarea"
            rows={5}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                e.currentTarget.form?.requestSubmit();
              }
            }}
            placeholder="Ask a question… Enter sends, Shift+Enter adds a line."
            disabled={busy || !datasourceId}
          />
          <div className="stack" style={{ width: 180 }}>
            <label className="label">
              Chart
              <select className="select" value={chart} onChange={(e) => setChart(e.target.value)}>
                {CHARTS.map((item) => (
                  <option key={item.key} value={item.key}>
                    {item.label}
                  </option>
                ))}
                {!CHARTS.some((item) => item.key === chart) && <option value={chart}>{chart}</option>}
              </select>
            </label>
            <button className="btn btn-primary" type="submit" disabled={busy || !datasourceId}>
              {busy ? "Working…" : "Send"}
            </button>
          </div>
        </form>
      </div>

      {draftSql && (
        <div className="card stack">
          <h2>Test query</h2>
          <p className="muted">
            Click a chat answer to load its SQL here. Tab opens saved table names. Check the query before saving.
          </p>
          <SqlEditor
            value={draftSql}
            names={names}
            height="220px"
            onChange={(sql) => {
              setDraftSql(sql);
              if (sql !== checkedSql) setPreview(null);
            }}
          />
          <div className="row">
            <button type="button" className="btn btn-primary" onClick={checkDraft} disabled={busy || !draftSql.trim()}>
              {busy ? "Checking…" : "Check query"}
            </button>
            <input
              className="input"
              style={{ maxWidth: 280 }}
              value={acceptName}
              onChange={(e) => setAcceptName(e.target.value)}
              placeholder="Question name"
            />
            <button type="button" className="btn btn-primary" onClick={accept} disabled={busy || !queryChecked || !draft?.id}>
              Save question
            </button>
            {draft?.id && (
              <button type="button" className="btn" onClick={discard}>
                Discard
              </button>
            )}
            {draft?.result?.question_id && (
              <Link href={`/questions/${draft.result.question_id}`}>Open question</Link>
            )}
          </div>
          {queryChecked && (
            <p className="muted">
              Query is valid{preview.duration_ms != null ? ` · ${preview.duration_ms} ms` : ""}
              {preview.truncated ? " · truncated" : ""} · {preview.rows.length} row{preview.rows.length === 1 ? "" : "s"}
            </p>
          )}
          {preview && (chart === "scalar" || chart === "scalar_comparison") && (
            <div className="card">
              <p className="muted">{preview.columns[0]?.name}</p>
              <p style={{ fontSize: 40, margin: 0 }}>{scalarText(preview)}</p>
            </div>
          )}
          {preview && previewOption && (
            <div className="card">
              <ReactECharts option={previewOption} style={{ height: 280 }} />
            </div>
          )}
          {preview && preview.columns.length > 0 && (
            <div className="card" style={{ overflow: "auto" }}>
              <table className="table">
                <thead>
                  <tr>
                    {preview.columns.map((column) => (
                      <th key={column.name}>{column.name}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {preview.rows.slice(0, 50).map((row, rowIndex) => (
                    <tr key={`preview-${rowIndex}-${String(row[0] ?? "")}`}>
                      {row.map((cell, cellIndex) => (
                        <td key={`${preview.columns[cellIndex]?.name ?? cellIndex}`} className="mono">
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
      )}
    </div>
    {historyOpen && <aside className="card stack">
      <h2>Recent chats</h2>
      {recentChats.length === 0 && <p className="muted">No chats yet.</p>}
      {recentChats.map((item) => (
        <button
          key={item.id}
          type="button"
          className={`btn${item.id === conversationId ? " btn-primary" : ""}`}
          onClick={() => {
            if (item.datasource_id) setDatasourceId(item.datasource_id);
            rememberConversation(item.id, item.datasource_id ?? datasourceId);
          }}
        >
          {item.title || "Untitled"}
        </button>
      ))}
    </aside>}
    </div>
  );
}

export default function AskPage() {
  return (
    <Suspense>
      <AskInner />
    </Suspense>
  );
}

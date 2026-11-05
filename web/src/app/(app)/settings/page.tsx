"use client";

import { FormEvent, useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api, ApiError, setAuthTokens } from "@/lib/api/client";
import type { AuthTokens, Me, Page } from "@/lib/api/types";

function VerifyEmailHint() {
  const [token, setToken] = useState("");
  const [msg, setMsg] = useState<string | null>(null);
  async function verify(e: FormEvent) {
    e.preventDefault();
    try {
      await api("/api/v1/auth/verify-email", {
        method: "POST",
        skipAuth: true,
        body: JSON.stringify({ token }),
      });
      setMsg("Email verified — create a company below.");
    } catch (err) {
      setMsg(err instanceof ApiError ? err.message : "Verify failed");
    }
  }
  return (
    <form className="stack" onSubmit={verify}>
      <p className="muted">Paste the verify token from signup (`dev_token`) to unlock company creation.</p>
      <input className="input" value={token} onChange={(e) => setToken(e.target.value)} placeholder="verify token" />
      <button className="btn" type="submit">
        Verify email
      </button>
      {msg && <p className="muted">{msg}</p>}
    </form>
  );
}

export default function SettingsPage() {
  const me = useQuery({ queryKey: ["me"], queryFn: () => api<Me>("/api/v1/me") });
  const companies = useQuery({
    queryKey: ["companies"],
    queryFn: () => api<Page<{ id: string; name: string }> | { id: string; name: string }[]>("/api/v1/companies"),
  });
  const [provider, setProvider] = useState("openai");
  const [baseUrl, setBaseUrl] = useState("");
  const [apiKey, setApiKey] = useState("");
  const [model, setModel] = useState("");
  const [queryModel, setQueryModel] = useState("");
  const [docModel, setDocModel] = useState("");
  const [dashboardModel, setDashboardModel] = useState("");
  const [liveModels, setLiveModels] = useState<{ id: string; name: string }[]>([]);
  const [endpointName, setEndpointName] = useState("");
  const [endpointPrefix, setEndpointPrefix] = useState("");
  const [endpointType, setEndpointType] = useState("openai-compatible");
  const [endpointApiType, setEndpointApiType] = useState("chat");
  const [endpointUrl, setEndpointUrl] = useState("https://api.openai.com/v1");
  const [endpointKey, setEndpointKey] = useState("");
  const [endpointModel, setEndpointModel] = useState("");
  const [companyName, setCompanyName] = useState("");
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const companyItems = Array.isArray(companies.data) ? companies.data : companies.data?.items ?? [];

  const providerCatalog = useQuery({
    queryKey: ["ai-providers"],
    queryFn: () =>
      api<{
        items: {
          id: string;
          name: string;
          format: string;
          chat_url: string;
          no_auth: boolean;
          base_url_editable: boolean;
          models: { id: string; name: string }[];
        }[];
      }>("/api/v1/ai/providers"),
  });
  const selected = providerCatalog.data?.items.find((item) => item.id === provider);
  const savedAgents = useQuery({
    queryKey: ["ai-agents"],
    queryFn: () =>
      api<{ items: { id: string; name: string; provider: string; default_model_id?: string; api_url?: string; has_api_key?: boolean }[] }>(
        "/api/v1/ai/agents",
      ),
  });
  const endpoints = useQuery({
    queryKey: ["ai-endpoints"],
    queryFn: () =>
      api<{
        items: {
          id: string;
          name: string;
          prefix: string;
          type: string;
          api_type: string;
          api_url: string;
          default_model_id: string;
          active: boolean;
        }[];
      }>("/api/v1/ai/endpoints"),
  });

  useEffect(() => {
    api<{
      provider?: string;
      default_model_id?: string;
      has_api_key?: boolean;
      settings?: { base_url?: string; agents?: Record<string, string> };
    }>("/api/v1/ai/provider-config")
      .then((c) => {
        if (c.provider) setProvider(c.provider);
        if (c.default_model_id) setModel(c.default_model_id);
        if (c.settings?.base_url) setBaseUrl(c.settings.base_url);
        if (c.settings?.agents?.query_writer) setQueryModel(c.settings.agents.query_writer);
        if (c.settings?.agents?.description_writer) setDocModel(c.settings.agents.description_writer);
        if (c.settings?.agents?.dashboard_builder) setDashboardModel(c.settings.agents.dashboard_builder);
        if (c.has_api_key) setMsg("Model provider is configured");
      })
      .catch(() => undefined);
  }, []);

  function providerBody() {
    return {
      provider,
      api_key: apiKey,
      base_url: baseUrl || selected?.chat_url || "",
      default_model_id: model,
      agents: {
        query_writer: queryModel || model,
        description_writer: docModel || model,
        dashboard_builder: dashboardModel || model,
      },
    };
  }

  async function saveProvider(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await api("/api/v1/ai/provider-config", {
        method: "PUT",
        body: JSON.stringify(providerBody()),
      });
      setMsg("Provider saved for this company. The key is stored encrypted and is not shown again.");
      setApiKey("");
      savedAgents.refetch();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Save failed");
    }
  }

  async function loadModels() {
    setError(null);
    try {
      const res = await api<{ items: { id: string; name: string }[] }>("/api/v1/ai/providers/models", {
        method: "POST",
        body: JSON.stringify(providerBody()),
      });
      setLiveModels(res.items);
      if (res.items[0] && !model) setModel(res.items[0].id);
      setMsg(res.items.length ? `Found ${res.items.length} models` : "No live model list; use a model id from the provider");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not list models");
    }
  }

  async function testProvider() {
    setError(null);
    try {
      const res = await api<{ reply: string }>("/api/v1/ai/provider-config/test", {
        method: "POST",
        body: JSON.stringify(providerBody()),
      });
      setMsg(`Connected: ${res.reply}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Connection test failed");
    }
  }

  async function createCompany(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const res = await api<{ company: { id: string }; access_token?: string; refresh_token?: string }>(
        "/api/v1/companies",
        { method: "POST", body: JSON.stringify({ name: companyName }) },
      );
      if (res.access_token && res.refresh_token) setAuthTokens(res.access_token, res.refresh_token);
      setMsg(`Company created`);
      setCompanyName("");
      companies.refetch();
      me.refetch();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Create failed");
    }
  }

  async function switchCompany(id: string) {
    try {
      const tokens = await api<AuthTokens>(`/api/v1/companies/${id}/switch`, { method: "POST" });
      setAuthTokens(tokens.access_token, tokens.refresh_token);
      window.location.reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Switch failed");
    }
  }

  return (
    <div className="stack">
      <h1>Settings</h1>
      {error && <div className="error-banner">{error}</div>}
      {msg && <p className="muted">{msg}</p>}

      <section className="card stack">
        <h2>Profile</h2>
        <p>
          {me.data?.display_name} · {me.data?.email}
        </p>
        <p className="muted">
          Username: {me.data?.username || "not set"}
          {me.data?.platform_admin ? " · platform admin" : ""}
        </p>
        <p className="muted">
          My branches: {(me.data?.branch_ids ?? []).length ? (me.data?.branch_ids ?? []).join(", ") : "none"}
        </p>
        <p className="muted">
          Active company: {me.data?.company_id ?? "none"} · role {me.data?.role ?? "—"}
          {me.data?.email_verified === false ? " · email not verified" : ""}
        </p>
        {me.data?.email_verified === false && (
          <VerifyEmailHint />
        )}
      </section>

      <section className="card stack">
        <h2>Companies</h2>
        <ul>
          {companyItems.map((c) => (
            <li key={c.id} className="row" style={{ justifyContent: "space-between" }}>
              <span>{c.name}</span>
              <button type="button" className="btn" onClick={() => switchCompany(c.id)}>
                Switch
              </button>
            </li>
          ))}
        </ul>
        <form className="row" onSubmit={createCompany}>
          <input
            className="input"
            style={{ maxWidth: 240 }}
            placeholder="New company name"
            value={companyName}
            onChange={(e) => setCompanyName(e.target.value)}
          />
          <button className="btn" type="submit">
            Create company
          </button>
        </form>
      </section>

      <section className="card stack">
        <h2>AI providers and agents</h2>
        <p className="muted">
          Same connection styles as the model router: OpenAI-compatible chat, Anthropic messages, Gemini, Azure, and
          Ollama (cloud or local). The key is encrypted and never shown again.
        </p>
        <form className="stack" onSubmit={saveProvider}>
          <label className="label">
            Provider
            <select
              className="select"
              value={provider}
              onChange={(e) => {
                const next = providerCatalog.data?.items.find((item) => item.id === e.target.value);
                setProvider(e.target.value);
                setLiveModels(next?.models ?? []);
                if (next?.chat_url) setBaseUrl(next.chat_url);
                if (next?.models[0]) setModel(next.models[0].id);
              }}
            >
              {(providerCatalog.data?.items ?? []).map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </label>
          {(selected?.base_url_editable || selected?.format === "ollama" || selected?.format === "azure" || provider.includes("compatible")) && (
            <label className="label">
              API URL
              <input
                className="input"
                value={baseUrl}
                onChange={(e) => setBaseUrl(e.target.value)}
                placeholder="https://host/v1"
              />
            </label>
          )}
          {!selected?.no_auth && (
            <label className="label">
              API key
              <input
                className="input"
                type="password"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder="Leave blank to keep the saved key"
              />
            </label>
          )}
          <label className="label">
            Default model
            <input className="input" list="provider-models" value={model} onChange={(e) => setModel(e.target.value)} />
            <datalist id="provider-models">
              {(liveModels.length ? liveModels : selected?.models ?? []).map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </datalist>
          </label>
          <div className="grid-2">
            <label className="label">
              Query writer
              <input className="input" value={queryModel} onChange={(e) => setQueryModel(e.target.value)} placeholder={model || "same as default"} />
            </label>
            <label className="label">
              Description writer
              <input className="input" value={docModel} onChange={(e) => setDocModel(e.target.value)} placeholder={model || "same as default"} />
            </label>
            <label className="label">
              Dashboard builder
              <input className="input" value={dashboardModel} onChange={(e) => setDashboardModel(e.target.value)} placeholder={model || "same as default"} />
            </label>
          </div>
          <div className="row">
            <button className="btn" type="button" onClick={() => void loadModels()}>
              List models
            </button>
            <button className="btn" type="button" onClick={() => void testProvider()}>
              Test connection
            </button>
            <button className="btn btn-primary" type="submit">
              Save provider
            </button>
          </div>
        </form>
      </section>

      <section className="card stack">
        <h2>Saved for this company</h2>
        <p className="muted">Keys stay encrypted in the database. Ask AI lists these agents so a question can use one of them.</p>
        <ul>
          {(savedAgents.data?.items ?? []).map((agent) => (
            <li key={agent.id}>
              <strong>{agent.name}</strong>
              <span className="muted">
                {" "}
                · {agent.provider}
                {agent.default_model_id ? ` · ${agent.default_model_id}` : ""}
                {agent.api_url ? ` · ${agent.api_url}` : ""}
                {agent.has_api_key ? " · key saved" : ""}
              </span>
            </li>
          ))}
          {(savedAgents.data?.items.length ?? 0) === 0 && <li className="muted">Nothing saved yet.</li>}
        </ul>
      </section>

      <section className="card stack">
        <h2>Custom endpoints</h2>
        <p className="muted">
          Add your own API URL, the same way a compatible node is added in the model router. OpenAI-style URLs should
          end in /v1 (the chat or responses path is added for you). Anthropic-style URLs should end in /v1 (/messages is
          added). Ollama can be http://localhost:11434.
        </p>
        <ul>
          {(endpoints.data?.items ?? []).map((item) => (
            <li key={item.id} className="row" style={{ justifyContent: "space-between", gap: "0.75rem" }}>
              <span>
                <strong>{item.name}</strong>
                {item.active ? " · in use" : ""}
                <br />
                <span className="muted mono">
                  {item.type} · {item.api_url}
                  {item.default_model_id ? ` · ${item.default_model_id}` : ""}
                </span>
              </span>
              <span className="row">
                <button
                  type="button"
                  className="btn"
                  onClick={() =>
                    void api(`/api/v1/ai/endpoints/${item.id}/use`, { method: "POST" }).then(() => {
                      setMsg(`${item.name} is the endpoint agents will call`);
                      endpoints.refetch();
                    })
                  }
                >
                  Use
                </button>
                <button
                  type="button"
                  className="btn"
                  onClick={() =>
                    void api(`/api/v1/ai/endpoints/${item.id}`, { method: "DELETE" }).then(() => endpoints.refetch())
                  }
                >
                  Remove
                </button>
              </span>
            </li>
          ))}
        </ul>
        <form
          className="stack"
          onSubmit={(e) => {
            e.preventDefault();
            setError(null);
            void api("/api/v1/ai/endpoints", {
              method: "POST",
              body: JSON.stringify({
                name: endpointName,
                prefix: endpointPrefix,
                type: endpointType,
                api_type: endpointApiType,
                api_url: endpointUrl,
                api_key: endpointKey,
                default_model_id: endpointModel,
              }),
            })
              .then(() => {
                setMsg("Custom endpoint saved for this company");
                setEndpointKey("");
                setEndpointName("");
                endpoints.refetch();
                savedAgents.refetch();
              })
              .catch((err) => setError(err instanceof ApiError ? err.message : "Could not add endpoint"));
          }}
        >
          <label className="label">
            Name
            <input className="input" value={endpointName} onChange={(e) => setEndpointName(e.target.value)} required />
          </label>
          <label className="label">
            Prefix
            <input
              className="input"
              value={endpointPrefix}
              onChange={(e) => setEndpointPrefix(e.target.value)}
              placeholder="oc-prod"
            />
          </label>
          <label className="label">
            Type
            <select
              className="select"
              value={endpointType}
              onChange={(e) => {
                setEndpointType(e.target.value);
                if (e.target.value === "anthropic-compatible") setEndpointUrl("https://api.anthropic.com/v1");
                else if (e.target.value === "ollama") setEndpointUrl("http://localhost:11434");
                else setEndpointUrl("https://api.openai.com/v1");
              }}
            >
              <option value="openai-compatible">OpenAI compatible</option>
              <option value="anthropic-compatible">Anthropic compatible</option>
              <option value="ollama">Ollama</option>
            </select>
          </label>
          {endpointType === "openai-compatible" && (
            <label className="label">
              API type
              <select className="select" value={endpointApiType} onChange={(e) => setEndpointApiType(e.target.value)}>
                <option value="chat">Chat completions</option>
                <option value="responses">Responses API</option>
              </select>
            </label>
          )}
          <label className="label">
            API URL
            <input
              className="input"
              value={endpointUrl}
              onChange={(e) => setEndpointUrl(e.target.value)}
              placeholder="https://your-host/v1"
              required
            />
          </label>
          {endpointType !== "ollama" && (
            <label className="label">
              API key
              <input className="input" type="password" value={endpointKey} onChange={(e) => setEndpointKey(e.target.value)} />
            </label>
          )}
          <label className="label">
            Model
            <input className="input" value={endpointModel} onChange={(e) => setEndpointModel(e.target.value)} placeholder="model id on that server" />
          </label>
          <div className="row">
            <button
              className="btn"
              type="button"
              onClick={() => {
                setError(null);
                const providerId = endpointType === "ollama" ? "ollama-local" : endpointType;
                void api<{ reply: string }>("/api/v1/ai/provider-config/test", {
                  method: "POST",
                  body: JSON.stringify({
                    provider: providerId,
                    api_url: endpointUrl,
                    api_type: endpointApiType,
                    api_key: endpointKey,
                    default_model_id: endpointModel,
                  }),
                })
                  .then((res) => setMsg(`Connected: ${res.reply}`))
                  .catch((err) => setError(err instanceof ApiError ? err.message : "Connection test failed"));
              }}
            >
              Test URL
            </button>
            <button className="btn btn-primary" type="submit">
              Add endpoint
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

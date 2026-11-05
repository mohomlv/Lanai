"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { api, ApiError, setAuthTokens } from "@/lib/api/client";
import type { AuthTokens } from "@/lib/api/types";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const tokens = await api<AuthTokens>("/api/v1/auth/login", {
        method: "POST",
        skipAuth: true,
        body: JSON.stringify({ email, password }),
      });
      setAuthTokens(tokens.access_token, tokens.refresh_token);
      router.replace(tokens.company_id ? "/datasources" : "/settings");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Login failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-page">
      <form className="card auth-card stack" onSubmit={onSubmit}>
        <div>
          <h1>Landata</h1>
          <p className="muted">Sign in to your workspace</p>
        </div>
        {error && <div className="error-banner">{error}</div>}
        <label className="label">
          Email
          <input className="input" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <label className="label">
          Password
          <input
            className="input"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        <button className="btn btn-primary" type="submit" disabled={loading}>
          {loading ? "Signing in…" : "Sign in"}
        </button>
        <div className="row muted">
          <Link href="/signup">Create account</Link>
          <Link href="/forgot-password">Forgot password</Link>
        </div>
      </form>
    </div>
  );
}

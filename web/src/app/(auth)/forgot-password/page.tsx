"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { api, ApiError } from "@/lib/api/client";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [devToken, setDevToken] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const res = await api<{ status?: string; dev_token?: string }>("/api/v1/auth/forgot-password", {
        method: "POST",
        skipAuth: true,
        body: JSON.stringify({ email }),
      });
      setMessage("If that email exists, a reset link was sent.");
      if (res.dev_token) setDevToken(res.dev_token);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Request failed");
    }
  }

  return (
    <div className="auth-page">
      <form className="card auth-card stack" onSubmit={onSubmit}>
        <h1>Forgot password</h1>
        {error && <div className="error-banner">{error}</div>}
        {message && <p className="muted">{message}</p>}
        {devToken && (
          <p className="mono">
            Dev token: <Link href={`/reset-password?token=${encodeURIComponent(devToken)}`}>{devToken}</Link>
          </p>
        )}
        <label className="label">
          Email
          <input className="input" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <button className="btn btn-primary" type="submit">
          Send reset
        </button>
        <Link href="/login">Back to login</Link>
      </form>
    </div>
  );
}

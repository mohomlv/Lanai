"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api/client";

export default function SignupPage() {
  const router = useRouter();
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      await api("/api/v1/auth/signup", {
        method: "POST",
        skipAuth: true,
        body: JSON.stringify({
          email,
          password,
          display_name: displayName,
        }),
      });
      router.replace("/login");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Signup failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-page">
      <form className="card auth-card stack" onSubmit={onSubmit}>
        <div>
          <h1>Create account</h1>
          <p className="muted">Start connecting databases</p>
        </div>
        {error && <div className="error-banner">{error}</div>}
        <label className="label">
          Display name
          <input className="input" value={displayName} onChange={(e) => setDisplayName(e.target.value)} required />
        </label>
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
            minLength={8}
          />
        </label>
        <p className="muted">After signup you sign in, then create or pick a company.</p>
        <button className="btn btn-primary" type="submit" disabled={loading}>
          {loading ? "Creating…" : "Sign up"}
        </button>
        <Link href="/login" className="muted">
          Already have an account?
        </Link>
      </form>
    </div>
  );
}

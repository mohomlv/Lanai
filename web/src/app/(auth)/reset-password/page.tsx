"use client";

import Link from "next/link";
import { FormEvent, useState, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { api, ApiError } from "@/lib/api/client";

function ResetForm() {
  const params = useSearchParams();
  const router = useRouter();
  const [token, setToken] = useState(params.get("token") ?? "");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await api("/api/v1/auth/reset-password", {
        method: "POST",
        skipAuth: true,
        body: JSON.stringify({ token, password }),
      });
      router.replace("/login");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Reset failed");
    }
  }

  return (
    <form className="card auth-card stack" onSubmit={onSubmit}>
      <h1>Reset password</h1>
      {error && <div className="error-banner">{error}</div>}
      <label className="label">
        Token
        <input className="input" value={token} onChange={(e) => setToken(e.target.value)} required />
      </label>
      <label className="label">
        New password
        <input
          className="input"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          minLength={8}
        />
      </label>
      <button className="btn btn-primary" type="submit">
        Update password
      </button>
      <Link href="/login">Back to login</Link>
    </form>
  );
}

export default function ResetPasswordPage() {
  return (
    <div className="auth-page">
      <Suspense>
        <ResetForm />
      </Suspense>
    </div>
  );
}

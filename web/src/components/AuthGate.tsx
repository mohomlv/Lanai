"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { hasAccessToken } from "@/lib/api/client";

export function AuthGate({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!hasAccessToken()) {
      router.replace("/login");
      return;
    }
    setReady(true);
  }, [router]);

  if (!ready) {
    return (
      <div className="auth-page">
        <p className="muted">Loading…</p>
      </div>
    );
  }

  return <>{children}</>;
}

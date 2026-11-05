"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { hasAccessToken } from "@/lib/api/client";

export default function Home() {
  const router = useRouter();
  useEffect(() => {
    router.replace(hasAccessToken() ? "/datasources" : "/login");
  }, [router]);
  return (
    <div className="auth-page">
      <p className="muted">Redirecting…</p>
    </div>
  );
}

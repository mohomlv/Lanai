"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { api, clearAuthTokens, getRefreshToken, setAuthTokens } from "@/lib/api/client";
import type { AuthTokens, Me, Page } from "@/lib/api/types";

const links: { href: string; label: string; roles?: string[]; platform?: boolean }[] = [
  { href: "/ask", label: "Ask AI" },
  { href: "/datasources", label: "Data sources" },
  { href: "/dashboards", label: "Dashboards" },
  { href: "/analytics", label: "Folders" },
  { href: "/settings", label: "Settings" },
  { href: "/profile", label: "Profile" },
  { href: "/admin", label: "Admin", roles: ["admin", "owner"] },
  { href: "/users", label: "Users", platform: true },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const me = useQuery({
    queryKey: ["me"],
    queryFn: () => api<Me>("/api/v1/me"),
  });
  const companies = useQuery({
    queryKey: ["companies"],
    queryFn: () => api<Page<{ id: string; name: string; role?: string }> | { id: string; name: string; role?: string }[]>("/api/v1/companies"),
  });
  const companyItems = Array.isArray(companies.data) ? companies.data : companies.data?.items ?? [];

  function logout() {
    const refresh = getRefreshToken();
    if (refresh) {
      void api("/api/v1/auth/logout", { method: "POST", body: JSON.stringify({ refresh_token: refresh }) }).catch(() => undefined);
    }
    clearAuthTokens();
    router.replace("/login");
  }

  async function switchCompany(id: string) {
    const tokens = await api<AuthTokens>(`/api/v1/companies/${id}/switch`, { method: "POST" });
    setAuthTokens(tokens.access_token, tokens.refresh_token);
    window.location.reload();
  }

  return (
    <div className="shell">
      <aside className="nav">
        <Link href="/ask" className="nav-brand">
          Landata
        </Link>
        <label className="label">
          Choose role
          <select
            className="select"
            value={me.data?.company_id ?? ""}
            onChange={(e) => {
              if (e.target.value) void switchCompany(e.target.value);
            }}
          >
            {!me.data?.company_id && <option value="">Choose…</option>}
            {companyItems.map((company) => (
              <option key={company.id} value={company.id}>
                {company.name}
                {company.role ? ` · ${company.role}` : ""}
              </option>
            ))}
          </select>
        </label>
        <nav className="stack" style={{ gap: "0.2rem" }}>
          {links
            .filter((link) => {
              if (link.platform) return Boolean(me.data?.platform_admin);
              if (link.roles) {
                return Boolean(me.data?.platform_admin) || (me.data?.role != null && link.roles.includes(me.data.role));
              }
              return true;
            })
            .map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className={`nav-link${pathname === link.href || pathname.startsWith(link.href + "/") ? " active" : ""}`}
            >
              {link.label}
            </Link>
          ))}
        </nav>
        <div className="stack" style={{ marginTop: "auto" }}>
          <span className="muted" style={{ fontSize: "0.8rem" }}>
            {me.data?.display_name || me.data?.email || "…"}
            {me.data?.role ? ` · ${me.data.role}` : ""}
          </span>
          <button type="button" className="btn" onClick={logout}>
            Logout
          </button>
        </div>
      </aside>
      <main className="main">{children}</main>
    </div>
  );
}

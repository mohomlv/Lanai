export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public body?: unknown,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

type TokenStore = {
  getAccess: () => string | null;
  getRefresh: () => string | null;
  setTokens: (access: string, refresh: string) => void;
  clear: () => void;
};

let tokenStore: TokenStore = {
  getAccess: () => (typeof window === "undefined" ? null : localStorage.getItem("landata_access")),
  getRefresh: () => (typeof window === "undefined" ? null : localStorage.getItem("landata_refresh")),
  setTokens: (access, refresh) => {
    localStorage.setItem("landata_access", access);
    localStorage.setItem("landata_refresh", refresh);
  },
  clear: () => {
    localStorage.removeItem("landata_access");
    localStorage.removeItem("landata_refresh");
  },
};

export function configureTokenStore(store: TokenStore) {
  tokenStore = store;
}

let refreshPromise: Promise<boolean> | null = null;

async function refreshAccess(): Promise<boolean> {
  const refresh = tokenStore.getRefresh();
  if (!refresh) return false;
  if (!refreshPromise) {
    refreshPromise = (async () => {
      try {
        const res = await fetch("/api/v1/auth/refresh", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ refresh_token: refresh }),
        });
        if (!res.ok) {
          tokenStore.clear();
          return false;
        }
        const data = await res.json();
        tokenStore.setTokens(data.access_token, data.refresh_token ?? refresh);
        return true;
      } catch {
        tokenStore.clear();
        return false;
      } finally {
        refreshPromise = null;
      }
    })();
  }
  return refreshPromise;
}

async function expiredSession(res: Response) {
  const text = await res.clone().text();
  if (!text) return true;
  try {
    const data = JSON.parse(text) as { code?: string };
    return !data.code || data.code === "UNAUTHENTICATED";
  } catch {
    return true;
  }
}

function redirectToLogin() {
  if (typeof window === "undefined") return;
  const path = window.location.pathname;
  if (path.startsWith("/login") || path.startsWith("/signup")) return;
  window.location.assign("/login");
}

export async function authorizedFetch(
  path: string,
  options: RequestInit & { skipAuth?: boolean; idempotencyKey?: string } = {},
): Promise<Response> {
  const headers = new Headers(options.headers);
  if (!headers.has("Content-Type") && options.body) {
    headers.set("Content-Type", "application/json");
  }
  if (!options.skipAuth) {
    const access = tokenStore.getAccess();
    if (access) headers.set("Authorization", `Bearer ${access}`);
  }
  if (options.idempotencyKey) {
    headers.set("Idempotency-Key", options.idempotencyKey);
  }

  const { skipAuth, idempotencyKey: _idempotencyKey, ...init } = options;
  void _idempotencyKey;
  const url = path.startsWith("/") ? path : `/api/v1/${path}`;
  let res = await fetch(url, { ...init, headers });

  if ((res.status === 401 || (res.status === 403 && (await expiredSession(res)))) && !skipAuth) {
    const ok = await refreshAccess();
    if (!ok) {
      redirectToLogin();
      return res;
    }
    const access = tokenStore.getAccess();
    if (access) headers.set("Authorization", `Bearer ${access}`);
    res = await fetch(url, { ...init, headers });
    if (res.status === 401 || (res.status === 403 && (await expiredSession(res)))) {
      tokenStore.clear();
      redirectToLogin();
    }
  }
  return res;
}

export async function api<T = unknown>(
  path: string,
  options: RequestInit & { skipAuth?: boolean; idempotencyKey?: string } = {},
): Promise<T> {
  const res = await authorizedFetch(path, options);

  if (res.status === 204) return undefined as T;

  const text = await res.text();
  const data = text ? JSON.parse(text) : null;

  if (!res.ok) {
    throw new ApiError(
      res.status,
      data?.code ?? data?.title ?? "ERROR",
      data?.detail ?? data?.title ?? res.statusText,
      data,
    );
  }
  return data as T;
}

export function setAuthTokens(access: string, refresh: string) {
  tokenStore.setTokens(access, refresh);
}

export function clearAuthTokens() {
  tokenStore.clear();
}

export function hasAccessToken() {
  return !!tokenStore.getAccess();
}

export function getRefreshToken() {
  return tokenStore.getRefresh();
}

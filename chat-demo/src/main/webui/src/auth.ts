const SESSION_KEY = "pages-dev-auth-token";

export function getToken(): string | null {
    try {
        return sessionStorage.getItem(SESSION_KEY);
    } catch {
        return null;
    }
}

export function getIdentity(): string | null {
    const token = getToken();
    if (!token) return null;
    try {
        const parts = token.split(".");
        if (parts.length !== 3) return null;
        const payload = JSON.parse(atob(parts[1]!.replace(/-/g, "+").replace(/_/g, "/")));
        return typeof payload.sub === "string" ? payload.sub : null;
    } catch {
        return null;
    }
}

export async function authenticatedFetch(url: string, init?: RequestInit): Promise<Response> {
    const token = getToken();
    const headers = new Headers(init?.headers);
    if (token) {
        headers.set("Authorization", `Bearer ${token}`);
    }
    const resp = await fetch(url, { ...init, headers });
    if (resp.status === 401) {
        document.dispatchEvent(new CustomEvent("pages-auth-expired"));
    }
    return resp;
}

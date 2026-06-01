function resolveApiBaseUrl() {
  const fromEnv = import.meta.env.VITE_API_BASE_URL;
  if (fromEnv != null && String(fromEnv).trim() !== "") {
    return String(fromEnv).replace(/\/$/, "");
  }
  if (import.meta.env.DEV) {
    return "";
  }
  return "";
}

const API_BASE_URL = resolveApiBaseUrl();
const ACCESS_TOKEN_KEY = "kemalessBankAccessJwt";
const REFRESH_TOKEN_KEY = "kemalessBankRefreshJwt";

class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

export const authStore = {
  getToken() {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  },
  getRefreshToken() {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  },
  setSession(session) {
    localStorage.setItem(ACCESS_TOKEN_KEY, session.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, session.refreshToken);
  },
  setToken(token) {
    localStorage.setItem(ACCESS_TOKEN_KEY, token);
  },
  clearToken() {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  },
  snapshot() {
    return {
      accessToken: localStorage.getItem(ACCESS_TOKEN_KEY),
      refreshToken: localStorage.getItem(REFRESH_TOKEN_KEY)
    };
  }
};

export function isAuthError(error) {
  return error?.status === 401;
}

async function request(path, options = {}) {
  return requestWithAuth(path, options, true);
}

async function requestWithAuth(path, options = {}, allowRefresh) {
  const token = authStore.getToken();
  const method = (options.method || "GET").toUpperCase();
  const headers = {
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...options.headers
  };
  if (method !== "GET" && method !== "HEAD") {
    headers["Content-Type"] = headers["Content-Type"] || "application/json";
  }
  let response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers
    });
  } catch {
    throw new ApiError("Не удалось связаться с банком. Проверьте подключение и попробуйте еще раз.", 0);
  }

  if (response.status === 401 && allowRefresh && path !== "/api/auth/refresh" && authStore.getRefreshToken()) {
    const refreshed = await refreshSession();
    if (refreshed) {
      return requestWithAuth(path, options, false);
    }
  }
  if (!response.ok) {
    if (response.status === 401) {
      authStore.clearToken();
    }
    const text = await response.text();
    let message = text || response.statusText;
    try {
      const problem = JSON.parse(text);
      message = problem.detail || problem.title || message;
      if (problem.fields) {
        message = Object.values(problem.fields).join(", ");
      }
    } catch {
      message = text?.trim() || fallbackMessage(response.status);
    }
    throw new ApiError(readableApiMessage(message, response.status), response.status);
  }
  if (response.status === 204) {
    return null;
  }
  const text = await response.text();
  if (!text) {
    return null;
  }
  return JSON.parse(text);
}

async function refreshSession() {
  try {
    const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: authStore.getRefreshToken() })
    });
    if (!response.ok) {
      authStore.clearToken();
      return false;
    }
    const session = await response.json();
    authStore.setSession(session);
    return true;
  } catch {
    return false;
  }
}

function fallbackMessage(status) {
  if (status === 400) {
    return "Проверьте введенные данные";
  }
  if (status === 401) {
    return "Сессия истекла. Войдите в банк заново";
  }
  if (status === 403) {
    return "У вас нет доступа к этому действию";
  }
  if (status === 404) {
    return "Запрошенные данные не найдены";
  }
  if (status === 409) {
    return "Эти данные уже используются";
  }
  return "Не удалось выполнить операцию. Попробуйте позже.";
}

function readableApiMessage(message, status) {
  const text = String(message || "").trim();
  if (!text) {
    return fallbackMessage(status);
  }
  if (text === "Failed to fetch" || text.includes("NetworkError")) {
    return "Не удалось связаться с банком. Проверьте подключение и попробуйте еще раз.";
  }
  if (status >= 500 && /exception|stack|java|sql|trace/i.test(text)) {
    return "Банк временно не смог выполнить операцию. Попробуйте позже.";
  }
  return text;
}

export const api = {
  login(credentials) {
    return request("/api/auth/login", {
      method: "POST",
      body: JSON.stringify(credentials)
    });
  },
  register(payload) {
    return request("/api/auth/register", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },
  me() {
    return request("/api/users/me");
  },
  myDevices() {
    return request("/api/users/me/devices");
  },
  updateProfile(payload) {
    return request("/api/users/me", {
      method: "PUT",
      body: JSON.stringify(payload)
    });
  },
  changePassword(payload) {
    return request("/api/users/me/password", {
      method: "PUT",
      body: JSON.stringify(payload)
    });
  },
  accounts() {
    return request("/api/accounts");
  },
  openAccount(payload) {
    return request("/api/accounts", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },
  closeAccount(id) {
    return request(`/api/accounts/${id}/close`, {
      method: "POST"
    });
  },
  blockAccount(id) {
    return request(`/api/accounts/${id}/block`, {
      method: "POST"
    });
  },
  unblockAccount(id) {
    return request(`/api/accounts/${id}/unblock`, {
      method: "POST"
    });
  },
  operations() {
    return request("/api/operations");
  },
  statement(accountId) {
    return request(`/api/accounts/${accountId}/statement`);
  },
  internalTransfer(payload) {
    return request("/api/transfers/internal", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },
  peerTransfer(payload) {
    return request("/api/transfers/peer", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },
  miniapps() {
    return request("/api/miniapps/catalog");
  },
  launchMiniapp(miniAppCode, payload) {
    return request(`/api/miniapps/${encodeURIComponent(miniAppCode)}/launch`, {
      method: "POST",
      body: JSON.stringify(payload ?? {})
    });
  },
  logout() {
    return request("/api/auth/logout", { method: "POST", body: "{}" });
  }
};

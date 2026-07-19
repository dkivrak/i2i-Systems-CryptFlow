export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api').replace(/\/+$/, '')
export const SESSION_TOKEN_KEY = 'cryptflow_token'

export function buildApiUrl(path) {
  const normalized = path.startsWith('/api/') ? path.slice(4) : path
  return `${API_BASE_URL}${normalized.startsWith('/') ? normalized : `/${normalized}`}`
}

export const token = {
  get: () => sessionStorage.getItem(SESSION_TOKEN_KEY),
  set: (value) => sessionStorage.setItem(SESSION_TOKEN_KEY, value),
  clear: () => sessionStorage.removeItem(SESSION_TOKEN_KEY)
}

export async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) }

  if (token.get()) {
    headers.Authorization = `Bearer ${token.get()}`
  }

  const res = await fetch(buildApiUrl(path), { ...options, headers })

  if (res.status === 204) return null

  const data = await res.json().catch(() => ({ message: 'Server response could not be read.' }))

  if (!res.ok) throw new Error(data.message || 'Request failed.')

  return data
}

const getWsUrl = () => {
  if (import.meta.env.VITE_WS_URL) {
    return import.meta.env.VITE_WS_URL;
  }
  if (API_BASE_URL.startsWith('/')) {
    const loc = window.location;
    const proto = loc.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${proto}//${loc.host}${API_BASE_URL.replace(/\/api$/, '')}/ws`;
  }
  const backendOrigin = API_BASE_URL.replace(/\/api$/, '');
  return backendOrigin.replace(/^http/, 'ws') + '/ws';
};

export const WS_URL = getWsUrl();

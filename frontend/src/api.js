// Talks to the backend. The login token is kept only for this browser tab (sessionStorage)
// and is sent as "Authorization: Bearer <token>". It is never written to the code or to GitHub.

const TOKEN_KEY = 'suma.token'
let onUnauthorized = () => {}

export function setUnauthorizedHandler(fn) {
  onUnauthorized = fn
}

export function getToken() {
  try {
    return sessionStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token) {
  try {
    if (token) sessionStorage.setItem(TOKEN_KEY, token)
    else sessionStorage.removeItem(TOKEN_KEY)
  } catch {
    /* private mode: the token simply lives until the page is closed */
  }
}

export class ApiError extends Error {
  constructor(status, message, fieldErrors) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors || {}
  }
}

async function request(method, path, { body, form, query } = {}) {
  const url = new URL(path, window.location.origin)
  if (query) {
    Object.entries(query).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, v)
    })
  }
  const headers = {}
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  let payload
  if (form) {
    payload = form
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  let res
  try {
    res = await fetch(url, { method, headers, body: payload })
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Is the backend running on port 8080?')
  }
  const text = await res.text()
  let data = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = null
    }
  }
  if (!res.ok) {
    if (res.status === 401 && path !== '/api/auth/login') onUnauthorized()
    const message = (data && data.message) || `Request failed (${res.status})`
    throw new ApiError(res.status, message, data && data.fieldErrors)
  }
  return data
}

export const api = {
  login: (email, password) => request('POST', '/api/auth/login', { body: { email, password } }),
  logout: () => request('POST', '/api/auth/logout'),
  me: () => request('GET', '/api/auth/me'),
  changePassword: (currentPassword, newPassword) =>
    request('POST', '/api/auth/change-password', { body: { currentPassword, newPassword } }),

  projects: () => request('GET', '/api/projects'),
  statuses: () => request('GET', '/api/ticket-statuses'),
  dashboard: (query) => request('GET', '/api/dashboard', { query }),

  tickets: (query) => request('GET', '/api/tickets', { query }),
  ticket: (id) => request('GET', `/api/tickets/${id}`),
  ticketHistory: (id) => request('GET', `/api/tickets/${id}/history`),

  importTickets: ({ file, projectId, mode, sheet }) => {
    const form = new FormData()
    form.append('file', file)
    form.append('projectId', projectId)
    form.append('mode', mode)
    if (sheet) form.append('sheet', sheet)
    return request('POST', '/api/imports/tickets', { form })
  },
  imports: (projectId) => request('GET', '/api/imports', { query: { projectId } }),
  importDetails: (id) => request('GET', `/api/imports/${id}`),
}
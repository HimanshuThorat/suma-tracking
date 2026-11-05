import { useCallback, useEffect, useState } from 'react'
import { api, getToken, setToken, setUnauthorizedHandler } from './api.js'
import LoginPage from './pages/LoginPage.jsx'
import ChangePasswordPage from './pages/ChangePasswordPage.jsx'
import DashboardPage from './pages/DashboardPage.jsx'
import TicketsPage from './pages/TicketsPage.jsx'
import ImportPage from './pages/ImportPage.jsx'

const PAGES = [
  { key: 'dashboard', label: 'Dashboard', icon: IconChart },
  { key: 'tickets', label: 'Tickets', icon: IconList },
  { key: 'import', label: 'Import sheet', icon: IconUpload, roles: ['SUPER_ADMIN', 'CLIENT_ADMIN', 'PROJECT_MANAGER'] },
]

function pageFromHash() {
  const key = window.location.hash.replace('#/', '')
  return PAGES.some((p) => p.key === key) ? key : 'dashboard'
}

export default function App() {
  const [user, setUser] = useState(null)
  const [checking, setChecking] = useState(Boolean(getToken()))
  const [page, setPage] = useState(pageFromHash())

  const signOut = useCallback(() => {
    setToken(null)
    setUser(null)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(signOut)
    if (!getToken()) return
    api.me()
      .then(setUser)
      .catch(() => setToken(null))
      .finally(() => setChecking(false))
  }, [signOut])

  useEffect(() => {
    const onHash = () => setPage(pageFromHash())
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [])

  async function handleLogout() {
    try {
      await api.logout()
    } catch {
      /* the session ends on this side anyway */
    }
    signOut()
  }

  if (checking) {
    return <div className="boot">Opening Suma Tracking...</div>
  }
  if (!user) {
    return <LoginPage onLoggedIn={(u) => setUser(u)} />
  }
  if (user.mustChangePassword) {
    return <ChangePasswordPage user={user} onDone={(u) => setUser(u)} onLogout={handleLogout} />
  }

  const visiblePages = PAGES.filter((p) => !p.roles || p.roles.includes(user.roleCode))
  const current = visiblePages.some((p) => p.key === page) ? page : 'dashboard'

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">S</span>
          <span className="brand-name">Suma Tracking</span>
        </div>
        <nav className="nav" aria-label="Main">
          {visiblePages.map((p) => (
            <a
              key={p.key}
              href={`#/${p.key}`}
              className={`nav-link${current === p.key ? ' active' : ''}`}
              aria-current={current === p.key ? 'page' : undefined}
            >
              <p.icon />
              <span>{p.label}</span>
            </a>
          ))}
        </nav>
        <div className="me">
          <div className="me-avatar" aria-hidden="true">{initials(user.fullName)}</div>
          <div className="me-text">
            <div className="me-name">{user.fullName}</div>
            <div className="me-role">{user.roleName}</div>
          </div>
          <button type="button" className="me-logout" onClick={handleLogout} title="Log out">
            Log out
          </button>
        </div>
      </aside>
      <main className="main">
        {current === 'dashboard' && <DashboardPage user={user} />}
        {current === 'tickets' && <TicketsPage user={user} />}
        {current === 'import' && <ImportPage user={user} />}
      </main>
    </div>
  )
}

function initials(name) {
  return (name || '?').split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase()
}

function IconChart() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M4 20V10M10 20V4M16 20v-7M22 20H2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}

function IconList() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}

function IconUpload() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M12 16V4M7 9l5-5 5 5M4 20h16" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
import { useCallback, useEffect, useState } from 'react'
import { api, getToken, setToken, setUnauthorizedHandler } from './api.js'
import LoginPage from './pages/LoginPage.jsx'
import ChangePasswordPage from './pages/ChangePasswordPage.jsx'
import DashboardPage from './pages/DashboardPage.jsx'
import ClientsPage from './pages/ClientsPage.jsx'
import ProjectsPage from './pages/ProjectsPage.jsx'
import IntegrationsPage from './pages/IntegrationsPage.jsx'
import TicketsPage from './pages/TicketsPage.jsx'
import TeamPage from './pages/TeamPage.jsx'
import ReportsPage from './pages/ReportsPage.jsx'
import SecurityPage from './pages/SecurityPage.jsx'
import KnowledgePage from './pages/KnowledgePage.jsx'
import SettingsPage from './pages/SettingsPage.jsx'
import SlaPage from './pages/SlaPage.jsx'
import NotificationsPage from './pages/NotificationsPage.jsx'
import AutomationPage from './pages/AutomationPage.jsx'

// Who sees which menu item. Missing "roles" = everyone who can log in.
const ADMIN = ['SUPER_ADMIN']
const LEADS = ['SUPER_ADMIN', 'MANAGEMENT_VIEWER', 'CLIENT_ADMIN', 'PROJECT_MANAGER', 'TEAM_LEADER']
const MANAGERS = ['SUPER_ADMIN', 'CLIENT_ADMIN', 'PROJECT_MANAGER']

const MENU = [
  { key: 'dashboard', label: 'Dashboard', icon: 'M4 20V10M10 20V4M16 20v-7M22 20H2' },
  { key: 'clients', label: 'Client Management', icon: 'M3 21h18M5 21V7l7-4 7 4v14M9 21v-6h6v6', roles: ADMIN },
  { key: 'projects', label: 'Project Management', icon: 'M3 7h18v13H3zM8 7V4h8v3', roles: LEADS },
  { key: 'integrations', label: 'Integration Management', icon: 'M8 7h8M8 17h8M4 12h16M12 3v4M12 17v4', roles: MANAGERS },
  { key: 'tickets', label: 'Ticket Management', icon: 'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01' },
  { key: 'team', label: 'Team Management', icon: 'M16 21v-2a4 4 0 0 0-8 0v2M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8M22 21v-2a3 3 0 0 0-3-3M2 21v-2a3 3 0 0 1 3-3', roles: ADMIN },
  { key: 'sla', label: 'SLA Management', icon: 'M12 7v5l3 3M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18', roles: LEADS },
  { key: 'automation', label: 'Automation', icon: 'M13 2L4 14h7l-1 8 9-12h-7l1-8z', roles: ADMIN },
  { key: 'reports', label: 'Reports', icon: 'M6 2h9l5 5v15H6zM14 2v6h6M9 13h6M9 17h6', roles: LEADS },
  { key: 'notifications', label: 'Notifications', icon: 'M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 0 1-3.4 0' },
  { key: 'knowledge', label: 'Knowledge Base', icon: 'M4 19.5A2.5 2.5 0 0 1 6.5 17H20V3H6.5A2.5 2.5 0 0 0 4 5.5zM4 19.5A2.5 2.5 0 0 0 6.5 22H20v-5' },
  { key: 'security', label: 'Security', icon: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z', roles: ADMIN },
  { key: 'settings', label: 'Settings', icon: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z' },
]

// Older links (#/import, #/people) still work.
const ALIASES = { import: 'integrations', people: 'team' }

function pageFromHash() {
  // "#/tickets?q=124628&open=1" and "#/knowledge/add-employee" keep their page
  const raw = window.location.hash.replace('#/', '').split(/[?/]/)[0].split('?')[0]
  const key = ALIASES[raw] || raw
  return MENU.some((m) => m.key === key) ? key : 'dashboard'
}

export default function App() {
  const [user, setUser] = useState(null)
  const [checking, setChecking] = useState(Boolean(getToken()))
  const [page, setPage] = useState(pageFromHash())
  const [hash, setHash] = useState(window.location.hash)
  const [unread, setUnread] = useState(0)

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
    const onHash = () => { setPage(pageFromHash()); setHash(window.location.hash) }
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [])

  // Unread notifications: checked on sign-in, on every page change and every minute.
  useEffect(() => {
    if (!user || user.mustChangePassword) return undefined
    let alive = true
    const check = () => api.unreadCount().then((c) => { if (alive) setUnread(c.count) }).catch(() => {})
    check()
    const timer = setInterval(check, 60000)
    return () => { alive = false; clearInterval(timer) }
  }, [user, page])

  async function handleLogout() {
    try {
      await api.logout()
    } catch {
      /* the session ends on this side anyway */
    }
    signOut()
  }

  if (checking) return <div className="boot">Opening Suma Tracking...</div>
  if (!user) return <LoginPage onLoggedIn={(u) => setUser(u)} />
  if (user.mustChangePassword) {
    return <ChangePasswordPage user={user} onDone={(u) => setUser(u)} onLogout={handleLogout} />
  }

  const menu = MENU.filter((m) => !m.roles || m.roles.includes(user.roleCode)
    // a role created later (e.g. Project Lead) of the lead type sees what leads see
    || (m.roles === LEADS && user.userGroup === 'PROJECT_MANAGER'))
  const current = menu.some((m) => m.key === page) ? page : 'dashboard'

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">S</span>
          <span className="brand-name">Suma Tracking</span>
        </div>
        <nav className="nav" aria-label="Main">
          {menu.map((m) => (
            <a key={m.key} href={`#/${m.key}`} className={`nav-link${current === m.key ? ' active' : ''}`}
              aria-current={current === m.key ? 'page' : undefined}>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d={m.icon} stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
              <span>{m.label}</span>
              {m.key === 'notifications' && unread > 0 && (
                <span className="nav-badge" aria-label={`${unread} unread`}>{unread > 99 ? '99+' : unread}</span>
              )}
            </a>
          ))}
        </nav>
        <div className="me">
          <div className="me-avatar" aria-hidden="true">{initials(user.fullName)}</div>
          <div className="me-text">
            <div className="me-name">{user.fullName}</div>
            <div className="me-role">{user.roleName}</div>
          </div>
          <button type="button" className="me-logout" onClick={handleLogout}>Log out</button>
        </div>
      </aside>
      <main className="main">
        {current === 'dashboard' && <DashboardPage user={user} />}
        {current === 'clients' && <ClientsPage user={user} />}
        {current === 'projects' && <ProjectsPage user={user} />}
        {current === 'integrations' && <IntegrationsPage user={user} />}
        {current === 'tickets' && <TicketsPage key={hash} user={user} />}
        {current === 'team' && <TeamPage user={user} />}
        {current === 'reports' && <ReportsPage user={user} />}
        {current === 'security' && <SecurityPage user={user} />}
        {current === 'sla' && <SlaPage user={user} />}
        {current === 'automation' && <AutomationPage user={user} />}
        {current === 'notifications' && <NotificationsPage user={user} onCountChange={setUnread} />}
        {current === 'knowledge' && <KnowledgePage user={user} />}
        {current === 'settings' && <SettingsPage user={user} />}
      </main>
    </div>
  )
}

function initials(name) {
  return (name || '?').split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase()
}
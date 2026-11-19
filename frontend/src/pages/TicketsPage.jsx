import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'
import { GROUP_COLORS, GROUP_LABELS, fmtDateTime, fmtMinutes, fmtNumber } from '../format.js'

const PAGE_SIZE = 50

export default function TicketsPage() {
  const [projects, setProjects] = useState([])
  const [filters, setFilters] = useState({ q: '', projectId: '', statusGroup: '', escalated: '', createdFrom: '', createdTo: '' })
  const [applied, setApplied] = useState(filters)
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [openId, setOpenId] = useState(null)

  useEffect(() => {
    api.projects().then((l) => setProjects(l || [])).catch(() => setProjects([]))
  }, [])

  useEffect(() => {
    let alive = true
    setLoading(true)
    setError('')
    api.tickets({ ...applied, page, size: PAGE_SIZE })
      .then((d) => { if (alive) setData(d) })
      .catch((e) => { if (alive) setError(e.message) })
      .finally(() => { if (alive) setLoading(false) })
    return () => { alive = false }
  }, [applied, page])

  function search(e) {
    e.preventDefault()
    setPage(0)
    setApplied(filters)
  }

  function reset() {
    const empty = { q: '', projectId: '', statusGroup: '', escalated: '', createdFrom: '', createdTo: '' }
    setFilters(empty)
    setApplied(empty)
    setPage(0)
  }

  const set = (k) => (e) => setFilters({ ...filters, [k]: e.target.value })
  const closeDrawer = useCallback(() => setOpenId(null), [])

  function quick(statusGroup, escalated) {
    const next = { ...filters, statusGroup, escalated }
    setFilters(next)
    setApplied(next)
    setPage(0)
  }

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>Ticket Management</h1>
          <p className="muted">
            {data ? `${fmtNumber(data.totalItems)} tickets match` : 'Loading tickets'}. Newest first. Click a ticket to see its details and history.
          </p>
        </div>
      </header>

      <div className="quick" role="group" aria-label="Quick filters">
        <button type="button" className={!applied.statusGroup && !applied.escalated ? 'on' : ''} onClick={() => quick('', '')}>All tickets</button>
        <button type="button" className={applied.statusGroup === 'PENDING' ? 'on' : ''} onClick={() => quick('PENDING', '')}>Pending</button>
        <button type="button" className={applied.statusGroup === 'OPEN' ? 'on' : ''} onClick={() => quick('OPEN', '')}>Open</button>
        <button type="button" className={applied.escalated === 'true' ? 'on' : ''} onClick={() => quick('', 'true')}>Escalated</button>
        <button type="button" className={applied.statusGroup === 'RESOLVED' ? 'on' : ''} onClick={() => quick('RESOLVED', '')}>Resolved</button>
        <button type="button" className={applied.statusGroup === 'CLOSED' ? 'on' : ''} onClick={() => quick('CLOSED', '')}>Closed</button>
      </div>

      <form className="toolbar" onSubmit={search}>
        <label className="field grow">
          <span>Search</span>
          <input type="search" placeholder="Ticket number, e.g. 124628 or TK-000012" value={filters.q} onChange={set('q')} />
        </label>
        <label className="field">
          <span>Project</span>
          <select value={filters.projectId} onChange={set('projectId')}>
            <option value="">All</option>
            {projects.map((p) => <option key={p.id} value={p.id}>{p.projectName}</option>)}
          </select>
        </label>
        <label className="field">
          <span>Status</span>
          <select value={filters.statusGroup} onChange={set('statusGroup')}>
            <option value="">All</option>
            {Object.entries(GROUP_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </label>
        <label className="field">
          <span>Escalated</span>
          <select value={filters.escalated} onChange={set('escalated')}>
            <option value="">All</option>
            <option value="true">Yes</option>
            <option value="false">No</option>
          </select>
        </label>
        <label className="field">
          <span>Created from</span>
          <input type="date" value={filters.createdFrom} onChange={set('createdFrom')} />
        </label>
        <label className="field">
          <span>to</span>
          <input type="date" value={filters.createdTo} onChange={set('createdTo')} />
        </label>
        <div className="toolbar-actions">
          <button className="btn primary" type="submit">Search</button>
          <button className="btn ghost" type="button" onClick={reset}>Clear</button>
        </div>
      </form>

      {error && <div className="alert error" role="alert">{error}</div>}

      <section className={`panel flush${loading ? ' dim' : ''}`}>
        <div className="table-wrap">
          <table className="table tickets">
            <thead>
              <tr>
                <th scope="col">Ticket</th>
                <th scope="col">Project</th>
                <th scope="col">Status</th>
                <th scope="col">Assignee</th>
                <th scope="col">Topic</th>
                <th scope="col">Source</th>
                <th scope="col">Created</th>
                <th scope="col" className="num">Solve time or age</th>
              </tr>
            </thead>
            <tbody>
              {data?.items.map((t) => (
                <tr key={t.id} className="clickable" tabIndex={0}
                  onClick={() => setOpenId(t.id)}
                  onKeyDown={(e) => { if (e.key === 'Enter') setOpenId(t.id) }}>
                  <td>
                    <div className="strong">{t.externalTicketId || t.ticketCode}</div>
                    <div className="muted small">{t.ticketCode}{t.escalated ? ', escalated' : ''}</div>
                  </td>
                  <td>{t.projectName}<div className="muted small">{titleCase(t.sourceSystem)}</div></td>
                  <td><StatusBadge group={t.statusGroup} name={t.statusName} /></td>
                  <td>{t.assigneeName || <span className="muted">Unassigned</span>}</td>
                  <td className="clip">{t.topic || t.subject || <span className="muted">-</span>}</td>
                  <td>{t.channel || <span className="muted">-</span>}</td>
                  <td className="nowrap">{fmtDateTime(t.createdAtSource)}</td>
                  <td className="num nowrap">{t.resolutionMinutes != null ? fmtMinutes(t.resolutionMinutes) : <span className="muted">open {fmtMinutes(t.ageMinutes)}</span>}</td>
                </tr>
              ))}
              {data && data.items.length === 0 && (
                <tr><td colSpan={8} className="empty-row">No tickets match these filters. Clear the filters or import the latest sheet.</td></tr>
              )}
            </tbody>
          </table>
        </div>
        {data && data.totalPages > 1 && (
          <div className="pager">
            <button className="btn ghost" type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
            <span>Page {page + 1} of {data.totalPages}</span>
            <button className="btn ghost" type="button" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
          </div>
        )}
      </section>

      {openId && <TicketDrawer id={openId} onClose={closeDrawer} onChanged={() => setApplied({ ...applied })} />}
    </div>
  )
}

export function StatusBadge({ group, name }) {
  return (
    <span className="badge" style={{ '--c': GROUP_COLORS[group] || '#5B6B7F' }}>{name}</span>
  )
}

function TicketDrawer({ id, onClose, onChanged }) {
  const [t, setT] = useState(null)
  const [history, setHistory] = useState([])
  const [error, setError] = useState('')
  const [version, setVersion] = useState(0)

  useEffect(() => {
    Promise.all([api.ticket(id), api.ticketHistory(id)])
      .then(([ticket, h]) => { setT(ticket); setHistory(h || []) })
      .catch((e) => setError(e.message))
  }, [id, version])

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="drawer-back" onClick={onClose}>
      <aside className="drawer" role="dialog" aria-modal="true" aria-label="Ticket details" onClick={(e) => e.stopPropagation()}>
        <button className="drawer-close" type="button" onClick={onClose} aria-label="Close">{"\u00d7"}</button>
        {error && <div className="alert error">{error}</div>}
        {!t && !error && <p className="muted">Loading...</p>}
        {t && (
          <>
            <div className="drawer-head">
              <h2>{t.externalTicketId || t.ticketCode}</h2>
              <StatusBadge group={t.statusGroup} name={t.statusName} />
            </div>
            <p className="muted">{t.projectName} ({t.clientName}), from {titleCase(t.sourceSystem)}. Our number {t.ticketCode}.</p>
            {t.description && <p className="desc">{t.description}</p>}
            <TicketActions ticket={t} onDone={() => { setVersion((v) => v + 1); onChanged() }} />
            <dl className="facts">
              <Fact k="Created" v={`${fmtDateTime(t.createdAtSource)} (${t.day})`} />
              <Fact k="Resolved" v={fmtDateTime(t.resolvedAt)} />
              <Fact k="Closed" v={fmtDateTime(t.closedAt)} />
              <Fact k="Solve time" v={t.resolutionMinutes != null ? fmtMinutes(t.resolutionMinutes) : `still open, ${fmtMinutes(t.ageMinutes)} so far`} />
              <Fact k="Assignee" v={t.assigneeName} />
              <Fact k="Closed by" v={t.closedByName} />
              <Fact k="Escalated" v={t.escalated ? 'Yes' : 'No'} />
              <Fact k="Priority" v={t.priorityName} />
              <Fact k="Source" v={t.channel} />
              <Fact k="Requester" v={[t.requesterName, t.requesterRole].filter(Boolean).join(', ')} />
              <Fact k="Issue type" v={t.issueType} />
              <Fact k="Plan" v={t.plan} />
              <Fact k="Topic" v={t.topic} />
              <Fact k="Category" v={[t.category, t.subcategory].filter(Boolean).join(' / ')} />
              <Fact k="Satisfaction" v={t.satisfactionRating} />
            </dl>
            <h3>History</h3>
            <ol className="timeline">
              {history.map((h) => (
                <li key={h.id}>
                  <div className="tl-when">{fmtDateTime(h.occurredAt)}</div>
                  <div className="tl-what">{describe(h)}</div>
                  <div className="muted small">{[h.changedByName, h.note].filter(Boolean).join(', ')}</div>
                </li>
              ))}
              {history.length === 0 && <li className="muted">No history yet.</li>}
            </ol>
          </>
        )}
      </aside>
    </div>
  )
}

function Fact({ k, v }) {
  return (
    <div className="fact">
      <dt>{k}</dt>
      <dd>{v || <span className="muted">-</span>}</dd>
    </div>
  )
}

function describe(h) {
  if (h.eventType === 'IMPORTED' && !h.fieldName) return `Imported with status ${h.newValue}`
  if (h.fieldName) return `${h.fieldName} changed from "${h.oldValue ?? 'empty'}" to "${h.newValue ?? 'empty'}"`
  if (h.eventType === 'STATUS_CHANGED') return `Status changed to ${h.newValue}`
  if (h.eventType === 'ASSIGNED') return `Assigned to ${h.newValue ?? 'nobody'}`
  return h.eventType.charAt(0) + h.eventType.slice(1).toLowerCase()
}

function titleCase(s) {
  return s ? s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' ') : ''
}

/** Work on the ticket: change status, assign / reassign, escalate to Tier 2. The server checks who may do what. */
function TicketActions({ ticket, onDone }) {
  const [statuses, setStatuses] = useState([])
  const [team, setTeam] = useState([])
  const [status, setStatus] = useState('')
  const [assignee, setAssignee] = useState('')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [done, setDone] = useState('')

  useEffect(() => {
    api.statuses().then((l) => setStatuses(l || [])).catch(() => setStatuses([]))
    api.projectTeam(ticket.projectId).then((l) => setTeam(l || [])).catch(() => setTeam([]))
  }, [ticket.projectId])

  useEffect(() => {
    setStatus(ticket.statusCode)
    setAssignee(ticket.assigneeUserId ? String(ticket.assigneeUserId) : '')
  }, [ticket.statusCode, ticket.assigneeUserId])

  async function run(kind) {
    setError('')
    setDone('')
    setBusy(kind)
    try {
      if (kind === 'status') {
        await api.changeStatus(ticket.id, status, note || null)
        setDone('Status changed.')
      } else if (kind === 'assign') {
        await api.assignTicket(ticket.id, assignee ? Number(assignee) : null, note || null)
        setDone(assignee ? 'Ticket assigned.' : 'Ticket unassigned.')
      } else {
        await api.escalateTicket(ticket.id, note || null)
        setDone('Escalated to Tier 2.')
      }
      setNote('')
      onDone()
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy('')
    }
  }

  const finished = ticket.statusGroup === 'RESOLVED' || ticket.statusGroup === 'CLOSED'
  return (
    <div className="actions-box">
      <div className="action-row">
        <label className="field"><span>Status</span>
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            {statuses.map((s) => <option key={s.code} value={s.code}>{s.name}</option>)}
          </select>
        </label>
        <button className="btn" type="button" disabled={!!busy || status === ticket.statusCode} onClick={() => run('status')}>
          {busy === 'status' ? 'Saving...' : 'Change status'}
        </button>
      </div>
      <div className="action-row">
        <label className="field"><span>Assigned to</span>
          <select value={assignee} onChange={(e) => setAssignee(e.target.value)} disabled={finished}>
            <option value="">Nobody</option>
            {team.map((m) => <option key={m.userId} value={m.userId}>{m.fullName}</option>)}
          </select>
        </label>
        <button className="btn" type="button" disabled={!!busy || finished || assignee === (ticket.assigneeUserId ? String(ticket.assigneeUserId) : '')}
          onClick={() => run('assign')}>
          {busy === 'assign' ? 'Saving...' : ticket.assigneeUserId ? 'Reassign' : 'Assign'}
        </button>
      </div>
      <label className="field"><span>Note for the history (optional)</span>
        <input value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} placeholder="Why this change" />
      </label>
      <div className="action-row end">
        <button className="btn warn" type="button" disabled={!!busy || finished} onClick={() => run('escalate')}>
          {busy === 'escalate' ? 'Escalating...' : 'Escalate to Tier 2'}
        </button>
      </div>
      {error && <div className="alert error" role="alert">{error}</div>}
      {done && <div className="alert ok" role="status">{done}</div>}
      {team.length === 0 && <p className="fine">Nobody is on this project&apos;s team yet, so the ticket cannot be assigned. Add people in Team Management.</p>}
    </div>
  )
}
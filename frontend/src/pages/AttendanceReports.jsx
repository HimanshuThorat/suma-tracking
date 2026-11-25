import { useEffect, useRef, useState } from 'react'
import { api } from '../api.js'
import { Toast } from '../components.jsx'
import { ZONE } from '../attendance.jsx'

// Step 43: daily and monthly attendance reports. Download as Excel (CSV), or send by email like a normal mail:
// To (pick people), Cc, subject, message, and the report attached by itself.

function todayIst() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: ZONE }).format(new Date())
}

function lastMonth() {
  const [y, m] = todayIst().split('-').map(Number)
  return m === 1 ? `${y - 1}-12` : `${y}-${String(m - 1).padStart(2, '0')}`
}

function hours(min) {
  const m = Math.max(0, Math.round(min || 0))
  return `${Math.floor(m / 60)}h ${String(m % 60).padStart(2, '0')}m`
}

function niceDay(iso) {
  return new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(`${iso}T00:00:00`))
}

function niceMonth(ym) {
  return new Intl.DateTimeFormat('en-IN', { month: 'long', year: 'numeric' }).format(new Date(`${ym}-01T00:00:00`))
}

export default function AttendanceReports() {
  const [type, setType] = useState('DAILY')
  const [date, setDate] = useState(todayIst())
  const [month, setMonth] = useState(todayIst().slice(0, 7))
  const [report, setReport] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [sending, setSending] = useState(false)
  const [toast, setToast] = useState('')
  const period = type === 'DAILY' ? date : month

  useEffect(() => {
    let alive = true
    setReport(null)
    setError('')
    const call = type === 'DAILY' ? api.reportDaily(date) : api.reportMonthly(month)
    call.then((r) => { if (alive) setReport(r) }).catch((e) => { if (alive) setError(e.message) })
    return () => { alive = false }
  }, [type, date, month])

  async function download() {
    setBusy(true)
    setError('')
    try {
      const name = await api.reportDownload(type, period)
      setToast(`Saved ${name} in your Downloads`)
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  const title = type === 'DAILY' ? `Daily attendance report ${niceDay(date)}` : `Monthly attendance report ${niceMonth(month)}`
  const fileName = type === 'DAILY' ? `attendance-daily-${date}.csv` : `attendance-monthly-${month}.csv`

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Attendance reports</h2>
        <p>Login and logout time, working time, every break and alerts. Download it for Excel, or send it by email.</p>
      </div>
      <div className="att-toolbar rep-toolbar">
        <div className="segmented" role="tablist" aria-label="Report type">
          <button type="button" role="tab" aria-selected={type === 'DAILY'} className={type === 'DAILY' ? 'on' : ''} onClick={() => setType('DAILY')}>Daily</button>
          <button type="button" role="tab" aria-selected={type === 'MONTHLY'} className={type === 'MONTHLY' ? 'on' : ''} onClick={() => setType('MONTHLY')}>Monthly</button>
        </div>
        {type === 'DAILY' ? (
          <label className="field inline"><span>Day</span>
            <input type="date" value={date} max={todayIst()} onChange={(e) => e.target.value && setDate(e.target.value)} />
          </label>
        ) : (
          <label className="field inline"><span>Month</span>
            <input type="month" value={month} max={todayIst().slice(0, 7)} onChange={(e) => e.target.value && setMonth(e.target.value)} />
          </label>
        )}
        {type === 'MONTHLY' && month !== lastMonth() && <button type="button" className="btn ghost small" onClick={() => setMonth(lastMonth())}>Last month</button>}
        <span className="rep-spacer" />
        <button type="button" className="btn" disabled={busy || !report} onClick={download}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 3v12m0 0l-4-4m4 4l4-4M4 17v3h16v-3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" /></svg>
          {busy ? 'Preparing...' : 'Download Excel'}
        </button>
        <button type="button" className="btn primary" disabled={!report} onClick={() => setSending(true)}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M22 2L11 13M22 2l-7 20-4-9-9-4z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" /></svg>
          Send report mail
        </button>
      </div>
      {error && <div className="alert error" role="alert">{error}</div>}
      {!report && !error && <p className="muted">Loading the report...</p>}
      {report && type === 'DAILY' && <DailyTable r={report} />}
      {report && type === 'MONTHLY' && <MonthlyTable r={report} />}
      {sending && (
        <SendReportMail type={type} period={period} title={title} fileName={fileName}
          onClose={() => setSending(false)} onSent={(msg) => { setSending(false); setToast(msg) }} />
      )}
      <Toast text={toast} onDone={() => setToast('')} />
    </section>
  )
}

function DailyTable({ r }) {
  return (
    <>
      <div className="att-tiles rep-tiles">
        <div className="att-tile ok"><strong>{r.present}</strong><span>Present</span></div>
        <div className="att-tile bad"><strong>{r.notMarked}</strong><span>Not marked</span></div>
        <div className="att-tile muted"><strong>{r.absent}</strong><span>Absent / not in</span></div>
        <div className="att-tile amber"><strong>{r.withAlerts}</strong><span>With alerts</span></div>
      </div>
      <div className="table-wrap">
        <table className="table compact">
          <thead><tr><th scope="col">Employee</th><th scope="col">Status</th><th scope="col">Login</th><th scope="col">Logout</th><th scope="col">Working</th><th scope="col">Break</th><th scope="col">Break timings</th><th scope="col">Alerts</th></tr></thead>
          <tbody>
            {r.rows.map((x) => (
              <tr key={x.userId} className={x.alerts.length ? 'row-alert' : ''}>
                <td><strong>{x.fullName}</strong><span className="muted small block">{x.employeeCode ? `${x.employeeCode} \u00b7 ` : ''}{x.roleName}</span></td>
                <td>{x.status}</td>
                <td className="mono">{x.punchIn || '\u2014'}</td>
                <td className="mono">{x.punchOut || '\u2014'}</td>
                <td>{x.punchIn ? hours(x.workedMinutes) : '\u2014'}</td>
                <td className={x.breakMinutes > x.breakAllowedMinutes + 1 ? 'bad' : ''}>{x.punchIn ? `${hours(x.breakMinutes)} / ${hours(x.breakAllowedMinutes)}` : '\u2014'}</td>
                <td className="small">{x.breaks || '\u2014'}{x.idleChecks ? <span className="muted block">Idle checks: {x.idleChecks}</span> : null}</td>
                <td className="small">{x.alerts.length ? x.alerts.join(', ') : <span className="muted">None</span>}</td>
              </tr>
            ))}
            {!r.rows.length && <tr><td colSpan={8} className="muted">Nobody here.</td></tr>}
          </tbody>
        </table>
      </div>
    </>
  )
}

function MonthlyTable({ r }) {
  return (
    <>
      <p className="muted small">{r.people} people {'\u00b7'} total working time {hours(r.totalWorkedMinutes)}</p>
      <div className="table-wrap">
        <table className="table compact">
          <thead><tr><th scope="col">Employee</th><th scope="col">Days worked</th><th scope="col">Present</th><th scope="col">Not marked</th><th scope="col">Working (total / avg)</th><th scope="col">Break (total / avg)</th><th scope="col">Avg login</th><th scope="col">Avg logout</th><th scope="col">Alerts</th></tr></thead>
          <tbody>
            {r.rows.map((x) => (
              <tr key={x.userId}>
                <td><strong>{x.fullName}</strong><span className="muted small block">{x.roleName}</span></td>
                <td>{x.daysWorked}</td>
                <td className="ok-text">{x.daysPresent}</td>
                <td className={x.daysNotMarked ? 'bad' : ''}>{x.daysNotMarked}</td>
                <td>{hours(x.workedMinutes)} <span className="muted small">/ {hours(x.avgWorkedMinutes)}</span></td>
                <td>{hours(x.breakMinutes)} <span className="muted small">/ {hours(x.avgBreakMinutes)}</span></td>
                <td className="mono">{x.avgPunchIn || '\u2014'}</td>
                <td className="mono">{x.avgPunchOut || '\u2014'}</td>
                <td className="small">
                  {[x.breakOvertimeDays ? `Break over time ${x.breakOvertimeDays}d` : '', x.noBreakDays ? `No full break ${x.noBreakDays}d` : '',
                    x.forgotPunchOutDays ? `Forgot punch-out ${x.forgotPunchOutDays}d` : '', x.idleChecks ? `Idle checks ${x.idleChecks}` : '',
                    x.deviceAlerts ? `Device alerts ${x.deviceAlerts}d` : ''].filter(Boolean).join(', ') || <span className="muted">None</span>}
                </td>
              </tr>
            ))}
            {!r.rows.length && <tr><td colSpan={9} className="muted">Nobody here.</td></tr>}
          </tbody>
        </table>
      </div>
    </>
  )
}

/** A mail window: To and Cc (pick people), subject, message, and the report attached by itself. */
function SendReportMail({ type, period, title, fileName, onClose, onSent }) {
  const [to, setTo] = useState([])
  const [cc, setCc] = useState([])
  const [showCc, setShowCc] = useState(false)
  const [subject, setSubject] = useState(`[Suma Tracking] ${title}`)
  const [message, setMessage] = useState(`Hello,\n\nPlease find the ${type === 'DAILY' ? 'daily' : 'monthly'} attendance report attached.\n\nRegards`)
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape' && !busy) onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [busy, onClose])

  async function send() {
    const errs = {}
    if (!to.length) errs.to = 'Add at least one person in To.'
    if (!subject.trim()) errs.subject = 'Write a subject.'
    if (subject.length > 200) errs.subject = 'Subject: at most 200 characters.'
    if (message.length > 3000) errs.message = 'Message: at most 3000 characters.'
    setErrors(errs)
    if (Object.keys(errs).length) return
    setBusy(true)
    setError('')
    try {
      const res = await api.reportSend({ type, period, to: to.map((p) => p.userId), cc: cc.map((p) => p.userId), subject: subject.trim(), message })
      if (res.status === 'SENT') onSent(`Report sent to ${res.to.join(', ')}`)
      else setError(res.message)
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mail-overlay" role="dialog" aria-modal="true" aria-labelledby="mail-title" onMouseDown={(e) => { if (e.target === e.currentTarget && !busy) onClose() }}>
      <div className="mail-window">
        <header className="mail-head">
          <h2 id="mail-title">New message</h2>
          <button type="button" className="icon-plain" aria-label="Close" onClick={onClose} disabled={busy}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" /></svg>
          </button>
        </header>
        <div className="mail-body">
          <PeopleField label="To" people={to} onChange={setTo} error={errors.to} autoFocus
            extra={!showCc && <button type="button" className="mail-cc-btn" onClick={() => setShowCc(true)}>Cc</button>} />
          {showCc && <PeopleField label="Cc" people={cc} onChange={setCc} />}
          <div className={`mail-row${errors.subject ? ' invalid' : ''}`}>
            <span className="mail-label">Subject</span>
            <input className="mail-input" value={subject} maxLength={200} onChange={(e) => setSubject(e.target.value)} aria-label="Subject" />
          </div>
          {errors.subject && <small className="field-error mail-err" role="alert">{'\u26a0 '}{errors.subject}</small>}
          <textarea className="mail-text" rows={8} value={message} maxLength={3000} onChange={(e) => setMessage(e.target.value)} aria-label="Message" />
          {errors.message && <small className="field-error mail-err" role="alert">{'\u26a0 '}{errors.message}</small>}
          <div className="mail-attach" title="Attached by itself">
            <span className="mail-attach-icon" aria-hidden="true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none"><path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9zM14 3v6h6M8 13h8M8 17h6" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" /></svg>
            </span>
            <span><strong>{fileName}</strong><span className="muted small block">Excel (CSV) {'\u00b7'} attached automatically</span></span>
          </div>
          {error && <div className="alert error" role="alert">{error}</div>}
        </div>
        <footer className="mail-foot">
          <button type="button" className="btn primary" disabled={busy} onClick={send}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M22 2L11 13M22 2l-7 20-4-9-9-4z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" /></svg>
            {busy ? 'Sending...' : 'Send'}
          </button>
          <button type="button" className="btn ghost" disabled={busy} onClick={onClose}>Discard</button>
        </footer>
      </div>
    </div>
  )
}

/** Type a name or email; pick from the list. Shows "Name <email>" chips. */
function PeopleField({ label, people, onChange, error, extra, autoFocus }) {
  const [q, setQ] = useState('')
  const [list, setList] = useState([])
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const inputRef = useRef(null)

  useEffect(() => {
    if (!open) return undefined
    let alive = true
    const t = setTimeout(() => {
      api.reportRecipients(q.trim()).then((r) => { if (alive) { setList((r || []).filter((p) => !people.some((x) => x.userId === p.userId))); setActive(0) } }).catch(() => {})
    }, 180)
    return () => { alive = false; clearTimeout(t) }
  }, [q, open, people])

  function add(p) {
    onChange([...people, p])
    setQ('')
    setActive(0)
    inputRef.current?.focus()
  }

  function onKey(e) {
    if (e.key === 'ArrowDown') { e.preventDefault(); setActive((a) => Math.min(a + 1, list.length - 1)) }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => Math.max(a - 1, 0)) }
    else if (e.key === 'Enter' && open && list[active]) { e.preventDefault(); add(list[active]) }
    else if (e.key === 'Backspace' && !q && people.length) onChange(people.slice(0, -1))
  }

  return (
    <>
      <div className={`mail-row${error ? ' invalid' : ''}`}>
        <span className="mail-label">{label}</span>
        <div className="mail-chips">
          {people.map((p) => (
            <span key={p.userId} className="mail-chip" title={p.email}>
              {p.fullName} <span className="muted">({p.email})</span>
              <button type="button" aria-label={`Remove ${p.fullName}`} onClick={() => onChange(people.filter((x) => x.userId !== p.userId))}>{'\u00d7'}</button>
            </span>
          ))}
          <input ref={inputRef} className="mail-input" value={q} placeholder={people.length ? '' : 'Type a name or email'} aria-label={label} autoFocus={autoFocus}
            role="combobox" aria-expanded={open && list.length > 0} aria-autocomplete="list" maxLength={60}
            onChange={(e) => { setQ(e.target.value); setOpen(true) }} onFocus={() => setOpen(true)} onBlur={() => setTimeout(() => setOpen(false), 150)} onKeyDown={onKey} />
        </div>
        {extra}
        {open && list.length > 0 && (
          <ul className="mail-suggest" role="listbox">
            {list.map((p, i) => (
              <li key={p.userId} role="option" aria-selected={i === active} className={i === active ? 'on' : ''}
                onMouseDown={(e) => { e.preventDefault(); add(p) }}>
                <span className="mail-avatar" aria-hidden="true">{p.fullName.split(' ').map((w) => w[0]).slice(0, 2).join('').toUpperCase()}</span>
                <span><strong>{p.fullName}</strong><span className="muted small block">{p.email} {'\u00b7'} {p.roleName}</span></span>
              </li>
            ))}
          </ul>
        )}
      </div>
      {error && <small className="field-error mail-err" role="alert">{'\u26a0 '}{error}</small>}
    </>
  )
}

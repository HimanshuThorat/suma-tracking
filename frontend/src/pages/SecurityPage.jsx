import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'
import { Drawer, Field, PageHead, Toast } from '../components.jsx'
import { FIX_MESSAGE, rules } from '../validation.js'
import { fmtDateTime } from '../format.js'

const RIGHTS = {
  SUPER_ADMIN: 'Everything: clients, projects, employees, imports, settings',
  MANAGEMENT_VIEWER: 'Sees all dashboards and reports, changes nothing',
  CLIENT_ADMIN: 'Manages their clients and those projects',
  PROJECT_MANAGER: 'Manages their projects, team and imports',
  TEAM_LEADER: 'Sees their projects, assigns tickets',
  L3_SUPPORT: 'Works tickets of their projects',
  L2_SUPPORT: 'Works tickets of their projects',
  L1_SUPPORT: 'Works tickets of their projects',
}

export default function SecurityPage() {
  const [tab, setTab] = useState('roles')
  const [roles, setRoles] = useState([])
  const [audit, setAudit] = useState([])
  const [entity, setEntity] = useState('')
  const [error, setError] = useState('')

  const [creating, setCreating] = useState(false)
  const [toast, setToast] = useState('')
  const loadRoles = useCallback(() => { api.roles().then(setRoles).catch((e) => setError(e.message)) }, [])
  useEffect(() => { loadRoles() }, [loadRoles])
  useEffect(() => {
    if (tab !== 'audit') return
    api.audit({ entityType: entity, limit: 200 }).then(setAudit).catch((e) => setError(e.message))
  }, [tab, entity])

  return (
    <div className="page">
      <PageHead title="Security" text="Roles, what each role may do, and the audit log of every change.">
        {tab === 'roles' && <button className="btn primary" type="button" onClick={() => setCreating(true)}>Create role</button>}
      </PageHead>
      <div className="segmented" role="tablist">
        <button type="button" role="tab" aria-selected={tab === 'roles'} className={tab === 'roles' ? 'on' : ''} onClick={() => setTab('roles')}>Roles and permissions</button>
        <button type="button" role="tab" aria-selected={tab === 'audit'} className={tab === 'audit' ? 'on' : ''} onClick={() => setTab('audit')}>Audit log</button>
      </div>
      {error && <div className="alert error" role="alert">{error}</div>}
      {tab === 'roles' && (
        <section className="panel flush">
          <div className="table-wrap">
            <table className="table grid-table">
              <thead><tr><th scope="col">Role</th><th scope="col">Group</th><th scope="col">What they can do</th></tr></thead>
              <tbody>
                {roles.map((r) => (
                  <tr key={r.code}>
                    <td className="strong">{r.name}</td>
                    <td>{r.userGroup.replace('_', ' ').toLowerCase()}</td>
                    <td>{RIGHTS[r.code] || r.description || (r.userGroup === 'SUPPORT_USER' ? 'Works tickets of their projects' : 'Leads the projects they are on')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="fine pad">A new role (for example Project Lead or Manager) works like one of two types: a lead type sees and leads the projects they are on; an agent type works tickets of their projects.</p>
        </section>
      )}
      {tab === 'audit' && (
        <section className="panel flush">
          <div className="toolbar flat">
            <label className="field">
              <span>Show</span>
              <select value={entity} onChange={(e) => setEntity(e.target.value)}>
                <option value="">Everything</option>
                <option value="CLIENT">Clients</option>
                <option value="PROJECT">Projects</option>
                <option value="USER">Employees</option>
                <option value="PROJECT_ASSIGNMENT">Team changes</option>
                <option value="TICKET">Tickets</option>
                <option value="IMPORT_BATCH">Imports</option>
              </select>
            </label>
          </div>
          <div className="table-wrap">
            <table className="table grid-table">
              <thead><tr><th scope="col">When</th><th scope="col">Who</th><th scope="col">What</th><th scope="col">Record</th><th scope="col">From</th></tr></thead>
              <tbody>
                {audit.map((a) => (
                  <tr key={a.id}>
                    <td className="nowrap">{fmtDateTime(a.occurredAt)}</td>
                    <td>{a.actorEmail || 'System'}</td>
                    <td>{a.action.replaceAll('_', ' ').toLowerCase()}</td>
                    <td>{a.entityType.replaceAll('_', ' ').toLowerCase()} {a.entityId}</td>
                    <td className="muted">{a.source.toLowerCase()}</td>
                  </tr>
                ))}
                {audit.length === 0 && <tr><td colSpan={5} className="empty-row">Nothing recorded yet.</td></tr>}
              </tbody>
            </table>
          </div>
        </section>
      )}
      {creating && (
        <RoleForm onClose={() => setCreating(false)} onSaved={(r) => { setCreating(false); setToast(`Role ${r.name} created`); loadRoles() }} />
      )}
      <Toast text={toast} onDone={() => setToast('')} />
    </div>
  )
}

function RoleForm({ onClose, onSaved }) {
  const [f, setF] = useState({ name: '', userGroup: 'PROJECT_MANAGER', description: '' })
  const [error, setError] = useState('')
  const [nameErr, setNameErr] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value })

  async function save(e) {
    e.preventDefault()
    setError('')
    const nameError = rules.title('Role name', true)(f.name)
    const descError = rules.maxLen('Description', 500)(f.description)
    setNameErr(nameError)
    if (nameError || descError) { setError(nameError ? FIX_MESSAGE : descError); return }
    setBusy(true)
    try {
      onSaved(await api.createRole({ ...f, name: f.name.trim(), description: f.description.trim() }))
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Drawer title="Create role" onClose={onClose}
      footer={(
        <>
          <button className="btn ghost" type="button" onClick={onClose}>Cancel</button>
          <button className="btn primary" type="submit" form="role-form" disabled={busy}>{busy ? 'Saving...' : 'Add'}</button>
        </>
      )}>
      <form id="role-form" className="form" onSubmit={save} noValidate>
        <Field label="Role name *" error={nameErr}>
          <input value={f.name} onChange={(e) => { set('name')(e); if (nameErr) setNameErr(rules.title('Role name', true)(e.target.value)) }}
            onBlur={() => setNameErr(rules.title('Role name', true)(f.name))} maxLength={100} placeholder="e.g. Project Lead" aria-invalid={Boolean(nameErr)} />
        </Field>
        <Field label="Works like">
          <select value={f.userGroup} onChange={set('userGroup')}>
            <option value="PROJECT_MANAGER">Lead or manager: sees and leads the projects they are on</option>
            <option value="SUPPORT_USER">Support agent: works tickets of their projects</option>
          </select>
        </Field>
        <Field label="Description"><textarea rows={3} value={f.description} onChange={set('description')} maxLength={500} /></Field>
        {error && <div className="alert error" role="alert">{error}</div>}
      </form>
    </Drawer>
  )
}
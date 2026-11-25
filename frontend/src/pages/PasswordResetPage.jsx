import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { rules } from '../validation.js'
import { ThemeToggle } from '../theme.jsx'

// Step 43: "Forgot password?" and "Make a new password" (from the link in the email). No sign-in needed.

function tokenFromHash() {
  const q = window.location.hash.split('?')[1] || ''
  return new URLSearchParams(q).get('token') || ''
}

function passwordProblem(p, email) {
  if (p.length < 10) return 'Use at least 10 characters.'
  if (p.length > 72) return 'Use at most 72 characters.'
  if (!/[A-Za-z]/.test(p) || !/[0-9]/.test(p)) return 'Use at least one letter and one number.'
  if (email && p.trim().toLowerCase() === email.trim().toLowerCase()) return 'The password must not be your email.'
  return ''
}

export default function PasswordResetPage({ mode, theme, onTheme }) {
  return (
    <div className="auth">
      <div className="auth-grid" aria-hidden="true" />
      <header className="auth-top">
        <a className="auth-brand" href="#/about">
          <span className="brand-mark" aria-hidden="true">S</span>
          <span className="brand-text"><span className="brand-name">Suma Tracking</span><span className="brand-sub">Support operations</span></span>
        </a>
        <div className="auth-top-right">
          <a className="auth-link" href="#/login">Back to sign in</a>
          <ThemeToggle theme={theme} onChange={onTheme} />
        </div>
      </header>
      <main className="auth-main single">
        <section className="hud-card" aria-labelledby="reset-title">
          <span className="hud-corner tl" aria-hidden="true" /><span className="hud-corner tr" aria-hidden="true" />
          <span className="hud-corner bl" aria-hidden="true" /><span className="hud-corner br" aria-hidden="true" />
          {mode === 'forgot-password' ? <Forgot /> : <Reset />}
        </section>
      </main>
    </div>
  )
}

function Forgot() {
  const [email, setEmail] = useState('')
  const [err, setErr] = useState('')
  const [error, setError] = useState('')
  const [done, setDone] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    const problem = rules.email('Email', true)(email)
    setErr(problem)
    if (problem) return
    setBusy(true)
    setError('')
    try {
      const res = await api.forgotPassword(email.trim())
      setDone(res.message)
    } catch (ex) {
      setError(ex.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <div className="hud-label"><span className="hud-dot amber" aria-hidden="true" /> PASSWORD HELP</div>
      <h1 id="reset-title">Forgot password?</h1>
      {done ? (
        <div className="reset-done">
          <div className="reset-mail" aria-hidden="true">
            <svg width="44" height="44" viewBox="0 0 24 24" fill="none"><path d="M3 6h18v12H3zM3 7l9 6 9-6" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" /></svg>
          </div>
          <p>{done}</p>
          <p className="muted small">The link works for 30 minutes, one time only. You can ask again after a few minutes if nothing comes.</p>
          <a className="btn primary wide" href="#/login">Back to sign in</a>
        </div>
      ) : (
        <form className="login-form" onSubmit={submit} noValidate>
          <p className="muted">Type your company email. We will send you a link to make a new password.</p>
          <label className={`field${err ? ' invalid' : ''}`}>
            <span>Email</span>
            <input type="email" autoComplete="username" value={email} placeholder="name@sumasoft.com" maxLength={254}
              aria-invalid={Boolean(err)} onChange={(e) => { setEmail(e.target.value); if (err) setErr('') }} />
            {err && <small className="field-error" role="alert">{'\u26a0 '}{err}</small>}
          </label>
          {error && <div className="alert error" role="alert">{error}</div>}
          <button className="btn primary wide" type="submit" disabled={busy}>{busy ? 'Sending...' : 'Send reset link'}</button>
          <a className="forgot-link" href="#/login">I remember it. Back to sign in</a>
        </form>
      )}
    </>
  )
}

function Reset() {
  const [token] = useState(tokenFromHash())
  const [check, setCheck] = useState(null)
  const [p1, setP1] = useState('')
  const [p2, setP2] = useState('')
  const [errs, setErrs] = useState({})
  const [error, setError] = useState('')
  const [done, setDone] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!token) { setCheck({ valid: false }); return }
    api.checkResetLink(token).then(setCheck).catch(() => setCheck({ valid: false }))
  }, [token])

  async function submit(e) {
    e.preventDefault()
    const e1 = passwordProblem(p1, '')
    const e2 = !e1 && p1 !== p2 ? 'The two passwords are not the same.' : ''
    setErrs({ p1: e1, p2: e2 })
    if (e1 || e2) return
    setBusy(true)
    setError('')
    try {
      const res = await api.resetPasswordByLink(token, p1)
      setP1('')
      setP2('')
      setDone(res.message)
    } catch (ex) {
      setError(ex.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <div className="hud-label"><span className="hud-dot" aria-hidden="true" /> NEW PASSWORD</div>
      <h1 id="reset-title">Make a new password</h1>
      {!check && <p className="muted">Checking your link...</p>}
      {check && !check.valid && !done && (
        <div className="reset-done">
          <div className="alert error" role="alert">This link is not valid any more. It may be used already or older than 30 minutes.</div>
          <a className="btn primary wide" href="#/forgot-password">Ask for a new link</a>
        </div>
      )}
      {done && (
        <div className="reset-done">
          <div className="alert ok" role="status">{done}</div>
          <a className="btn primary wide" href="#/login">Sign in</a>
        </div>
      )}
      {check && check.valid && !done && (
        <form className="login-form" onSubmit={submit} noValidate>
          <p className="muted">For the account <strong>{check.email}</strong>. You will be signed out on every other device.</p>
          <label className={`field${errs.p1 ? ' invalid' : ''}`}>
            <span>New password</span>
            <input type="password" autoComplete="new-password" value={p1} maxLength={72} onChange={(e) => setP1(e.target.value)} />
            {errs.p1 ? <small className="field-error" role="alert">{'\u26a0 '}{errs.p1}</small> : <small className="muted">10 or more characters, with letters and numbers.</small>}
          </label>
          <label className={`field${errs.p2 ? ' invalid' : ''}`}>
            <span>Type it again</span>
            <input type="password" autoComplete="new-password" value={p2} maxLength={72} onChange={(e) => setP2(e.target.value)} />
            {errs.p2 && <small className="field-error" role="alert">{'\u26a0 '}{errs.p2}</small>}
          </label>
          {error && <div className="alert error" role="alert">{error}</div>}
          <button className="btn primary wide" type="submit" disabled={busy}>{busy ? 'Saving...' : 'Save new password'}</button>
        </form>
      )}
    </>
  )
}

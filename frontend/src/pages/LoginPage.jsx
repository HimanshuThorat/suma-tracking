import { useState } from 'react'
import { api, setToken } from '../api.js'
import { rules } from '../validation.js'

export default function LoginPage({ onLoggedIn }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [emailErr, setEmailErr] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    setError('')
    const emailError = rules.email('Email', true)(email)
    setEmailErr(emailError)
    if (emailError) return
    if (!password) { setError('Enter your password.'); return }
    setBusy(true)
    try {
      const res = await api.login(email.trim(), password)
      setToken(res.token)
      setPassword('')
      onLoggedIn({ ...res.user, mustChangePassword: res.mustChangePassword })
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login">
      <section className="login-side" aria-hidden="true">
        <div className="login-side-inner">
          <span className="brand-mark big">S</span>
          <h1>Every project&apos;s tickets, in one place.</h1>
          <p>Zendesk, Keypanion and the agents&apos; daily sheets, counted and tracked together for Suma Soft support.</p>
          <ShiftDial />
        </div>
      </section>
      <section className="login-form-wrap">
        <form className="login-form" onSubmit={submit} noValidate>
          <h2>Sign in</h2>
          <p className="muted">Use the email and password given by your administrator.</p>
          <label className={`field${emailErr ? ' invalid' : ''}`}>
            <span>Email</span>
            <input type="email" autoComplete="username" value={email} placeholder="name@sumasoft.com" aria-invalid={Boolean(emailErr)}
              onChange={(e) => { setEmail(e.target.value); if (emailErr) setEmailErr(rules.email('Email', true)(e.target.value)) }}
              onBlur={() => email && setEmailErr(rules.email('Email', true)(email))} />
            {emailErr && <small className="field-error" role="alert">{'\u26a0 '}{emailErr}</small>}
          </label>
          <label className="field">
            <span>Password</span>
            <input type="password" autoComplete="current-password" value={password}
              onChange={(e) => setPassword(e.target.value)} required />
          </label>
          {error && <div className="alert error" role="alert">{error}</div>}
          <button className="btn primary wide" type="submit" disabled={busy}>
            {busy ? 'Signing in...' : 'Sign in'}
          </button>
          <p className="fine">After 5 wrong passwords the account is locked for 15 minutes.</p>
        </form>
      </section>
    </div>
  )
}

/** A small 24-hour dial, the visual idea of the product: support runs around the clock in shifts. */
function ShiftDial() {
  const ticks = Array.from({ length: 24 }, (_, h) => h)
  return (
    <svg className="dial" viewBox="0 0 200 200">
      <circle cx="100" cy="100" r="86" fill="none" stroke="rgba(255,255,255,.12)" strokeWidth="14" />
      <circle cx="100" cy="100" r="86" fill="none" stroke="#14A38B" strokeWidth="14"
        strokeDasharray="180 360" transform="rotate(-90 100 100)" />
      <circle cx="100" cy="100" r="86" fill="none" stroke="#E8A33D" strokeWidth="14"
        strokeDasharray="90 450" strokeDashoffset="-180" transform="rotate(-90 100 100)" />
      {ticks.map((h) => {
        const a = (h / 24) * Math.PI * 2 - Math.PI / 2
        return (
          <line key={h} x1={100 + Math.cos(a) * 66} y1={100 + Math.sin(a) * 66}
            x2={100 + Math.cos(a) * (h % 6 === 0 ? 56 : 61)} y2={100 + Math.sin(a) * (h % 6 === 0 ? 56 : 61)}
            stroke="rgba(255,255,255,.5)" strokeWidth={h % 6 === 0 ? 2 : 1} />
        )
      })}
      <text x="100" y="106" textAnchor="middle" fill="#fff" fontSize="18" fontWeight="700">24 h</text>
    </svg>
  )
}
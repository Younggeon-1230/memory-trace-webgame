import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, USE_MOCK } from '../api'
import { useAuth } from '../auth/AuthContext'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as { from?: string; justSignedUp?: string } | null
  const [loginId, setLoginId] = useState(state?.justSignedUp ?? '')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!loginId || !password) return setError('아이디와 비밀번호를 입력하세요.')
    setBusy(true)
    setError('')
    try {
      await login(loginId.trim(), password)
      navigate(state?.from ?? '/cases', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '로그인하지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="auth-page">
      <form className="auth-card" onSubmit={onSubmit} noValidate>
        <p className="eyebrow"><span /> DETECTIVE LOGIN</p>
        <h1>로그인</h1>
        <p className="auth-sub">탐정 수첩을 열려면 로그인하세요.</p>
        {state?.justSignedUp && <p className="form-ok">가입이 완료되었습니다. 로그인해 주세요.</p>}
        <label className="field">아이디
          <input value={loginId} onChange={(e) => setLoginId(e.target.value)} autoComplete="username" placeholder="detective01" />
        </label>
        <label className="field">비밀번호
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" placeholder="비밀번호 입력" />
        </label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="primary-link full" type="submit" disabled={busy}>{busy ? '확인 중…' : '로그인'}</button>
        <p className="auth-foot">아직 회원이 아니신가요? <Link to="/signup">회원가입</Link></p>
        {USE_MOCK && <p className="mock-hint">임시 서버 모드 · 체험 계정 detective01 / pass1234!</p>}
      </form>
    </main>
  )
}

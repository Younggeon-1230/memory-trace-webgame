import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api'

// 검증 규칙은 docs/api_spec.md 1장과 같다. 서버도 같은 검사를 한 번 더 한다.
function validate(f: { loginId: string; password: string; confirm: string; nickname: string }) {
  const e: Record<string, string> = {}
  if (!/^[a-z0-9_]{4,30}$/.test(f.loginId)) e.loginId = '영문 소문자·숫자·_ 4~30자로 입력하세요.'
  if (f.password.length < 8 || f.password.length > 64) e.password = '비밀번호는 8~64자입니다.'
  else if (new TextEncoder().encode(f.password).length > 72) e.password = '비밀번호가 너무 깁니다. 한글·특수 문자는 글자당 여러 바이트를 차지합니다.'
  if (f.confirm !== f.password) e.confirm = '비밀번호가 서로 다릅니다.'
  const n = f.nickname.trim().length
  if (n < 2 || n > 20) e.nickname = '닉네임은 2~20자입니다.'
  return e
}

export default function SignupPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ loginId: '', password: '', confirm: '', nickname: '' })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [serverError, setServerError] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value })

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    const found = validate(form)
    setErrors(found)
    setServerError('')
    if (Object.keys(found).length) return
    setBusy(true)
    try {
      await api.signup({ loginId: form.loginId, password: form.password, nickname: form.nickname.trim() })
      navigate('/login', { state: { justSignedUp: form.loginId } })
    } catch (err) {
      if (err instanceof ApiError && err.code === 'LOGIN_ID_TAKEN') setErrors({ loginId: err.message })
      else setServerError(err instanceof ApiError ? err.message : '가입하지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  const field = (k: keyof typeof form, label: string, props: Record<string, string>) => (
    <label className="field">{label}
      <input value={form[k]} onChange={set(k)} aria-invalid={!!errors[k]} {...props} />
      {errors[k] && <span className="field-error">{errors[k]}</span>}
    </label>
  )

  return (
    <main className="auth-page">
      <form className="auth-card" onSubmit={onSubmit} noValidate>
        <p className="eyebrow"><span /> NEW DETECTIVE</p>
        <h1>회원가입</h1>
        <p className="auth-sub">수사 기록은 회원별로 저장됩니다.</p>
        {field('loginId', '아이디', { autoComplete: 'username', placeholder: '영문 소문자·숫자 4~30자' })}
        {field('password', '비밀번호', { type: 'password', autoComplete: 'new-password', placeholder: '8자 이상' })}
        {field('confirm', '비밀번호 확인', { type: 'password', autoComplete: 'new-password' })}
        {field('nickname', '닉네임', { placeholder: '2~20자' })}
        {serverError && <p className="form-error" role="alert">{serverError}</p>}
        <button className="primary-link full" type="submit" disabled={busy}>{busy ? '가입 중…' : '가입하기'}</button>
        <p className="auth-foot">이미 회원이신가요? <Link to="/login">로그인</Link></p>
      </form>
    </main>
  )
}

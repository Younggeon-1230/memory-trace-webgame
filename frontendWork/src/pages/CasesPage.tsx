import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, ApiError, type CaseSummary } from '../api'
import SceneImage from '../components/SceneImage'
import { caseLabel } from '../lib/labels'

function badge(c: CaseSummary) {
  const s = c.myStatus
  if (s.inProgressSessionId) return { cls: 'is-progress', text: '진행 중' }
  if (s.bestGrade) return { cls: 'is-solved', text: `해결 · 최고 ${s.bestGrade}` }
  if (s.playCount > 0) return { cls: 'is-failed', text: '미해결 · 재도전 가능' }
  return { cls: '', text: '미조사' }
}

export default function CasesPage() {
  const navigate = useNavigate()
  const [cases, setCases] = useState<CaseSummary[] | null>(null)
  const [error, setError] = useState('')
  const [opening, setOpening] = useState<number | null>(null)

  useEffect(() => {
    api.getCases().then(setCases).catch((e) => setError(e instanceof ApiError ? e.message : '사건 목록을 불러오지 못했습니다.'))
  }, [])

  const open = async (c: CaseSummary) => {
    setOpening(c.caseId)
    try {
      const state = await api.startSession(c.caseId)
      navigate(`/play/${state.sessionId}`, { state: { fresh: !c.myStatus.inProgressSessionId } })
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '수사를 시작하지 못했습니다.')
      setOpening(null)
    }
  }

  return (
    <main className="page">
      <div className="page-head">
        <p className="eyebrow"><span /> INCIDENT ARCHIVE</p>
        <h1>사건 목록</h1>
        <p>조사할 사건을 고르세요. 진행 중인 사건은 이어서 할 수 있어요.</p>
      </div>
      {error && <p className="form-error" role="alert">{error}</p>}
      {!cases && !error && <p className="muted">사건 기록을 여는 중…</p>}
      <div className="case-grid">
        {cases?.map((c) => {
          const b = badge(c)
          const s = c.myStatus
          const label = s.inProgressSessionId ? '이어 하기' : s.playCount > 0 ? '다시 하기' : '조사 시작'
          return (
            <article key={c.caseId} className={`case-tile ${s.inProgressSessionId ? 'is-current' : ''}`}>
              <div className="case-thumb"><SceneImage src={c.sceneImagePath} alt={`${c.title} 현장`} /></div>
              <div className="case-tile-body">
                <div className="case-tile-meta">
                  <span className="case-code">{caseLabel(c.caseCode)}</span>
                  <span className={`pill ${b.cls}`}>{b.text}</span>
                </div>
                <h2>{c.title}</h2>
                <p>{c.subtitle}</p>
                <dl className="case-tile-stats">
                  <div><dt>플레이</dt><dd>{s.playCount}회</dd></div>
                  <div><dt>최고 점수</dt><dd>{s.bestScore ?? '–'}</dd></div>
                </dl>
                <button
                  type="button"
                  className={s.inProgressSessionId ? 'primary-link full' : 'outline-button full'}
                  onClick={() => open(c)}
                  disabled={opening !== null}
                >
                  {opening === c.caseId ? '사건 파일 여는 중…' : label}
                </button>
              </div>
            </article>
          )
        })}
      </div>
    </main>
  )
}

import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, ApiError, type SessionResult } from '../api'
import { caseLabel, STATUS_KO } from '../lib/labels'

export default function ResultPage() {
  const id = Number(useParams().sessionId)
  const navigate = useNavigate()
  const [r, setR] = useState<SessionResult | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api.getResult(id).then(setR).catch((e) => {
      if (e instanceof ApiError && e.code === 'SESSION_IN_PROGRESS') navigate(`/play/${id}`, { replace: true })
      else setError(e instanceof ApiError ? e.message : '결과를 불러오지 못했습니다.')
    })
  }, [id, navigate])

  if (error) return <main className="page"><p className="form-error">{error}</p></main>
  if (!r) return <div className="page-loading">결과를 정리하는 중…</div>

  const solved = r.status === 'SOLVED'
  const wrongAcc = r.accusationCount - (solved ? 1 : 0)
  const replay = async () => {
    setBusy(true)
    try {
      const s = await api.startSession(r.caseId)
      navigate(`/play/${s.sessionId}`, { state: { fresh: true } })
    } catch {
      setBusy(false)
    }
  }

  return (
    <main className="page">
      <div className="result-card">
        <div className="result-story">
          <span className={`pill ${solved ? 'is-solved' : 'is-failed'}`}>{solved ? '사건 해결' : '수사 실패'}</span>
          <p className="case-code">{caseLabel(r.caseCode)} · {r.title}</p>
          <h1>메타몽이 변신했던 모습은 <em>{r.answer.nameKo}</em>{solved ? '!' : '였습니다.'}</h1>
          <div className="result-answer">
            <img src={r.answer.spritePath} alt={r.answer.nameKo} width={112} height={112} />
            <p>{solved ? '오박사: “역시 자네라면 해낼 줄 알았네!”' : '오박사: “증거를 다시 한번 차근차근 맞춰 보게나.”'}</p>
          </div>
          <section><h2>사건의 진상</h2><p>{r.truthText}</p></section>
          <section><h2>메타몽의 변신 동기</h2><p>{r.motiveText}</p></section>
          <section><h2>반전</h2><p>{r.twistText}</p></section>
          <section>
            <h2>오답 용의자 정리</h2>
            <ul className="exclusion-list">
              {r.exclusions.map((x) => (
                <li key={x.caseSuspectId}>
                  <strong>{x.nameKo}</strong>
                  <span>{STATUS_KO[x.status]}{x.basisClueName ? ` · 근거: ${x.basisClueName}` : ''}</span>
                </li>
              ))}
            </ul>
          </section>
        </div>

        <aside className="result-score">
          <div className="score-head">
            <span>최종 점수</span>
            <span className={`grade grade-${r.grade ?? 'none'}`}>{r.grade ?? '–'}</span>
          </div>
          <strong className="score-big">{r.score}점</strong>
          <dl className="score-lines">
            <div><dt>기본 점수</dt><dd>100</dd></div>
            <div><dt>틀린 배제 × {r.wrongExclusionCount}</dt><dd className={r.wrongExclusionCount ? 'minus' : ''}>{r.wrongExclusionCount ? `−${r.wrongExclusionCount * 10}` : 0}</dd></div>
            <div><dt>틀린 지목 × {wrongAcc}</dt><dd className={wrongAcc ? 'minus' : ''}>{wrongAcc ? `−${wrongAcc * 30}` : 0}</dd></div>
            <div><dt>증거로 전원 배제 보너스</dt><dd className={r.bonusAwarded ? 'plus' : 'muted'}>{r.bonusAwarded ? '+10' : '미달성'}</dd></div>
            {!solved && <div><dt>수사 실패</dt><dd className="minus">점수 0</dd></div>}
          </dl>
          <p className="muted small">S: 감점 없이 오답 3명을 모두 증거로 배제 · A: 80점 이상 · B: 80점 미만</p>
          <div className="result-actions">
            <Link className="primary-link full" to="/cases">사건 목록으로</Link>
            <div className="row-2">
              <Link className="outline-button" to="/mypage">마이페이지</Link>
              <button type="button" className="outline-button" onClick={replay} disabled={busy}>다시 하기</button>
            </div>
          </div>
        </aside>
      </div>
    </main>
  )
}

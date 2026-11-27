import { Fragment, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError, type ActionLog, type CaseRecordSummary, type SessionRecord } from '../api'
import { useAuth } from '../auth/AuthContext'
import { ACTION_KO, caseLabel, fmtDate, RESULT_KO, SESSION_KO } from '../lib/labels'

const PAGE_SIZE = 10

export default function MyPage() {
  const { user } = useAuth()
  const [summary, setSummary] = useState<CaseRecordSummary[] | null>(null)
  const [records, setRecords] = useState<SessionRecord[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [error, setError] = useState('')
  const [openId, setOpenId] = useState<number | null>(null)
  const [actions, setActions] = useState<Record<number, ActionLog[]>>({})

  useEffect(() => {
    api.getMySummary().then(setSummary).catch((e) => setError(e instanceof ApiError ? e.message : '기록을 불러오지 못했습니다.'))
  }, [])
  useEffect(() => {
    api.getMySessions({ page, size: PAGE_SIZE }).then((p) => { setRecords(p.content); setTotal(p.totalElements) }).catch(() => undefined)
  }, [page])

  const toggle = async (id: number) => {
    if (openId === id) return setOpenId(null)
    setOpenId(id)
    if (!actions[id]) {
      const list = await api.getMyActions(id).catch(() => [])
      setActions((m) => ({ ...m, [id]: list }))
    }
  }

  const finished = summary?.reduce((n, c) => n + c.playCount, 0) ?? 0
  const solvedCount = summary?.reduce((n, c) => n + c.solvedCount, 0) ?? 0
  const solvedCases = summary?.filter((c) => c.solvedCount > 0).length ?? 0
  const bestScore = summary?.reduce<number | null>((m, c) => (c.bestScore !== null && (m === null || c.bestScore > m) ? c.bestScore : m), null)
  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  return (
    <main className="page">
      <div className="page-head">
        <p className="eyebrow"><span /> DETECTIVE RECORD</p>
        <h1>{user?.nickname} 탐정의 수사 기록</h1>
        <p>끝난 수사와 진행 중인 수사가 모두 저장됩니다.</p>
      </div>
      {error && <p className="form-error">{error}</p>}

      <div className="stat-grid">
        <div className="stat"><span>해결한 사건</span><strong>{solvedCases} / {summary?.length ?? 3}</strong></div>
        <div className="stat"><span>성공률</span><strong>{finished ? Math.round((solvedCount / finished) * 100) : 0}%</strong></div>
        <div className="stat"><span>최고 점수</span><strong>{bestScore ?? '–'}</strong></div>
        <div className="stat"><span>끝낸 수사</span><strong>{finished}회</strong></div>
      </div>

      <div className="best-grid">
        {summary?.map((c) => (
          <div key={c.caseId} className="best-item">
            <span><span className="case-code">{caseLabel(c.caseCode)}</span> {c.title}</span>
            <strong>{c.bestScore !== null ? `${c.bestScore}점 · ${c.bestGrade}` : <span className="muted">기록 없음</span>}</strong>
          </div>
        ))}
      </div>

      <h2 className="sub-head">전체 플레이 기록</h2>
      {records.length === 0 ? (
        <p className="muted">아직 수사 기록이 없습니다. <Link to="/cases">첫 사건을 시작해 보세요.</Link></p>
      ) : (
        <div className="table-wrap">
          <table className="record-table">
            <thead>
              <tr><th>시작 일시</th><th>사건</th><th>결과</th><th>점수</th><th>등급</th><th>틀린 배제</th><th>조사한 단서</th><th>행동 기록</th></tr>
            </thead>
            <tbody>
              {records.map((r) => (
                <Fragment key={r.sessionId}>
                  <tr>
                    <td>{fmtDate(r.startedAt)}</td>
                    <td>{caseLabel(r.caseCode)}</td>
                    <td className={`st-${r.status}`}>{SESSION_KO[r.status]}</td>
                    <td>{r.score}</td>
                    <td>{r.grade ?? '–'}</td>
                    <td>{r.wrongExclusionCount}</td>
                    <td>{r.collectedClueCount} / {r.totalSceneClueCount}</td>
                    <td>
                      {r.status === 'IN_PROGRESS'
                        ? <Link to={`/play/${r.sessionId}`}>이어 하기</Link>
                        : <button type="button" className="link-button" onClick={() => toggle(r.sessionId)} aria-expanded={openId === r.sessionId}>{openId === r.sessionId ? '접기' : '보기'}</button>}
                    </td>
                  </tr>
                  {openId === r.sessionId && (
                    <tr className="timeline-row">
                      <td colSpan={8}>
                        <ol className="timeline">
                          {(actions[r.sessionId] ?? []).map((a, i) => (
                            <li key={i} className={`res-${a.result}`}>
                              <span className="tl-time">{fmtDate(a.createdAt).slice(11)}</span>
                              <span className="tl-type">{ACTION_KO[a.actionType]}</span>
                              <span>{[a.clueName, a.suspectName].filter(Boolean).join(' → ')}</span>
                              <span className="tl-res">{RESULT_KO[a.result]}{a.scoreDelta ? ` (${a.scoreDelta > 0 ? '+' : ''}${a.scoreDelta})` : ''}</span>
                            </li>
                          ))}
                          {!actions[r.sessionId] && <li>불러오는 중…</li>}
                        </ol>
                        <Link to={`/result/${r.sessionId}`}>결과 화면 다시 보기 →</Link>
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {pages > 1 && (
        <div className="pager">
          <button type="button" className="outline-button small" disabled={page === 0} onClick={() => setPage(page - 1)}>이전</button>
          <span>{page + 1} / {pages}</span>
          <button type="button" className="outline-button small" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>다음</button>
        </div>
      )}
    </main>
  )
}

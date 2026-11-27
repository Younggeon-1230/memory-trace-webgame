import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { api, ApiError, newRequestId, type ActionResponse, type Clue, type GameState, type Suspect } from '../api'
import Modal from '../components/Modal'
import SceneImage from '../components/SceneImage'
import SuspectFile from '../components/SuspectFile'
import { caseLabel, STATUS_KO } from '../lib/labels'

type Feedback = { tone: 'ok' | 'bad' | 'info'; title: string; text?: string; rebuttal?: string }
type Confirm = { kind: 'exclude'; clue: Clue; suspect: Suspect } | { kind: 'accuse'; suspect: Suspect }

export default function GamePage() {
  const { sessionId } = useParams()
  const id = Number(sessionId)
  const navigate = useNavigate()
  const location = useLocation()
  const [state, setState] = useState<GameState | null>(null)
  const [loadError, setLoadError] = useState('')
  const [tab, setTab] = useState<'notebook' | 'dictionary'>('notebook')
  const [selectedClueId, setSelectedClueId] = useState<number | null>(null)
  const [showHints, setShowHints] = useState(false)
  const [briefingOpen, setBriefingOpen] = useState(Boolean((location.state as { fresh?: boolean } | null)?.fresh))
  const [fileSuspect, setFileSuspect] = useState<Suspect | null>(null)
  const [accuseOpen, setAccuseOpen] = useState(false)
  const [confirm, setConfirm] = useState<Confirm | null>(null)
  const [feedback, setFeedback] = useState<Feedback | null>(null)
  const [newIds, setNewIds] = useState<number[]>([])
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api.getSession(id).then((s) => {
      if (s.status !== 'IN_PROGRESS') navigate(`/result/${id}`, { replace: true })
      else setState(s)
    }).catch((e) => setLoadError(e instanceof ApiError ? e.message : '수사 기록을 불러오지 못했습니다.'))
  }, [id, navigate])

  // 안내 문구는 잠시 뒤 사라지게 (실패·반박은 직접 닫을 때까지 유지)
  useEffect(() => {
    if (!feedback || feedback.tone === 'bad') return
    const t = setTimeout(() => setFeedback(null), 5000)
    return () => clearTimeout(t)
  }, [feedback])

  const run = useCallback(async (call: () => Promise<ActionResponse>, onDone: (r: ActionResponse) => void) => {
    setBusy(true)
    try {
      const r = await call()
      setState(r.state)
      onDone(r)
      if (r.state.status !== 'IN_PROGRESS') navigate(`/result/${id}`)
    } catch (e) {
      setFeedback({ tone: 'bad', title: '요청을 처리하지 못했습니다', text: e instanceof ApiError ? e.message : undefined })
    } finally {
      setBusy(false)
    }
  }, [id, navigate])

  const clueMap = useMemo(() => new Map(state?.clues.map((c) => [c.clueId, c]) ?? []), [state])
  const selectedClue = selectedClueId ? clueMap.get(selectedClueId) ?? null : null

  if (loadError) return <main className="page"><p className="form-error">{loadError}</p><Link to="/cases">사건 목록으로</Link></main>
  if (!state) return <div className="page-loading">현장으로 이동하는 중…</div>

  const collectedScene = state.sceneAreas.filter((a) => a.collected).length
  const active = state.suspects.filter((s) => s.status === 'ACTIVE')

  const investigate = (clueId: number) => {
    if (busy) return
    run(() => api.investigate(id, { requestId: newRequestId(), clueId }), (r) => {
      if (r.result === 'REJECTED') return setFeedback({ tone: 'info', title: r.message })
      const found = r.state.clues.find((c) => c.clueId === clueId)
      const inferred = (r.inferredClueIds ?? []).map((cid) => r.state.clues.find((c) => c.clueId === cid)?.name).filter(Boolean)
      setNewIds([clueId, ...(r.inferredClueIds ?? [])])
      setTab('notebook')
      setFeedback({
        tone: 'ok',
        title: found ? `${found.clueCode} · ${found.name}` : r.message,
        text: found?.detailText,
        rebuttal: inferred.length ? `새 추론: ${inferred.join(', ')}` : undefined,
      })
    })
  }

  const doConfirm = () => {
    if (!confirm) return
    const c = confirm
    setConfirm(null)
    if (c.kind === 'exclude') {
      run(() => api.exclude(id, { requestId: newRequestId(), clueId: c.clue.clueId, caseSuspectId: c.suspect.caseSuspectId }), (r) => {
        if (r.result === 'SUCCESS') { setSelectedClueId(null); setFeedback({ tone: 'ok', title: '배제 성공', text: r.message }) }
        else if (r.result === 'FAIL') setFeedback({ tone: 'bad', title: `배제 실패 · ${r.scoreDelta}점`, text: r.message, rebuttal: r.rebuttalText })
        else setFeedback({ tone: 'info', title: r.message })
      })
    } else {
      setAccuseOpen(false)
      run(() => api.accuse(id, { requestId: newRequestId(), caseSuspectId: c.suspect.caseSuspectId }), (r) => {
        if (r.result === 'FAIL') setFeedback({ tone: 'bad', title: `지목 실패 · ${r.scoreDelta}점`, text: r.message })
        else if (r.result === 'REJECTED') setFeedback({ tone: 'info', title: r.message })
      })
    }
  }

  return (
    <div className="game-shell">
      <header className="game-bar">
        <div className="game-bar-left">
          <Link to="/cases" className="back-link">← 사건 목록</Link>
          <h1><span className="case-code">{caseLabel(state.case.caseCode)}</span> {state.case.title}</h1>
          <button type="button" className="outline-button small" onClick={() => setBriefingOpen(true)}>브리핑</button>
        </div>
        <dl className="game-stats">
          <div><dt>조사</dt><dd>{collectedScene} / {state.sceneAreas.length}</dd></div>
          <div><dt>점수</dt><dd>{state.score}</dd></div>
          <div><dt>남은 지목</dt><dd>{state.accusationsLeft}회</dd></div>
        </dl>
        <button type="button" className="primary-link accuse-button" onClick={() => setAccuseOpen(true)} disabled={busy}>지목하기</button>
      </header>

      <div className="game-main">
        <section className="scene" aria-label="사건 현장">
          <div className="scene-frame">
            <SceneImage src={state.case.sceneImagePath} alt={`${state.case.title} 현장`} className="scene-img" />
            {state.sceneAreas.map((a) => {
              const clue = clueMap.get(a.clueId)
              return (
                <button
                  key={a.clueId}
                  type="button"
                  className={`hotspot ${a.collected ? 'is-collected' : ''} ${showHints ? 'show-hint' : ''}`}
                  style={{ left: `${a.x * 100}%`, top: `${a.y * 100}%`, width: `${a.w * 100}%`, height: `${a.h * 100}%` }}
                  onClick={() => (a.collected ? setSelectedClueId(a.clueId) : investigate(a.clueId))}
                  aria-label={a.collected ? `조사 완료: ${clue?.name}` : '조사하지 않은 곳'}
                  disabled={busy}
                >
                  {a.collected && <span className="hotspot-tag">{clue?.clueCode} ✓</span>}
                </button>
              )
            })}
          </div>
          <div className="scene-tools">
            <span>현장을 클릭해 단서를 찾으세요.</span>
            <label className="check"><input type="checkbox" checked={showHints} onChange={(e) => setShowHints(e.target.checked)} /> 조사 지점 표시</label>
          </div>
        </section>

        <aside className="side-panel">
          <div className="side-panel-inner">
          <div className="tabs" role="tablist">
            <button type="button" role="tab" aria-selected={tab === 'notebook'} onClick={() => setTab('notebook')}>증거 수첩 ({state.clues.length})</button>
            <button type="button" role="tab" aria-selected={tab === 'dictionary'} onClick={() => setTab('dictionary')}>흔적 사전 ({state.traceDictionary.length})</button>
          </div>
          {tab === 'notebook' ? (
            <div className="tab-body">
              {state.clues.length === 0 && <p className="muted">아직 수집한 단서가 없습니다. 현장을 조사하세요.</p>}
              {state.clues.map((c) => {
                const on = c.clueId === selectedClueId
                return (
                  <button
                    key={c.clueId}
                    type="button"
                    className={`clue-card ${on ? 'is-selected' : ''} ${c.kind === 'INFERENCE' ? 'is-inference' : ''} ${newIds.includes(c.clueId) ? 'is-new' : ''}`}
                    onClick={() => setSelectedClueId(on ? null : c.clueId)}
                    aria-pressed={on}
                  >
                    <span className="clue-head">
                      <span className="case-code">{c.clueCode}</span>
                      <strong>{c.name}</strong>
                      {c.kind === 'INFERENCE' && <span className="pill is-progress">추론</span>}
                      {on && <span className="pill is-solved">증거로 선택</span>}
                    </span>
                    <span className="clue-summary">{c.summary}</span>
                    {on && <span className="clue-detail">{c.detailText}</span>}
                  </button>
                )
              })}
            </div>
          ) : (
            <div className="tab-body">
              <p className="muted small">서버가 배제를 판정할 때 쓰는 기준입니다. 어떤 단서가 어떤 기준에 해당하는지는 직접 추리하세요.</p>
              <ol className="dict-list">
                {state.traceDictionary.map((d, i) => <li key={i}>{d.basisText}</li>)}
              </ol>
            </div>
          )}
          {feedback && (
            <div className={`feedback tone-${feedback.tone}`} role="status">
              <button type="button" className="close-button" onClick={() => setFeedback(null)} aria-label="닫기">×</button>
              <strong>{feedback.title}</strong>
              {feedback.text && <p>{feedback.text}</p>}
              {feedback.rebuttal && <p className="toast-quote">{feedback.tone === 'bad' ? `오박사: “${feedback.rebuttal}”` : feedback.rebuttal}</p>}
            </div>
          )}
          <p className="panel-hint">
            {selectedClue ? <>선택한 증거 <b>{selectedClue.name}</b> · 아래 용의자의 <b>배제</b>를 누르세요.</> : '증거를 고른 뒤, 아래 용의자의 [배제]를 누르세요.'}
          </p>
          </div>
        </aside>
      </div>

      <section className="suspect-row" aria-label="용의자">
        {state.suspects.map((s) => {
          const out = s.status !== 'ACTIVE'
          return (
            <article key={s.caseSuspectId} className={`suspect-card ${out ? 'is-out' : ''}`}>
              <img className="sprite" src={s.spritePath} alt="" width={96} height={96} />
              <div className="suspect-body">
                <strong className="suspect-name">{s.nameKo}</strong>
                {out ? (
                  <span className="pill is-out-pill">{STATUS_KO[s.status]}</span>
                ) : (
                  <span className="suspect-sus">{s.suspicionText}</span>
                )}
                {s.basisClueId && <span className="muted small">근거: {clueMap.get(s.basisClueId)?.name}</span>}
                <div className="suspect-actions">
                  <button type="button" className="outline-button small" onClick={() => setFileSuspect(s)}>파일</button>
                  {!out && (
                    <button
                      type="button"
                      className="dark-button small"
                      disabled={!selectedClue || busy}
                      title={selectedClue ? undefined : '먼저 증거 수첩에서 증거를 고르세요'}
                      onClick={() => selectedClue && setConfirm({ kind: 'exclude', clue: selectedClue, suspect: s })}
                    >배제</button>
                  )}
                </div>
              </div>
            </article>
          )
        })}
      </section>

      {briefingOpen && (
        <Modal title={state.case.title} eyebrow={`CASE BRIEFING · ${caseLabel(state.case.caseCode)}`} onClose={() => setBriefingOpen(false)}>
          <p className="modal-text">{state.case.briefing}</p>
          <div className="briefing-note">
            <strong>수사 원칙</strong>
            <span>추측이 아닌, 수집한 증거로만 용의자를 배제할 수 있습니다. 잘못된 배제 -10점, 잘못된 지목 -30점, 지목 기회는 두 번입니다.</span>
          </div>
          <button type="button" className="primary-link full" onClick={() => setBriefingOpen(false)}>현장 조사 시작</button>
        </Modal>
      )}

      {fileSuspect && <SuspectFile suspect={fileSuspect} basisName={fileSuspect.basisClueId ? clueMap.get(fileSuspect.basisClueId)?.name : undefined} onClose={() => setFileSuspect(null)} />}

      {accuseOpen && (
        <Modal title="메타몽은 누구로 변신했나?" eyebrow={`FINAL ACCUSATION · 남은 기회 ${state.accusationsLeft}회`} onClose={() => setAccuseOpen(false)}>
          <p className="modal-text">용의선상에 남은 용의자 중 한 명을 지목하세요. 틀리면 30점이 깎이고, 두 번 틀리면 수사는 실패로 끝납니다.</p>
          <div className="accuse-grid">
            {active.map((s) => (
              <button key={s.caseSuspectId} type="button" className="accuse-option" onClick={() => setConfirm({ kind: 'accuse', suspect: s })}>
                <img src={s.spritePath} alt="" width={72} height={72} />
                <span>{s.nameKo}</span>
              </button>
            ))}
          </div>
          {state.suspects.length - active.length < 3 && (
            <p className="muted small">아직 증거로 배제하지 않은 용의자가 있습니다. 오답 3명을 모두 증거로 배제하고 지목하면 보너스 +10점!</p>
          )}
        </Modal>
      )}

      {confirm && (
        <Modal title={confirm.kind === 'exclude' ? '이 추리를 제출할까요?' : `${confirm.suspect.nameKo}을(를) 지목할까요?`} onClose={() => setConfirm(null)} small>
          <p className="modal-text">
            {confirm.kind === 'exclude'
              ? <>증거 <b>{confirm.clue.name}</b>에 따르면 <b>{confirm.suspect.nameKo}</b>은(는) 불가능하다. 틀리면 10점이 깎입니다.</>
              : <>지목은 되돌릴 수 없습니다. 틀리면 30점이 깎입니다.</>}
          </p>
          <div className="modal-actions">
            <button type="button" className="outline-button" onClick={() => setConfirm(null)}>취소</button>
            <button type="button" className="primary-link" onClick={doConfirm}>{confirm.kind === 'exclude' ? '배제 제출' : '지목'}</button>
          </div>
        </Modal>
      )}
    </div>
  )
}

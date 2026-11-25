import { useState } from 'react'
import './App.css'

type CaseFile = {
  code: string
  title: string
  tagline: string
  status: 'playable' | 'coming-soon'
  meta: string
}

const cases: CaseFile[] = [
  { code: 'CASE 01', title: '자정의 연구소', tagline: '초자연 현상의 정체를 밝혀라', status: 'coming-soon', meta: '기록 복원 중' },
  { code: 'CASE 02', title: '불타버린 실험실', tagline: '화재의 시작점을 추적하라', status: 'coming-soon', meta: '감식 진행 중' },
  { code: 'CASE 03', title: '들어간 발자국만 있는 창고', tagline: '남겨진 발자국의 모순을 풀어라', status: 'playable', meta: '수사 가능' },
]

function App() {
  const [selectedCase, setSelectedCase] = useState(cases[2])
  const [isBriefingOpen, setIsBriefingOpen] = useState(false)

  return (
    <main className="app-shell">
      <header className="topbar">
        <a className="brand" href="#top" aria-label="Memory Trace 홈">
          <span className="brand-mark" aria-hidden="true">M</span>
          <span><strong>MEMORY TRACE</strong><small>LAB INCIDENT ARCHIVE</small></span>
        </a>
        <nav className="topnav" aria-label="주요 메뉴">
          <a href="#cases">사건 기록</a>
          <button type="button" className="login-button">로그인</button>
        </nav>
      </header>

      <section className="hero-section" id="top">
        <div className="hero-copy">
          <p className="eyebrow"><span /> 미확인 변신 사건 전담 기록실</p>
          <h1>사라진 기억의<br /><em>흔적을 추적하라</em></h1>
          <p className="hero-description">
            현장에 남은 단서와 용의자 파일을 대조해 거짓 가능성을 하나씩
            지워 나가세요. 마지막에 남은 단 하나의 모습이 그날 밤의 진실입니다.
          </p>
          <div className="hero-actions">
            <a className="primary-link" href="#cases">사건 선택하기</a>
            <span>3개의 사건 · 12명의 용의자</span>
          </div>
        </div>

        <div className="hero-visual" aria-label="창고 사건 현장 미리보기">
          <img src="/scenes/case03_scene.webp" alt="상자와 발자국이 남겨진 어두운 창고 현장" />
          <div className="scan-line" aria-hidden="true" />
          <div className="scene-label"><span>ACTIVE SCENE</span><strong>STORAGE / 03</strong></div>
          <div className="evidence-marker marker-one" aria-hidden="true"><span>01</span></div>
          <div className="evidence-marker marker-two" aria-hidden="true"><span>05</span></div>
        </div>
      </section>

      <section className="case-section" id="cases">
        <div className="section-heading">
          <div>
            <p className="eyebrow"><span /> INCIDENT ARCHIVE</p>
            <h2>수사할 사건을 선택하세요</h2>
          </div>
          <p>단서를 수집하고 배제 근거를 제출하면<br />서버가 추리의 타당성을 판정합니다.</p>
        </div>

        <div className="case-layout">
          <div className="case-list" role="list" aria-label="사건 목록">
            {cases.map((caseFile) => {
              const isSelected = selectedCase.code === caseFile.code
              return (
                <button
                  type="button"
                  role="listitem"
                  className={`case-card ${isSelected ? 'is-selected' : ''}`}
                  key={caseFile.code}
                  onClick={() => setSelectedCase(caseFile)}
                  aria-pressed={isSelected}
                >
                  <span className="case-code">{caseFile.code}</span>
                  <span className="case-title">{caseFile.title}</span>
                  <span className="case-tagline">{caseFile.tagline}</span>
                  <span className={`case-status ${caseFile.status}`}><i aria-hidden="true" /> {caseFile.meta}</span>
                </button>
              )
            })}
          </div>

          <article className="selected-file">
            <div className="file-tabs" aria-hidden="true"><span>INCIDENT</span><span>EVIDENCE</span><span>SUSPECTS</span></div>
            <div className="file-content">
              <div className="file-number">{selectedCase.code.replace('CASE ', '')}</div>
              <p className="file-kicker">선택된 사건</p>
              <h3>{selectedCase.title}</h3>
              <p>{selectedCase.tagline}</p>
              <dl>
                <div><dt>초기 점수</dt><dd>100</dd></div>
                <div><dt>용의자</dt><dd>4</dd></div>
                <div><dt>지목 기회</dt><dd>2</dd></div>
              </dl>
              <button
                type="button"
                className="open-file-button"
                onClick={() => setIsBriefingOpen(true)}
                disabled={selectedCase.status !== 'playable'}
              >
                {selectedCase.status === 'playable' ? '브리핑 열기' : '현재 준비 중'}
              </button>
            </div>
          </article>
        </div>
      </section>

      <footer><span>MEMORY TRACE · WEB MYSTERY GAME</span><span>React + Spring Boot</span></footer>

      {isBriefingOpen && (
        <div className="modal-backdrop" role="presentation" onMouseDown={() => setIsBriefingOpen(false)}>
          <section className="briefing-modal" role="dialog" aria-modal="true" aria-labelledby="briefing-title" onMouseDown={(event) => event.stopPropagation()}>
            <button type="button" className="close-button" onClick={() => setIsBriefingOpen(false)} aria-label="브리핑 닫기">×</button>
            <p className="eyebrow"><span /> CASE BRIEFING 03</p>
            <h2 id="briefing-title">들어간 발자국만 있는 창고</h2>
            <p>
              진화의 돌 보관 창고에서 침입 흔적이 발견됐습니다. 출구의 흔적은 없고,
              바닥에는 들어간 방향의 발자국만 남았습니다. 현장 단서를 조사해 네 명의
              용의자 중 불가능한 모습을 배제하세요.
            </p>
            <div className="briefing-note"><strong>수사 원칙</strong><span>추측이 아닌, 수집한 증거로만 용의자를 배제할 수 있습니다.</span></div>
            <button type="button" className="primary-link modal-action" onClick={() => setIsBriefingOpen(false)}>확인했습니다</button>
          </section>
        </div>
      )}
    </main>
  )
}

export default App

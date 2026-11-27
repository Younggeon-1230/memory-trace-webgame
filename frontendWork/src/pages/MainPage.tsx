import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const previews = [
  { code: 'CASE 01', title: '자정의 연구소', tagline: '아무도 없는 방에서 물건이 움직였다' },
  { code: 'CASE 02', title: '불타버린 실험실', tagline: '불이 먼저였나, 정전이 먼저였나' },
  { code: 'CASE 03', title: '들어간 발자국만 있는 창고', tagline: '나온 발자국은 어디로 갔을까' },
]

const steps = [
  { no: '01', title: '현장 조사', text: '현장 이미지를 클릭해 단서를 모읍니다. 단서가 모이면 새로운 추론이 떠오릅니다.' },
  { no: '02', title: '증거 배제', text: '증거 하나와 용의자 하나를 골라 “이 증거로는 불가능하다”를 제출합니다. 틀리면 -10점.' },
  { no: '03', title: '최종 지목', text: '남은 용의자 중 메타몽이 변신했던 모습을 지목합니다. 기회는 두 번, 틀리면 -30점.' },
]

export default function MainPage() {
  const { user } = useAuth()
  const start = user ? '/cases' : '/login'
  return (
    <main>
      <section className="hero-section" id="top">
        <div className="hero-copy">
          <p className="eyebrow"><span /> 오박사와 메타몽의 사라진 기억</p>
          <h1>사라진 기억의<br /><em>흔적을 추적하라</em></h1>
          <p className="hero-description">
            오박사의 연구소에서 사건이 벌어졌고, 그날 밤 현장에 있던 포켓몬은 변신한 메타몽뿐입니다.
            현장 단서와 용의자 파일을 대조해 불가능한 모습을 하나씩 지워 나가세요.
          </p>
          <div className="hero-actions">
            <Link className="primary-link" to={start}>{user ? '사건 목록으로' : '로그인하고 수사 시작'}</Link>
            {!user && <Link className="text-link" to="/signup">처음이라면 회원가입 →</Link>}
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

      <section className="case-section">
        <div className="section-heading">
          <div>
            <p className="eyebrow"><span /> HOW TO PLAY</p>
            <h2>추측이 아닌, 증거로 지운다</h2>
          </div>
          <p>정답과 판정 규칙은 서버만 알고 있습니다.<br />모든 추리는 서버가 포켓몬 데이터로 검증합니다.</p>
        </div>
        <ol className="step-list">
          {steps.map((s) => (
            <li key={s.no}><span className="case-code">{s.no}</span><strong>{s.title}</strong><p>{s.text}</p></li>
          ))}
        </ol>

        <div className="section-heading" style={{ marginTop: 72 }}>
          <div>
            <p className="eyebrow"><span /> INCIDENT ARCHIVE</p>
            <h2>세 개의 사건 기록</h2>
          </div>
          <p>3개의 사건 · 사건마다 용의자 4명<br />시작 점수 100점, 지목 기회 2번</p>
        </div>
        <div className="case-list">
          {previews.map((c) => (
            <Link key={c.code} className="case-card" to={start}>
              <span className="case-code">{c.code}</span>
              <span className="case-title">{c.title}</span>
              <span className="case-tagline">{c.tagline}</span>
              <span className="case-status playable"><i aria-hidden="true" /> 수사 가능</span>
            </Link>
          ))}
        </div>
      </section>
    </main>
  )
}
